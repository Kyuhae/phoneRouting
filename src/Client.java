import com.rabbitmq.client.*;
import org.apache.commons.lang3.SerializationUtils;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeoutException;

public class Client {

    private static Consumer consumer;

    public static void main(String[] args) {
        //clientName, clientX, clientY
        String name = args[0];
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
        Channel channel;

        //get name and pos form cmdline
        //login to node given by pos :by sending msg on queue pos+"_queue"

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(nodeHostName);

        try {
            Connection connection = factory.newConnection();
            channel = connection.createChannel();

            //prepare the name for the queue from node to us
            String replyQueueName = channel.queueDeclare().getQueue();
            //prepare props
            final String corrId = UUID.randomUUID().toString();
            AMQP.BasicProperties props = new AMQP.BasicProperties
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
                    response.offer(msgBody);
                }
            };

            channel.basicConsume(replyQueueName, true, consumer);

            System.out.println(response.take());

        } catch (TimeoutException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    static int posToNodeId(int x, int y) throws InvalidCoordinatesException{
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