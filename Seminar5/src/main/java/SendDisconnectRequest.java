public class SendDisconnectRequest extends AbstractRequest {

    public static final String TYPE = "disconnectMessage";

    public SendDisconnectRequest() {
        setType(TYPE);
    }
}
