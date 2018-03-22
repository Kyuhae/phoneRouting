import com.rabbitmq.client.*;
import org.apache.commons.lang3.SerializationUtils;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;


public class Node implements  Runnable {

    private int id;
    private Channel myChannel;
    private List<NeighbourInfo> neighbours;
    private Consumer consumer;
    private List<Client> managedClients;
    private String inQueue;

    public Node(int id, List<NeighbourInfo> neighbors) {
        this.id = id;
        this.neighbours = neighbors;
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

        System.out.println("I'm node " + id);
        System.out.println("My neighbours are:");

        // Create neighbor communication channels
        for (NeighbourInfo n : neighbors) {
            factory = new ConnectionFactory();
            factory.setHost(n.getHostName());

            System.out.println("\t" + n.getNodeId());

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
        System.out.println();


        // Build consumer
        consumer = new DefaultConsumer(myChannel) {
            @Override
            public void handleDelivery (String consumerTag, Envelope envelope,
                                        AMQP.BasicProperties peroperties, byte[] body)
                    throws IOException {

                String message = new String(body, "UTF-8");
                String command = message.split(" ")[0];

                Message msg = SerializationUtils.deserialize(body);

                switch (msg.getType()) {
                    case LOGIN:
                        System.out.println(msg.getBody());
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
}
