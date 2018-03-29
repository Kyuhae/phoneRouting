import com.rabbitmq.client.*;
import org.apache.commons.lang3.SerializationUtils;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeoutException;


public class Node implements  Runnable {

    private int id;
    private Channel myChannel;
    private List<NeighbourInfo> neighbours;
    private Consumer consumer;
    private List<ClientInfo> managedClients;
    private String inQueue;

    // Map of ID - (nextStep, dist) for sending and stuff
    private ConcurrentMap<Integer, Pair<Integer, Integer>> nodeRouting;

    public Node(int id, List<NeighbourInfo> neighbours) {
        this.id = id;
        this.neighbours = neighbours;
        nodeRouting = new ConcurrentHashMap<>();

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

        // Create neighbor communication channels
        for (NeighbourInfo n : neighbours) {
            factory = new ConnectionFactory();
            factory.setHost(n.getHostName());

            //System.out.println("\t" + n.getNodeId());

            try {
                connection = factory.newConnection();
                n.setChannel(connection.createChannel());
                n.getChannel().queueDeclare(n.getQueueName(),
                        false, false, false, null);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (TimeoutException e) {
                e.printStackTrace();
            }
        }
        //System.out.println();


        nodeRouting.put(id, new Pair<>(id, 0));
        //Node only knows its local neighbours now.
        //advertise routing info
        // Send <origin, nextHop, dist to this id from sender> to all neighbours
        neighbourBCast(MessageType.N_RIP, id + " " + id + " " + 0);

        // TODO: make sure we have info for all neighbours

        // Build consumer
        consumer = new DefaultConsumer(myChannel) {
            @Override
            public void handleDelivery (String consumerTag, Envelope envelope,
                                        AMQP.BasicProperties props, byte[] body)
                    throws IOException {
                String replyQueueName = props.getReplyTo();

                Message msg = SerializationUtils.deserialize(body);
                String msgBody = msg.getBody();

                //use info from msg to do some logic and/or reply as needed
                switch (msg.getType()) {
                    case LOGIN:
                        System.out.println(msg.getBody());
                        break;
                    case CALL:
                        break;
                    case RIP:
                        break;
                    case N_RIP:
                        String[] parts = msgBody.split(" ");
                        if (parts.length != 3) {
                            System.out.println("Invalid network broadcast received. Wrong number of parameters.");
                            return;
                        }
                        int originID = 0;
                        int nextHop = 0;
                        int dist = 0;
                        try {
                            originID = Integer.parseInt(parts[0]);
                            nextHop = Integer.parseInt(parts[1]);
                            dist = Integer.parseInt(parts[2]);
                        } catch (NumberFormatException nfe) {
                            System.out.println("Invalid network broadcast received. Id or dist not an Integer.");
                            nfe.printStackTrace();
                            return;
                        }
                        handleN_Rip(originID, nextHop, dist);
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
        for (NeighbourInfo n : this.neighbours) {
            Message bc = new Message(type, msg);
            try {
                n.getChannel().basicPublish("", n.getQueueName(), null, SerializationUtils.serialize(bc));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleLogin() {
        //
        //is the name he wants available on my node?
        //is it available globally?
        //  bcast can i haz this name ploxx?
        // for all nodes : wait for positive anwser
        //bcast

    }
    private void handleCall() {

    }

    private void handleRip() {

    }

    // TODO why do the other nodes not seem to bcast?  find out next time :D
    private void handleN_Rip(int originID, int nextHop, int dist) {
        int newDist = dist + 1;
        Pair<Integer, Integer> prevEntry;
        prevEntry = nodeRouting.putIfAbsent(originID, new Pair<>(nextHop, newDist));
        if (prevEntry == null) {
            neighbourBCast(MessageType.N_RIP,originID + " " + id + " " + newDist);
            if (id == 1)
                System.out.println("Update1! Node: " + originID + ", nextHop: " + nextHop + ", dist: " + newDist);
            return;
        } else {
            if (prevEntry.getSecond() > newDist) {
                // update entry
                synchronized (prevEntry) {
                    if (prevEntry.getSecond() > newDist) {
                        prevEntry.setSecond(newDist);
                        prevEntry.setFirst(nextHop);
                        System.out.println("Update2! Node: " + originID + ", nextHop: " + nextHop + ", dist: " + newDist);
                    }
                }

                // tell all neighbours about this great new thing!
                // Send <sender, id this is about, dist to this id from sender> to all neighbours
                neighbourBCast(MessageType.N_RIP,originID + " " + id + " " + newDist);
            }
        }
    }
}

