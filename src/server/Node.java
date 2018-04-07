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
    private List<ClientInfo_itf> clients;
    private Map<String, Pair<ClientInfo_itf, Integer>> desiredNames;
    private Set<String> reservedNames;

    // Map of ID - (nextStep, dist) for sending and stuff
    private ConcurrentMap<Integer, Pair<Integer, Integer>> nodeRouting;

    public Node(int id, int numOfNodes, List<NeighbourInfo_itf> neighbours) {
        this.id = id;
        this.numOfNodes = numOfNodes;
        this.neighbours = neighbours;
        nodeRouting = new ConcurrentHashMap<>();

        //setup incoming channel to this node
        inQueue = String.valueOf(id) + "_queue";
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");

        Connection connection = null;

        try {
            connection = factory.newConnection();
            myChannel = connection.createChannel();
            myChannel.queueDeclare(inQueue, false, false, false, null);
        } catch (IOException | TimeoutException e) {
            System.out.println("Node setup failed due to network reasons. We are not prepared for this. Burn.");
            e.printStackTrace();
            // Isn't it convenient that everything is running in the same thread? Yep. It is. :D
            System.exit(1);
        }

        //System.out.println("I'm node " + id);
        //System.out.println("My neighbours are:");

        // Create outgoing channels to neighbour nodes
        for (NeighbourInfo_itf n : neighbours) {
            factory = new ConnectionFactory();
            factory.setHost(n.getHostName());

            //System.out.println("\t" + n.getNodeId());

            try {
                connection = factory.newConnection();
                Channel channel = connection.createChannel();
                n.setChannel(channel);
                channel.queueDeclare(n.getQueueName(), false, false, false, null);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (TimeoutException e) {
                e.printStackTrace();
            }
        }
        //System.out.println();


        nodeRouting.put(id, new Pair<>(id, 0));
        //server.Node only knows its local neighbours now.
        //advertise routing info
        // Send <origin, nextHop, dist to this id from sender> to all neighbours
        neighbourBCast(MessageType.N_RIP, id + " " + 0);


        //Initialize list of clients
        clients = new ArrayList<>();
        desiredNames = new HashMap<>();
        ConcurrentMap<String, String> m = new ConcurrentHashMap<>();
        reservedNames = m.keySet();

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

                // Pass through. Nothing to see her for this guy.
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
                        // block logins until nodeRouting.size() == numOfNodes
                        if (nodeRouting.size() != numOfNodes) {
                            return;
                        }
                        if (parts.length != 1) {
                            System.out.println("Invalid login message received. Wrong number of parameters.");
                            return;
                        }

                        handleLogin(parts[0], replyQueueName);
                        break;

                    case NAME_LOCK_REQ:
                        handleNameLockReq(msg.getSrc(), msg.getBody());
                        break;

                    case NAME_LOCK_REPLY:
                        //parse message
                        if (parts.length != 2) {
                            System.out.println("Invalid NAME_LOCK_REPLY message received. Wrong number of parameters.");
                            return;
                        }
                        handleNameLockReply(parts[0], parts[1]);
                        break;

                    case NAME_LOCK_ANNOUNCE:
                        //someone is confirming they got a name (good for them)
                        //add it to clients list, remove it from reserved
                        System.out.println("node " + id + "got NAME_LOCK_ANNOUNCE for " + msg.getBody() + " from " + msg.getSrc());
                        ClientInfo_itf newClient = new ClientInfo(msg.getSrc(), msg.getBody());
                        clients.add(newClient);
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

                    default:
                        System.out.println("Who is sending useless messages here?");
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
        //is the name he wants available on my node?
        boolean available = true;
        //check if I already know of an established client with this name
        for (ClientInfo_itf c : this.clients) {
            if (c.getName().equals(clientName)) {
                available = false;
            }
        }
        if (!available)
            System.out.println("A client already has name" + clientName);

        //check if there is an ongoing election process for this name
        available = (available && !reservedNames.contains(clientName));

        if (!available) {
            //already know it's pointless, ask for another name
            try {
                Message msg = new Message(id, -1, MessageType.LOGIN, "nameInUse");
                myChannel.basicPublish("", replyQueueName, null, SerializationUtils.serialize(msg));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else {
            //add name to desired list, and initialize nbResponse counter
            ClientInfo_itf clientInfo = new ClientInfo(id, clientName);
            clientInfo.setQueueName(replyQueueName);
            Pair<ClientInfo_itf, Integer> newClient = new Pair<>(clientInfo, 0);

            desiredNames.put(clientName, newClient);
            //try to acquire lock on that name
            bCast(MessageType.NAME_LOCK_REQ, clientName);
        }
    }

    private void handleNameLockReq(int requester, String clientName) {
        boolean available = true;
        String response = "winner";
        // check if we know a client with this name already exists
        for (ClientInfo_itf c : clients) {
            if (c.getName().equals(clientName)) {
                System.out.println("Billy is already claimed by an existing client on node " + c.getNodeId());
                response = "looser";
                available = false;
            }
        }

        //if no clients have this name yet, and we also want this name
        if (available && desiredNames.containsKey(clientName)) {
            System.out.println("Hey! I (node " + id +") want that name too!");
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
        String response = CLIENT_LOGIN_NEG;
        System.out.println("Node " + id + "got NAME_LOCK_REPLY for " + clientName);
        if (!desiredNames.containsKey(clientName)) {
            System.out.println("I have no memory of requesting this name... " + clientName);
            return;
        }

        Pair<ClientInfo_itf, Integer> infoPair = desiredNames.get(clientName);
        ClientInfo_itf client = infoPair.getFirst();

        if (vote.equals("looser")) {
            //one negative response! -> We lost!
            System.out.println("Negative response for name " + clientName + " received");
            response = CLIENT_LOGIN_NEG;
            desiredNames.remove(clientName);
        } else {
            System.out.println("positive response for name " + clientName + " received. num of nodes " + numOfNodes);
            infoPair.setSecond(infoPair.getSecond() + 1);
            if (infoPair.getSecond() != numOfNodes-1) {
                //we haven't received enough answers yet.
                return;
            } else {
                // all positive responses! -> We won!
                clients.add(client);
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
            // TODO: tell others that client did not login after all? (relatively unimportant robustness-thingy)
            System.out.println("Unable to confirm name winning to client");
            e.printStackTrace();
        }
    }

    private void handleClientCall(String sender, String recv, String msg) {
        ClientInfo_itf recvClient = null;
        for (ClientInfo_itf c : clients) {
            if (c.getName().equals(recv)) {
                recvClient = c;
                break;
            }
        }
        if (recvClient == null) {
            // send message to our client that the guy he wants to contact kind of doesn't exist
            for (ClientInfo_itf c : clients) {
                if (c.getName().equals(sender)) {
                    String response = "The client " + recv + " does not exist.";
                    Message responseMsg = new Message(id, -1, MessageType.CLIENT_CALL, response);
                    try {
                        myChannel.basicPublish("", c.getQueueName(), null,
                                SerializationUtils.serialize(responseMsg));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
            return;
        }

        // I don't know why you're doing this but it's not my place to judge.
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
        ClientInfo_itf recvClient = null;
        for (ClientInfo_itf c : clients) {
            if (c.getName().equals(recv)) {
                recvClient = c;
                break;
            }
        }
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

            if (id == 1)
                System.out.println("Update1! server.Node: " + originID + ", nextHop: " + nextHop + ", dist: " + newDist);

            return;
        } else {
            if (prevEntry.getSecond() > newDist) {
                // update entry
                synchronized (prevEntry) {
                    if (prevEntry.getSecond() > newDist) {
                        prevEntry.setSecond(newDist);
                        prevEntry.setFirst(nextHop);
                        if (id == 1)
                            System.out.println("Update2! server.Node: " + originID + ", nextHop: " + nextHop + ", dist: " + newDist);
                    }
                }

                // tell all neighbours about this great new thing!
                // Send <sender, id this is about, dist to this id from sender> to all neighbours
                neighbourBCast(MessageType.N_RIP,originID + " " + newDist);
            }
        }
    }
}