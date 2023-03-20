package dslab.protocols;

import java.io.IOException;

public interface DslabMessageAccessProtocolServer extends DslabMessageAccessProtocol{
    void login() throws IOException;
    void list();
    void show(String messageId);
    void delete(String messageId);
    void logout();
    void quit();

}
