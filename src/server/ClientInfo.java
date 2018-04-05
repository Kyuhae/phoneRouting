package server;

public class ClientInfo implements ClientInfo_itf {
    private int nodeId;
    private String name;
    private String queueName;


    public ClientInfo(int nodeId, String name) {
        this.nodeId = nodeId;
        this.name = name;
    }

    public int getNodeId() {
        return nodeId;
    }

    public void setNodeId(int nodeId) {
        this.nodeId = nodeId;
    }

    public String getName() {
        return this.name;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public String getQueueName() {
        // TODO: set sensible default if not set/ Exception?
        return queueName;
    }
}
