import com.rabbitmq.client.*;

public class NeighbourInfo {
    private int nodeId;
    private String queueName;
    private String hostName;
    private Channel channel;

    public NeighbourInfo(int nodeId, String queueName, String hostName) {
        this.nodeId = nodeId;
        this.queueName = queueName;
        this.hostName = hostName;
    }

    public int getNodeId() {
        return nodeId;
    }

    public String getQueueName() {
        return queueName;
    }

    public String getHostName() {
        return hostName;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }
}
