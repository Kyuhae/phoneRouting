package server;

public interface ClientInfo_itf {
    int getNodeId();
    String getQueueName();
    void setQueueName(String queueName);
    void setNodeId(int nodeId);
}


