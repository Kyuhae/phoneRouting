package shared;

import java.io.Serializable;
import java.util.StringJoiner;

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

    public static Message createMsg(int src, int dest, MessageType type, String ... parts) {
        StringJoiner sj = new StringJoiner(" ");
        for (String p : parts) {
            sj.add(p);
        }
        return new Message(src, dest, type, sj.toString());
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
