import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.concurrent.TimeoutException;


public class Node implements  Runnable {

    private int id;
    // one queue for logins
    // know the neighbour queues (2 - 4)
    // one queue for messages to this node

    private Channel[] outChannel;
    private Channel myChannel;
    Consumer consumer;

    public Node(int id, NeighbourInfo[] neighbors) {
        this.id = id;

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");

        Connection connection = null;
        try {
            connection = factory.newConnection();
            myChannel = connection.createChannel();
            myChannel.queueDeclare(String.valueOf(id), false, false, false, null);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
        }

        // Create neighbor communication channels
        for (NeighbourInfo n : neighbors) {
            factory = new ConnectionFactory();
            factory.setHost(n.getHostName());

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
    }

    @Override
    public void run() {

    }
}
