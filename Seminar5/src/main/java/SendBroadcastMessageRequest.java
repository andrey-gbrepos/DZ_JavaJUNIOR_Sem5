

public class SendBroadcastMessageRequest extends AbstractRequest {

    public static final String TYPE = "broadcastMessage";

    private String message;

    public SendBroadcastMessageRequest() {
        setType(TYPE);
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }







}