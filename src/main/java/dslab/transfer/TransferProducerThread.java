package dslab.transfer;

import dslab.dtos.Email;
import dslab.protocols.DslabMessageTransferProtocolServer;
import dslab.util.SocketIOTool;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class TransferProducerThread extends Thread implements DslabMessageTransferProtocolServer {

    private static final Logger audit = Logger.getLogger("requests");
    private static final Logger errors = Logger.getLogger("errors");

    private final Socket connection;
    private final BlockingQueue<Email> queue;
    private final SocketIOTool ioTool;
    private Email email;

    public TransferProducerThread(Socket connection, BlockingQueue<Email> queue) throws IOException {
        this.connection = connection;
        this.ioTool = new SocketIOTool(
                new OutputStreamWriter(connection.getOutputStream()),
                new InputStreamReader(connection.getInputStream())
        );
        this.queue = queue;
    }

    @Override
    public void run() {
        audit.info("starting Producer");
        try {
            messageTransaction();
        } catch (RuntimeException e) {
            errors.log(Level.SEVERE, e.getMessage(), e);
        } finally {
            try {
                connection.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    public void messageTransaction() {
        email = new Email();

        try {
            // tell the client he is connected
            ioTool.write(OK + " DMTP");

            begin();

            while (true) {
                String line = ioTool.read();
                if (line.startsWith(TO)) {
                    to(line);
                } else if (line.startsWith(FROM)) {
                    from(line);
                } else if (line.startsWith(SUBJECT)) {
                    subject(line);
                } else if (line.startsWith(DATA)) {
                    data(line);
                } else if (line.equals(SEND)) {
                    send();
                } else if (line.equals(QUIT)) {
                    quit();
                    break;
                } else {
                    audit.log(Level.SEVERE, line + " is not supported");
                    ioTool.write("error command not supported");
                    break;
                }
            }

        } catch (IOException e) {
            errors.log(Level.SEVERE, e.getMessage(), e);
            throw new RuntimeException(e);
        } catch (RuntimeException e) {
            errors.log(Level.SEVERE, e.getMessage(), e);
        }
    }


    @Override
    public void begin() {
        try {
            String line = ioTool.read();
            if (line.startsWith(BEGIN)) {
                ioTool.write(OK);
            } else {
                audit.log(Level.SEVERE, line + " is not supported");
                ioTool.write("error protocol error");
                ioTool.close();
            }
        } catch (IOException e) {
            errors.log(Level.SEVERE, e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void to(String addresses) {
        addresses = addresses.substring(TO.length());
        email.setRecipients(addresses.split(","));
        for (String address : email.getRecipients()) {
            if (!Pattern.matches("[A-Za-z0-9.]*@[A-Za-z0-9.]*", address)) {
                audit.log(Level.SEVERE, "error invalid email pattern " + address);
                ioTool.write("error invalid email pattern " + address);
                return;
            }
        }
        ioTool.write(OK + " " + email.getRecipients().length);
    }

    @Override
    public void from(String sender) {
        email.setSender(sender.substring(FROM.length()));
        ioTool.write(OK);
    }

    @Override
    public void subject(String subject) {
        email.setSubject(subject.substring(SUBJECT.length()));
        ioTool.write(OK);
    }

    @Override
    public void data(String data) {
        email.setData(data.substring(DATA.length()));
        ioTool.write(OK);
    }

    @Override
    public void send() {
        try {
            if (email.isComplete()) {
                queue.put(email);
                audit.info(email + " was received and will be transferred");
                ioTool.write(OK);
                email = new Email();
            } else {
                audit.log(Level.SEVERE, "error email is missing " + email.getMissingField());
                ioTool.write("error email is missing " + email.getMissingField());
            }
        } catch (InterruptedException e) {
            errors.log(Level.SEVERE, e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void quit() {
        ioTool.write(OK + " bye");
    }
}
