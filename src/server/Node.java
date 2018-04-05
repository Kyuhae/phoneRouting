package server;

import com.rabbitmq.client.*;
import org.apache.commons.lang3.SerializationUtils;
import server.util.Pair;
import shared.Message;
import shared.MessageType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeoutException;


public class Node implements  Runnable {

    private int id;
    private Channel myChannel;
    private Consumer consumer;
    private String inQueue;

    private List<NeighbourInfo_itf> neighbours;
    private List<ClientInfo_itf> clients;
    private List<Pair<String, List<Integer>>> desiredNames;
    private Set<String> reservedNames;

    // Map of ID - (nextStep, dist) for sending and stuff
    private ConcurrentMap<Integer, Pair<Integer, Integer>> nodeRouting;

    public Node(int id, List<NeighbourInfo_itf> neighbours) {
        this.id = id;
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
        } catch (IOException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
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
        neighbourBCast(MessageType.N_RIP, id + " " + id + " " + 0);


        //Initialize list of clients
        clients = new ArrayList<>();
        desiredNames = new ArrayList<>();
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

                //use info from msg to do some logic and/or reply as needed
                switch (msg.getType()) {
                    case N_RIP:
                        //parse message
                        if (parts.length != 2) {
                            System.out.println("Invalid network broadcast received. Wrong number of parameters.");
                            return;
                        }
                        int nextHop = 0;
                        int dist = 0;
                        try {
                            nextHop = Integer.parseInt(parts[1]);
                            dist = Integer.parseInt(parts[2]);
                        } catch (NumberFormatException nfe) {
                            System.out.println("Invalid network broadcast received. Id or dist not an Integer.");
                            nfe.printStackTrace();
                            return;
                        }

                        //handle message
                        handleN_Rip(msg.getSrc(), nextHop, dist);
                        break;

                    case LOGIN:
                        //parse message
                        if (parts.length != 1) {
                            System.out.println("Invalid login message received. Wrong number of parameters.");
                            return;
                        }
                        String clientName = parts[0];
                        //handle Login
                        handleLogin(clientName, replyQueueName);
                        break;

                    case NAME_LOCK_REQ:
                        handleNameLockReq(msg.getSrc(), msg.getBody());
                        break;

                    case NAME_LOCK_REPLY:
                        //parse message
                        if (parts.length != 1) {
                            System.out.println("Invalid NAME_LOCK_REPLY message received. Wrong number of parameters.");
                            return;
                        }
                        int response = Integer.parseInt(parts[0]); // 0 or 1

                        //handleNameLockReply(origiId, dest, response);

                        //if got all replys: check if i won
                        ///if i won
                        //clients.add(new server.ClientInfo(clientName, replyQueueName));
                        // + send NAME_LOCK_CONFIRM
                        break;

                    case NAME_LOCK_CONFIRM:
                        //someone is confirming they got a name (good for them)
                        //add it to clients list, remove it from reserved

                        break;


                    case CALL:
                        break;
                    case RIP:
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
        //is the name he wants available on my node?
        boolean available = true;
        //check if I already know of an established client with this name
        for (ClientInfo_itf c : this.clients) {
            if (c.getName().equals(clientName)) {
                available = false;
            }
        }
        //check if there is an ongoing election process for this name
        available = reservedNames.contains(clientName);

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
            desiredNames.add(new Pair<>(clientName, new ArrayList<>()));
            //try to acquire lock on that name
            bCast(MessageType.NAME_LOCK_REQ, clientName);
        }
        //is it available globally?
        //  bcast can i haz this name ploxx?
        // for all nodes : wait for positive anwser
        //bcast
    }

    private void handleNameLockReq(int requester, String name) {
        //private List<ClientInfo_itf> clients;
        //private List<Pair<String, List<Integer>>> desiredNames;
        String response = "winner";
        for (ClientInfo_itf c : clients) {
            if (c.getName().equals(name)) {
                response = "looser";
                return;
            }
        }

        for (Pair<String, List<Integer>> p : desiredNames) {
            if (p.getFirst().equals(name)) {
                if (requester < id) {
                    response = "winner";
                } else {
                    response = "looser";
                }
                return;
            }
        }
        sendMsgToNode(id, requester, MessageType.NAME_LOCK_REPLY, response);
    }

    private void handleCall() {

    }

    // TODO: Implement
    private void handleRip() {

    }

    private void handleN_Rip(int originID, int nextHop, int dist) {
        int newDist = dist + 1;
        Pair<Integer, Integer> prevEntry;
        prevEntry = nodeRouting.putIfAbsent(originID, new Pair<>(nextHop, newDist));
        if (prevEntry == null) {
            neighbourBCast(MessageType.N_RIP,originID + " " + id + " " + newDist);
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
                        System.out.println("Update2! server.Node: " + originID + ", nextHop: " + nextHop + ", dist: " + newDist);
                    }
                }

                // tell all neighbours about this great new thing!
                // Send <sender, id this is about, dist to this id from sender> to all neighbours
                neighbourBCast(MessageType.N_RIP,originID + " " + id + " " + newDist);
            }
        }
    }
}