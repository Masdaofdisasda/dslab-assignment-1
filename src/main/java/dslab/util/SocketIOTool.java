package dslab.util;

import java.io.*;

public class SocketIOTool {
    private final PrintWriter out;
    private final BufferedReader in;

    public SocketIOTool(Writer out, Reader in) {
        this.out = new PrintWriter(out);
        this.in = new BufferedReader(in);
    }

    public void write(String message) {
        out.println(message);
        out.flush();
    }

    public String read() throws IOException {
        return in.readLine();
    }

    public void close() {
        out.close();
    }

}
