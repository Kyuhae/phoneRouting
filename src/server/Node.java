package server;

import com.rabbitmq.client.*;
import org.apache.commons.lang3.SerializationUtils;
import server.util.Pair;
import static shared.CommunicationConstants.*;
import shared.Message;
import shared.MessageType;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeoutException;


public class Node implements  Runnable {

    private final int id;
    private final int numOfNodes;
    private Channel myChannel;
    private Consumer consumer;
    private final String inQueue;
    private List<NeighbourInfo_itf> neighbours;

    private ConcurrentMap<String, ClientInfo_itf> clients;
    private ConcurrentMap<String, Pair<ClientInfo_itf, Integer>> desiredNames;
    private Set<String> reservedNames;
    // Map of ID - (nextStep, dist) for sending and stuff
    private ConcurrentMap<Integer, Pair<Integer, Integer>> nodeRouting;

    public Node(int id, int numOfNodes, List<NeighbourInfo_itf> neighbours) {
        this.id = id;
        this.numOfNodes = numOfNodes;
        this.neighbours = neighbours;
        nodeRouting = new ConcurrentHashMap<>();

        //Initialize list of clients
        clients = new ConcurrentHashMap<>();
        desiredNames = new ConcurrentHashMap<>();
        reservedNames = ConcurrentHashMap.newKeySet();

        //setup incoming channel to this node
        inQueue = String.valueOf(id) + "_queue";
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");

        Connection connection;
        try {
            connection = factory.newConnection();
            myChannel = connection.createChannel();
            myChannel.queueDeclare(inQueue, false, false, false, null);
        } catch (IOException | TimeoutException e) {
            System.out.println("Node setup failed due to network reasons. We are not prepared for this.");
            e.printStackTrace();
            System.exit(1);
        }

        // Create outgoing channels to neighbour nodes
        for (NeighbourInfo_itf n : neighbours) {
            factory = new ConnectionFactory();
            factory.setHost(n.getHostName());

            try {
                connection = factory.newConnection();
                Channel channel = connection.createChannel();
                n.setChannel(channel);
                channel.queueDeclare(n.getQueueName(), false, false, false, null);
            } catch (IOException | TimeoutException e) {
                e.printStackTrace();
            }
        }

        //add ourself to list of neighbours
        NeighbourInfo_itf myInfo = new NeighbourInfo(id, inQueue, "localhost");
        myInfo.setChannel(myChannel);
        neighbours.add(myInfo);
        nodeRouting.put(id, new Pair<>(id, 0));

        //Node only knows its local neighbours now.
        //advertise routing info
        // Send <origin, nextHop, dist to this id from sender> to all neighbours
        neighbourBCast(MessageType.N_RIP, id + " " + 0);

        // Build consumer to handle incoming messages
        consumer = new DefaultConsumer(myChannel) {
            @Override
            public void handleDelivery (String consumerTag, Envelope envelope,
                                        AMQP.BasicProperties props, byte[] body)
                    throws IOException {
                String replyQueueName = props.getReplyTo();

                Message msg = SerializationUtils.deserialize(body);
                String msgBody = msg.getBody();
                String[] parts = msgBody.split(" ");

                // Pass through. Nothing to see here for this guy.
                if (msg.getDest() != id) {
                    sendMsgToNode(msg);
                    return;
                }

                switch (msg.getType()) {
                    case N_RIP:
                        if (parts.length != 2) {
                            System.out.println("Invalid network broadcast received. Wrong number of parameters.");
                            return;
                        }
                        try {
                            int originId = Integer.parseInt(parts[0]);
                            int dist = Integer.parseInt(parts[1]);
                            handleN_Rip(originId, msg.getSrc(), dist);
                        } catch (NumberFormatException nfe) {
                            System.out.println("Invalid network broadcast received. Id or dist not an Integer.");
                            nfe.printStackTrace();
                        }
                        break;

                    case LOGIN:
                        if (parts.length != 1) {
                            System.out.println("Invalid login message received. Wrong number of parameters.");
                            return;
                        }
                        handleLogin(parts[0], replyQueueName);
                        break;

                    case DISCONNECT:
                        if (parts.length != 1) {
                            System.out.println("Invalid disconnect message received. Wrong number of parameters.");
                            return;
                        }
                        System.out.println("Node" + id + " got disconnect from client " + parts[0]);
                        handleDisconnect(parts[0]);
                        break;

                    case NAME_LOCK_REQ:
                        handleNameLockReq(msg.getSrc(), msg.getBody());
                        break;

                    case NAME_LOCK_REPLY:
                        if (parts.length != 2) {
                            System.out.println("Invalid NAME_LOCK_REPLY message received. Wrong number of parameters.");
                            return;
                        }
                        handleNameLockReply(parts[0], parts[1]);
                        break;

                    case NAME_LOCK_ANNOUNCE:
                        //someone is confirming they got a name
                        //add it to clients list, remove it from reserved
                        ClientInfo_itf newClient = new ClientInfo(msg.getSrc());
                        clients.put(msg.getBody(), newClient);
                        reservedNames.remove(msg.getBody());
                        break;

                    case CLIENT_CALL:
                        // Assume: senderName recvName msg
                        parts = msgBody.split(" ", 3);
                        if (parts.length != 3) {
                            System.out.println("Invalid CLIENT_CALL message received. Wrong number of parameters.");
                            return;
                        }

                        handleClientCall(parts[0], parts[1], parts[2]);
                        break;

                    case NODE_CALL:
                        parts = msgBody.split(" ", 3);
                        if (parts.length != 3) {
                            System.out.println("Invalid NODE_CALL message received. Wrong number of parameters.");
                            return;
                        }

                        handleNodeCall(parts[0], parts[1], parts[2]);
                        break;

                    case NAME_LOCK_RELEASE:
                        //someone doesn't need a name anymore, I should update my client info
                        clients.remove(msg.getBody());
                        break;

                    case CLIENT_TRANSFER_REQ:
                        //a client of mine needs to be transfered to the node he specifies
                        if (parts.length != 2) {
                            System.out.println("Invalid CLIENT_TRANSFER_REQ message received. Wrong number of parameters.");
                            return;
                        }
                        int newNodeId = Integer.parseInt(parts[1]);
                        handleClientTransferReq(parts[0], newNodeId);
                        break;

                    case TRANSFER:
                        //a client is being transferred
                        if (parts.length != 2) {
                            System.out.println("Invalid TRANSFER message received. Wrong number of parameters.");
                            return;
                        }
                        int transferNodeId = Integer.parseInt(parts[1]);
                        handleTransfer(parts[0], transferNodeId);

                    case CLIENT_ARRIVAL:
                        //a client has been transfered to me, and sent me a message. I can deduce its replyqueue
                        ClientInfo_itf tempC = clients.get(parts[0]);
                        tempC.setQueueName(replyQueueName);
                        break;

                    default:
                        System.out.println("Unrecognized message type");
                }
            }
        };
    }

    @Override
    public void run() {
        try {
            myChannel.basicConsume(inQueue, true, consumer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void neighbourBCast(MessageType type, String msg) {
        for (NeighbourInfo_itf n : this.neighbours) {
            Message bc = new Message(id, n.getNodeId(), type, msg);
            try {
                n.getChannel().basicPublish("", n.getQueueName(), null, SerializationUtils.serialize(bc));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void sendMsgToNode(int src, int dest, MessageType type, String msgBody) {
        Pair<Integer,Integer> p = nodeRouting.get(dest);
        NeighbourInfo_itf nextHop = null;
        for (NeighbourInfo_itf n : neighbours) {
            if (n.getNodeId() == p.getFirst()) {
                nextHop = n;
            }
        }
        // Someone doesn't exist who should exist
        if (nextHop == null) {
            System.out.println("No next hop was found for NodeId " + dest + ". Fix your setup function.");
            return;
        }

        try {
            Message msg = new Message(src, dest, type, msgBody);
            nextHop.getChannel().basicPublish("", nextHop.getQueueName(), null, SerializationUtils.serialize(msg));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendMsgToNode(int src, int dest, MessageType type, String ... parts) {
        StringJoiner sj = new StringJoiner(" ");
        for (String p : parts) {
            sj.add(p);
        }
        sendMsgToNode(src, dest, type, sj.toString());
    }

    private void sendMsgToNode(Message msg) {
        sendMsgToNode(msg.getSrc(), msg.getDest(), msg.getType(), msg.getBody());
    }

    private void bCast(MessageType type, String msgBody) {
        for (int destId = 0; destId < nodeRouting.size(); destId++) {
            // Avoid trouble with elections
            if (destId != id) {
                sendMsgToNode(id, destId, type, msgBody);
            }
        }
    }

    private void handleLogin(String clientName, String replyQueueName) {
        System.out.println("Login for client " + clientName + " requested.");

        // block logins until nodeRouting.size() == numOfNodes
        if (nodeRouting.size() != numOfNodes) {
            return;
        }
        //is the name he wants available on my node?
        boolean available = true;
        //check if I already know of an established client with this name
        if (clients.containsKey(clientName))
            System.out.println("A client already has name " + clientName);

        //check if there is an ongoing election process for this name
        available = (available && !reservedNames.contains(clientName));

        if (!available) {
            //already know it's pointless, ask for another name
            try {
                Message msg = new Message(id, -1, MessageType.LOGIN, CLIENT_LOGIN_NEG);
                myChannel.basicPublish("", replyQueueName, null, SerializationUtils.serialize(msg));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else {
            //add name to desired list, and initialize nbResponse counter
            ClientInfo_itf clientInfo = new ClientInfo(id);
            clientInfo.setQueueName(replyQueueName);
            Pair<ClientInfo_itf, Integer> newClient = new Pair<>(clientInfo, 0);

            desiredNames.put(clientName, newClient);
            //try to acquire lock on that name
            bCast(MessageType.NAME_LOCK_REQ, clientName);
        }
    }

    private void handleDisconnect(String clientName) {
        if (!clients.containsKey(clientName)) {
            System.out.println("Got disconnect message from unknown client " + clientName);
            return;
        }

        //remove it from my own map of clients
        clients.remove(clientName);

        //tell other nodes this name is free
        bCast(MessageType.NAME_LOCK_RELEASE, clientName);
    }

    private void handleNameLockReq(int requester, String clientName) {
        String response = "winner";
        // check if we know a client with this name
        if (clients.containsKey(clientName)) {
            response = "looser";
        } else if (desiredNames.containsKey(clientName)) {
            //if no clients have this name yet, and we also want this name
            //determine which of us is higher priority
            if (requester < id) {
                response = "winner";
            } else {
                response = "looser";
            }
        }
        sendMsgToNode(id, requester, MessageType.NAME_LOCK_REPLY, clientName, response);
    }

    private void handleNameLockReply(String clientName, String vote) {
        String response;
        if (!desiredNames.containsKey(clientName)) {
            return;
        }

        Pair<ClientInfo_itf, Integer> infoPair = desiredNames.get(clientName);
        ClientInfo_itf client = infoPair.getFirst();

        if (vote.equals("looser")) {
            //one negative response! -> We lost!
            response = CLIENT_LOGIN_NEG;
            desiredNames.remove(clientName);
        } else {
            infoPair.setSecond(infoPair.getSecond() + 1);
            if (infoPair.getSecond() != numOfNodes-1) {
                //we haven't received enough answers yet.
                return;
            } else {
                // all positive responses! -> We won!
                clients.put(clientName, client);
                desiredNames.remove(clientName);
                // announce this name as ours
                bCast(MessageType.NAME_LOCK_ANNOUNCE, clientName);
                // confirm login to client
                response = CLIENT_LOGIN_POS;
            }
        }
        try {
            Message responseMsg = new Message(id, -1, MessageType.LOGIN, response);
            myChannel.basicPublish("", client.getQueueName(), null,
                    SerializationUtils.serialize(responseMsg));
        } catch (IOException e) {
            // TODO: tell others that client did not login after all? (robustness improvement)
            System.out.println("Unable to confirm name winning to client");
            e.printStackTrace();
        }
    }

    private void handleClientCall(String sender, String recv, String msg) {
        ClientInfo_itf recvClient = clients.get(recv);
        if (recvClient == null) {
            // Send Message to our client that his buddy does not exist
            ClientInfo_itf senderClient = clients.get(sender);
            if (senderClient != null) {
                String response = "The client " + recv + " does not exist.";
                Message responseMsg = new Message(id, -1, MessageType.CLIENT_CALL, response);
                try {
                    myChannel.basicPublish("", senderClient.getQueueName(), null,
                            SerializationUtils.serialize(responseMsg));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return;
        }

        // I don't know why you're doing this but it's not my place to judge you.
        if (sender.equals(recv)) {
            Message responseMsg = new Message(id, -1, MessageType.CLIENT_CALL, sender + ": " + msg);
            try {
                myChannel.basicPublish("", recvClient.getQueueName(), null,
                        SerializationUtils.serialize(responseMsg));
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            sendMsgToNode(id, recvClient.getNodeId(), MessageType.NODE_CALL, sender, recv, msg);
        }
    }

    private void handleNodeCall(String sender, String recv, String msg) {
        ClientInfo_itf recvClient = clients.get(recv);
        if (recvClient == null) {
            return;
        }

        if (recvClient.getNodeId() == id) {
            Message responseMsg = new Message(id, -1, MessageType.CLIENT_CALL, sender + ": " + msg);
            try {
                myChannel.basicPublish("", recvClient.getQueueName(), null,
                        SerializationUtils.serialize(responseMsg));
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else { // The client already moved on
            sendMsgToNode(id, recvClient.getNodeId(), MessageType.NODE_CALL, sender, recv, msg);
        }
    }

    private void handleN_Rip(int originID, int nextHop, int dist) {
        int newDist = dist + 1;
        Pair<Integer, Integer> prevEntry;
        prevEntry = nodeRouting.putIfAbsent(originID, new Pair<>(nextHop, newDist));
        if (prevEntry == null) {
            neighbourBCast(MessageType.N_RIP,originID + " " + newDist);
        } else {
            if (prevEntry.getSecond() > newDist) {
                // update entry
                synchronized (prevEntry) {
                    if (prevEntry.getSecond() > newDist) {
                        prevEntry.setSecond(newDist);
                        prevEntry.setFirst(nextHop);
                    }
                }

                // tell all neighbours about this great new routing information!
                // Send <sender, id this is about, dist to this id from sender> to all neighbours
                neighbourBCast(MessageType.N_RIP,originID + " " + newDist);
            }
        }
    }

    private void handleClientTransferReq(String clientName, int newNodeId) {
        //check we have this client
        if (!clients.containsKey(clientName)) {
            System.out.println("Got ClientTransfer request from unknown client " + clientName);
            return;
        }
        if (newNodeId < 0 || newNodeId > numOfNodes-1) {
            // This shouldn't happen anyway, given our Client checks this when converting from world coords to nodeId
            System.out.println("Got clientTransfer request to out-of-range node: " + newNodeId);
            return;
        }

        //update our local information about which node the client is on
        ClientInfo_itf tempC = clients.get(clientName);
        tempC.setNodeId(newNodeId);
        tempC.setQueueName(null);

        //tell everyone about the transfer
        bCast(MessageType.TRANSFER, clientName + " " + newNodeId);
    }

    private void handleTransfer(String clientName, int newNodeId) {
        //check we have this client
        if (!clients.containsKey(clientName)) {
            System.out.println("Got transfer message for unknown client " + clientName);
            return;
        }
        //set nodeId of that client to reflect the transfer
        ClientInfo_itf tempC = clients.get(clientName);
        tempC.setNodeId(newNodeId);

    }
}