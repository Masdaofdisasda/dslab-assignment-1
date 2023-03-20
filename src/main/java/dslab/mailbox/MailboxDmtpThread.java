package dslab.mailbox;

import dslab.dtos.Email;
import dslab.protocols.DslabMessageTransferProtocolServer;
import dslab.util.Config;
import dslab.util.SocketIOTool;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MailboxDmtpThread extends Thread implements DslabMessageTransferProtocolServer {

    private static final Logger audit = Logger.getLogger("requests");
    private static final Logger errors = Logger.getLogger("errors");

    private final Map<String, ConcurrentHashMap<Long, Email>> mailboxes;
    private final AtomicLong messageSequence;
    private final Socket connection;
    private final SocketIOTool ioTool;
    private final String domain;
    private final Config userConfig;
    private Email email;

    public MailboxDmtpThread(Socket connection, Map<String, ConcurrentHashMap<Long, Email>> mailbox, AtomicLong messageSequence, String domain, String userConfig) throws IOException {
        this.connection = connection;
        this.mailboxes = mailbox;
        this.messageSequence = messageSequence;
        this.domain = domain;
        this.userConfig = new Config(userConfig);
        this.ioTool = new SocketIOTool(
                new OutputStreamWriter(connection.getOutputStream()),
                new InputStreamReader(connection.getInputStream())
        );
    }

    @Override
    public void run() {
        audit.info("starting incoming transfer");
        try {
            messageTransaction();
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
                    audit.log(Level.WARNING, line + " is not supported");
                    ioTool.write("error command not supported");
                    break;
                }
            }

        } catch (IOException e) {
            errors.log(Level.SEVERE, e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void begin() {
        try {
            String begin = ioTool.read();
            if (begin.startsWith(BEGIN)) {
                ioTool.write(OK);
            } else {
                audit.log(Level.WARNING, begin + " is not supported");
                ioTool.write("error protocol error");
                ioTool.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void to(String addresses) {
        addresses = addresses.substring(TO.length());
        email.setRecipients(addresses.split(","));
        int knownRecipients = countKnownDomains(email.getRecipients());
        if (knownRecipients == -1) {
            return;
        }
        if (knownRecipients == 0) {
            audit.log(Level.WARNING, addresses + " error no known domain");
            ioTool.write("error no known domain");
            return;
        }
        ioTool.write(OK + " " + knownRecipients);
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
        if (!email.isComplete()) {
            audit.log(Level.WARNING, "error email is missing " + email.getMissingField());
            ioTool.write("error email is missing " + email.getMissingField());
        } else {
            saveInMailbox(email);
            audit.info(email + " has been received");
            ioTool.write(OK);
            email = new Email();
        }
    }

    @Override
    public void quit() {
        ioTool.write(OK + " bye");
    }

    private int countKnownDomains(String[] recipients) {
        int knownRecipients = 0;
        for (String recipient : recipients) {
            String recipientUser = recipient.split("@")[0];
            String recipientDomain = recipient.split("@")[1];
            if (recipientDomain.endsWith(domain)) {
                if (userConfig.containsKey(recipientUser)) {
                    knownRecipients++;
                } else {
                    ioTool.write("error unknown recipient " + recipientUser);
                    return -1;
                }
            }
        }
        return knownRecipients;
    }

    private void saveInMailbox(Email email) {
            for (String recipient: email.getRecipients()) {
                String user = recipient.split("@")[0];
                if (mailboxes.containsKey(user)) {
                    ConcurrentHashMap<Long, Email> mailbox = mailboxes.get(user);
                    mailbox.putIfAbsent(messageSequence.getAndIncrement(), email);
                    mailboxes.replace(user, mailbox);
                } else {
                    ConcurrentHashMap<Long, Email> mailbox = new ConcurrentHashMap<>();
                    mailbox.putIfAbsent(messageSequence.getAndIncrement(), email);
                    mailboxes.putIfAbsent(user, mailbox);
                }
            }
    }
}
