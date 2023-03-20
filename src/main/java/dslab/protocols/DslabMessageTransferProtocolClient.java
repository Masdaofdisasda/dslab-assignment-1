package dslab.protocols;

import java.io.IOException;

public interface DslabMessageTransferProtocolClient extends DslabMessageTransferProtocol{

    void begin() throws IOException;
    void to(String addresses, int numberOfRecipients) throws IOException;
    void from(String sender) throws IOException;
    void subject(String subject) throws IOException;
    void data(String data) throws IOException;
    void send() throws IOException;
    void quit();
}
