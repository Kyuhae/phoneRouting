package server;

public class ClientInfo implements ClientInfo_itf {
    private int nodeId;
    private String name;
    private String queueName;


    public ClientInfo(String name, String queueName) {
        this.name = name;
        this.queueName = queueName;
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

    public String getQueueName() {
        return queueName;
    }
}
