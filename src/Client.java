import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.apache.commons.lang3.SerializationUtils;

import java.io.IOException;
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
        int xPos = Integer.parseInt(args[1]);
        int yPos = Integer.parseInt(args[2]);

        String queueName = posToNodeId(xPos, yPos) + "_queue";
        String hostName = "localhost";
        Channel channel;

        //get name and pos form cmdline
        //login to node given by pos :by sending msg on queue pos+"_queue"

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(hostName);

        try {
            Connection connection = factory.newConnection();
            channel = connection.createChannel();
            Message msg = new Message(MessageType.LOGIN, args[0]);
            channel.basicPublish("", queueName, null, SerializationUtils.serialize(msg));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
        }
    }
}

//for next time: need to implement client disconnect + all messaging logic :D