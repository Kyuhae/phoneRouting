package client;

import com.rabbitmq.client.*;
import org.apache.commons.lang3.SerializationUtils;
import shared.CommunicationConstants;
import shared.Message;
import shared.MessageType;

import java.io.IOException;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeoutException;

public class Client {

    private static Consumer consumer;
    private static String name;
    private static Channel channel;
    private static String queueName;
    private static String replyQueueName;
    private static AMQP.BasicProperties props;

    public static void main(String[] args) {
        //clientName, clientX, clientY
        name = args[0];
        // He who passes invalid parameters deserves the crash and burn
        int xPos = 0;
        int yPos = 0;
        try {
            xPos = Integer.parseInt(args[1]);
            yPos = Integer.parseInt(args[2]);
        } catch (NumberFormatException nfe) {
            System.out.println("The x and y position need to be given as integers. Call the program with <String, Integer, Integer>");
            nfe.printStackTrace();
            System.exit(1);
        }
        //setup queue from us to node
        String queueName = null;
        try {
            queueName = posToNodeId(xPos, yPos) + "_queue";
            System.out.println("Pos: " + posToNodeId(xPos, yPos));
        } catch (InvalidCoordinatesException e) {
            System.out.println(e.getMessage());
            return;
        }

        String nodeHostName = "localhost";
        //get name and pos form cmdline
        //login to node given by pos :by sending msg on queue pos+"_queue"

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(nodeHostName);

        try {
            Connection connection = factory.newConnection();
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

            Message msg = new Message(MessageType.LOGIN, args[0]);
            channel.basicPublish("", queueName, props, SerializationUtils.serialize(msg));

            final BlockingQueue<String> response = new ArrayBlockingQueue<String>(1);

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
                        case CALL:
                            break;
                        case RIP:
                            break;
                        default:
                            System.out.println("Who is sending useless messages here?");
                    }
                }
            };

            channel.basicConsume(replyQueueName, true, consumer);

        } catch (IOException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
        }


    }

    private static void handleLogin(String message) {
        switch(message) {
            case "success":
                System.out.println("Successfully logged in!");
                break;

            case "nameInUse":
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
                    Message msg = new Message(MessageType.LOGIN, name);
                    channel.basicPublish("", queueName, props, SerializationUtils.serialize(msg));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;

            default:
                System.out.println("unrecognized LOGIN message type");

        }
    }

    static int posToNodeId(int x, int y) throws InvalidCoordinatesException {
        if (x < 0 || x > CommunicationConstants.WORLD_WIDTH - 1 || y < 0 || y > CommunicationConstants.WORLD_HEIGTH - 1) {
            throw new InvalidCoordinatesException("Coordinates out of range. Valid ranges are: \n" +
                    "\tx: [0, " + (CommunicationConstants.WORLD_WIDTH - 1) + "]\n" +
                    "\ty: [0, " + (CommunicationConstants.WORLD_HEIGTH - 1) + "]");
        }

        int xPos = (x  / CommunicationConstants.HOR_STRETCH);
        int yPos = (y / CommunicationConstants.VER_STRETCH);
        return yPos * CommunicationConstants.GRID_WIDTH + xPos;
    }
}

//for next time: need to implement client disconnect + all messaging logic :D

