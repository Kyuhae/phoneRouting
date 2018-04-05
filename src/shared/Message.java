package shared;

import java.io.Serializable;

public class Message implements Serializable{
    private final int src;

    /*
     * -1 refers to clients
     */
    private final int dest;
    private final MessageType type;
    private final String body;

    public Message(int src, int dest, MessageType type, String body) {
        this.src = src;
        this.dest = dest;
        this.type = type;
        this.body = body;
    }

    public int getSrc() {
        return src;
    }

    public int getDest() {
        return dest;
    }

    public MessageType getType() {
        return type;
    }

    public String getBody() {
        return body;
    }
}
