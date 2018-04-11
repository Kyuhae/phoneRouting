package client;

import com.rabbitmq.client.*;
import org.apache.commons.lang3.SerializationUtils;
import static shared.CommunicationConstants.*;
import shared.Message;
import shared.MessageType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.TimeoutException;

public class Client {

    private static Consumer consumer;
    private static String name;
    private static Channel channel;
    private static String queueName;
    private static String replyQueueName;
    private static AMQP.BasicProperties props;
    private static int nodeNum;
    private static ConnectionFactory factory;
    private static String nodeHostName;
    private static volatile boolean loggedIn = false;
    private static  String consumerTag;
    private static  Connection connection;

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: <Name : String> <xPos : int> <yPos : int>");
            return;
        }

        //clientName, clientX, clientY
        name = args[0];
        int xPos = 0;
        int yPos = 0;
        try {
            xPos = Integer.parseInt(args[1]);
            yPos = Integer.parseInt(args[2]);
        } catch (NumberFormatException nfe) { // He who passes invalid parameters deserves the crash and burn
            System.out.println("The x and y position need to be given as integers. Call the program with <String> <Integer> <Integer>");
            nfe.printStackTrace();
            System.exit(1);
        }
        //setup queue from us to node
        try {
            nodeNum = posToNodeId(xPos, yPos);
            System.out.println("Zone: " + posToNodeId(xPos, yPos));
        } catch (InvalidCoordinatesException e) {
            System.out.println(e.getMessage());
            return;
        }

        queueName = nodeNum + "_queue";
        nodeHostName = "localhost";

        //get name and pos from cmdline
        //login to node given by pos :by sending msg on queue pos+"_queue"
        factory = new ConnectionFactory();
        factory.setHost(nodeHostName);

        try {
            connection = factory.newConnection();
            channel = connection.createChannel();

            //prepare the name for the queue from node to us
            replyQueueName = channel.queueDeclare().getQueue();
            //prepare props
            final String corrId = UUID.randomUUID().toString();
            props = new AMQP.BasicProperties
                    .Builder()
                    .correlationId(corrId)
                    .replyTo(replyQueueName)
                    .build();

            Message loginMsg = new Message(-1, nodeNum, MessageType.LOGIN, args[0]);
            channel.basicPublish("", queueName, props, SerializationUtils.serialize(loginMsg));

            consumer = new DefaultConsumer(channel) {
                @Override
                public void handleDelivery (String consumerTag, Envelope envelope,
                                            AMQP.BasicProperties props, byte[] body)
                        throws IOException {
                    Message msg = SerializationUtils.deserialize(body);
                    String msgBody = msg.getBody();

                    switch (msg.getType()) {
                        case LOGIN:
                            //parse message
                            String[] parts = msgBody.split(" ");
                            if (parts.length != 1) {
                                System.out.println("Invalid login message received. Wrong number of parameters.");
                                return;
                            }
                            String message = parts[0];
                            //handle Login
                            handleLogin(message);
                            break;

                        case CLIENT_CALL:
                            // Display message that we received
                            System.out.println(msg.getBody());
                            break;

                        default:
                            System.out.println("Unrecognized message type");
                    }
                }
            };

            // Run consumer to process arriving messages
            consumerTag = channel.basicConsume(replyQueueName, true, consumer);


            // User interaction: Console
            System.out.println("Use as\n" +
                    "\tquit\n" +
                    "\thelp\n" +
                    "\tcall <receiverName> <message>");

            boolean quit = false;
            while (!loggedIn) {/* La-Di-Da */}
            String input;
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            input = br.readLine();

            while(!quit) {
                String command = input.split(" ")[0];
                String[] parts;
                switch (command) {
                    case "call":
                        parts = input.split(" ", 3);
                        if (parts.length != 3) {
                            System.out.println("Invalid syntax. Type \"help\" for help");
                        } else {
                            // Send call message to our Node
                            Message msg = Message.createMsg(-1, nodeNum, MessageType.CLIENT_CALL, name, parts[1], parts[2]);
                            channel.basicPublish("", queueName, props, SerializationUtils.serialize(msg));
                        }
                        break;

                    case "changePos":
                        parts = input.split(" ");
                        if (parts.length != 3) {
                            System.out.println("Invalid syntax. Type \"help\" for help");
                        } else {
                            int newX = Integer.parseInt(parts[1]);
                            int newY = Integer.parseInt(parts[2]);
                            changePos(newX, newY);
                        }
                        break;

                    case "quit":
                        parts = input.split(" ");
                        if (parts.length != 1) {
                            System.out.println("Invalid syntax. Type \"help\" for help");
                        } else {
                            System.out.println("Disconnecting.");
                            disconnect();
                            quit = true;
                        }
                        break;

                    case "help": // You want help.
                    default: // You didn't ask for help, but trust me, you need it.
                        System.out.println("Use as\n" +
                                "\tquit\n" +
                                "\thelp\n" +
                                "\tcall <receiver> <message>\n" +
                                "\tchangePos <newX> <newY>\n");
                }
                if (!quit) {
                    input = br.readLine();
                }
            }
        } catch (IOException | TimeoutException e) {
            e.printStackTrace();
        }
    }

    private static void handleLogin(String message) {
        switch(message) {
            case CLIENT_LOGIN_POS:
                System.out.println("Successfully logged in as " + name + "!");
                loggedIn = true;
                break;

            case CLIENT_LOGIN_NEG:
                //get new name from user input
                System.out.println("Login failed: this name is already in use. Enter another name.");
                Scanner scan = new Scanner(System.in);
                String s = scan.next();
                while (s.equals(name)) {
                    System.out.println("No, I said ANOTHER name.");
                    s = scan.next();
                }
                name = s;
                System.out.println("Name entered, retrying login");
                try {
                    Message msg = new Message(-1, nodeNum, MessageType.LOGIN, name);
                    channel.basicPublish("", queueName, props, SerializationUtils.serialize(msg));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;

            default:
                System.out.println("unrecognized LOGIN message type");
        }
    }

    private static void disconnect() {
        Message msg = new Message(-1, nodeNum, MessageType.DISCONNECT, name);
        try {
            //tell our node we no longer need this name
            channel.basicPublish("", queueName, props, SerializationUtils.serialize(msg));
            //cancel consumer, and stop channel and connection
            channel.basicCancel(consumerTag);
            channel.close();
            connection.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
        }
    }

    private static int posToNodeId(int x, int y) throws InvalidCoordinatesException {
        //convert from "world coordinates" to the node which handles that zone
        if (x < 0 || x > WORLD_WIDTH - 1 || y < 0 || y > WORLD_HEIGTH - 1) {
            throw new InvalidCoordinatesException("Coordinates out of range. Valid ranges are: \n" +
                    "\tx: [0, " + (WORLD_WIDTH - 1) + "]\n" +
                    "\ty: [0, " + (WORLD_HEIGTH - 1) + "]");
        }

        int xPos = (x  / HOR_STRETCH);
        int yPos = (y / VER_STRETCH);
        return yPos * GRID_WIDTH + xPos;
    }

    private static void changePos(int x, int y) {
        // Handle a Moving client
        int newNodeNum;
        try {
            newNodeNum = posToNodeId(x, y);
        } catch (InvalidCoordinatesException e) {
            System.out.println(e.getMessage());
            return;
        }
        //check if they should be transferred to a different node given new World position
        if (newNodeNum != nodeNum) {
            //we need to request a transfer from our original node to the new node
            System.out.println("Requesting transfer from node" + nodeNum + " to node " + newNodeNum);
            try {
                Message msg = Message.createMsg(-1, nodeNum, MessageType.CLIENT_TRANSFER_REQ, name, String.valueOf(newNodeNum));
                channel.basicPublish("", queueName, props, SerializationUtils.serialize(msg));

                //stop old consumer
                channel.basicCancel(consumerTag);
                channel.close();
                connection.close();

                //setup a new channel and queues
                nodeNum = newNodeNum;
                queueName = nodeNum + "_queue";
                //here we would change nodeHostName. But all running locally so it stays localhost
                factory.setHost(nodeHostName);
                connection = factory.newConnection();
                channel = connection.createChannel();

                //prepare the name for the queue from node to us
                replyQueueName = channel.queueDeclare().getQueue();
                //prepare props
                final String corrId = UUID.randomUUID().toString();
                props = new AMQP.BasicProperties
                        .Builder()
                        .correlationId(corrId)
                        .replyTo(replyQueueName)
                        .build();

                //inform new node of our arrival
                Message message = new Message(-1, nodeNum, MessageType.CLIENT_ARRIVAL, name);
                channel.basicPublish("", queueName, props, SerializationUtils.serialize(message));

                consumerTag = channel.basicConsume(replyQueueName, true, consumer);
            } catch (IOException | TimeoutException e) {
                e.printStackTrace();
            }
            System.out.println("Transfer to node " + newNodeNum + " complete.");
        }
    }
}
