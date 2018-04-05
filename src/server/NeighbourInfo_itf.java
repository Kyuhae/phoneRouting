package server;

import com.rabbitmq.client.Channel;

public interface NeighbourInfo_itf {
    int getNodeId();
    String getQueueName();
    String getHostName();
    Channel getChannel();
    void setChannel(Channel channel);
}