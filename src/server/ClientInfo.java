package server;

public class ClientInfo implements ClientInfo_itf {
    private int nodeId;
    private String queueName;


    public ClientInfo(int nodeId) {
        this.nodeId = nodeId;
    }

    public int getNodeId() {
        return nodeId;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public String getQueueName() {
        // TODO: set sensible default if not set/ Exception?
        return queueName;
    }
    public void setNodeId(int id) {
        nodeId = id;
    }
}
