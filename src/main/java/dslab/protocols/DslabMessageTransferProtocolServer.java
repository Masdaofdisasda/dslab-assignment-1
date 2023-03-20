package dslab.protocols;

public interface DslabMessageTransferProtocolServer extends DslabMessageTransferProtocol{

    void begin();
    void to(String addresses);
    void from(String sender);
    void subject(String subject);
    void data(String data);
    void send();
    void quit();
}
