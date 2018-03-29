import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.apache.commons.lang3.SerializationUtils;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

public class Client {

    static int posToNodeId(int x, int y) {
        //TODO change this for actual coordinate mapping
        return x;
    }



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
            System.out.println("Dude, seriously, it's not that hard to pass 2 integers.");
            nfe.printStackTrace();
            System.exit(1);
        }
        //setup queue from us to node
        String queueName = posToNodeId(xPos, yPos) + "_queue";
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
        } catch (IOException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
        }
    }
}

//for next time: need to implement client disconnect + all messaging logic :D