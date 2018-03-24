import java.io.Serializable;

public class Message implements Serializable{
    private MessageType type;
    private String body;

    public Message(MessageType type, String body) {
        this.type = type;
        this.body = body;

    }

    public MessageType getType() {
        return type;
    }

    public String getBody() {
        return body;
    }
}
