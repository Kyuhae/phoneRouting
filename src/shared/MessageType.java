package shared;

public enum MessageType {
    // Login messages: Client to Node and Node to Client
    LOGIN,
    // Call messages: Client to Node and Node to Client
    CLIENT_CALL,
    // Call messages: Node to Node
    NODE_CALL,
    // Node wants to reserve a name
    NAME_LOCK_REQ,
    // Node (dis-)agrees with NAME_LOCK_REQ
    NAME_LOCK_REPLY,
    // Node got a name and tells everyone where the client is located
    NAME_LOCK_ANNOUNCE,
    // Node no longer holds a name
    NAME_LOCK_RELEASE,
    // Client wants to move to another node
    CLIENT_TRANSFER_REQ,
    // Client disconnects from Node
    DISCONNECT,
    // Nodes telling each other (everyone) about a moving Client
    TRANSFER,
    // Client telling a new Node "Hi, I'm living with you now"
    CLIENT_ARRIVAL,
    // Propagate routing information between Nodes
    N_RIP
}