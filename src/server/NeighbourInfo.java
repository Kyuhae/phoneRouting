package server;

import com.rabbitmq.client.*;

public class NeighbourInfo implements NeighbourInfo_itf {
    private int nodeId;
    private String queueName;
    private String hostName;
    private Channel channel;

    public NeighbourInfo(int nodeId, String queueName, String hostName) {
        this.nodeId = nodeId;
        this.queueName = queueName;
        this.hostName = hostName;
    }

    @Override
    public int getNodeId() {
        return nodeId;
    }

    @Override
    public String getQueueName() {
        return queueName;
    }

    @Override
    public String getHostName() {
        return hostName;
    }

    @Override
    public Channel getChannel() {
        return channel;
    }

    @Override
    public void setChannel(Channel channel) {
        this.channel = channel;
    }
}
