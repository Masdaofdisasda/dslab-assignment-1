package dslab.mailbox;

import dslab.dtos.Email;
import dslab.protocols.DslabMessageAccessProtocolServer;
import dslab.util.Config;
import dslab.util.SocketIOTool;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MailboxDmapThread extends Thread implements DslabMessageAccessProtocolServer {

    private static final Logger audit = Logger.getLogger("requests");
    private static final Logger errors = Logger.getLogger("errors");

    private final HashMap<String, ConcurrentHashMap<Long, Email>> mailboxes;
    private final Socket connection;
    private final SocketIOTool ioTool;
    private final String domain;
    private final Config userConfig;
    private String connectedUser;

    public MailboxDmapThread(Socket connection, HashMap<String, ConcurrentHashMap<Long, Email>> mailboxes, String domain, String userConfig) throws IOException {
        this.mailboxes = mailboxes;
        this.connection = connection;
        this.ioTool = new SocketIOTool(
                new OutputStreamWriter(connection.getOutputStream()),
                new InputStreamReader(connection.getInputStream())
        );
        this.domain = domain;
        this.userConfig = new Config(userConfig);
    }


    @Override
    public void run() {
        try {
            audit.info("serving Client");
            serveClient();
        } finally {
            try {
                connection.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    private void serveClient() {

        try {
            ioTool.write(OK + " DMAP");


            while (true) {

                if (connectedUser == null) {
                    login();
                }

                String line = ioTool.read();
                if (line.startsWith(LIST)) {
                    list();
                } else if (line.startsWith(SHOW)) {
                    show(line);
                } else if (line.startsWith(DELETE)) {
                    delete(line);
                } else if (line.startsWith(LOGOUT)) {
                    logout();
                } else if (line.startsWith(QUIT)) {
                    quit();
                    break;
                } else {
                    audit.log(Level.WARNING, line + " is not supported");
                    ioTool.write("error command not supported");
                    break;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            errors.log(Level.SEVERE, e + e.getMessage());
        }
    }

    @Override
    public void login() throws IOException {
        audit.info("waiting for login");
        while (true) {
            String line = ioTool.read();
            if (line.startsWith(LOGIN)) {
                String username = line.split(" ")[1];
                String password = line.split(" ")[2];
                if (!userConfig.containsKey(username)) {
                    audit.log(Level.WARNING, username + " error unknown user");
                    ioTool.write("error unknown user");
                    break;
                } else if (!userConfig.getString(username).equals(password)) {
                    audit.log(Level.WARNING, password + " error wrong password");
                    ioTool.write("error wrong password");
                    break;
                } else {
                    connectedUser = username;
                    audit.info(username + " logged in");
                    ioTool.write(OK);
                    return;
                }
            } if (line.startsWith(QUIT)) {
                quit();
            } else {
                audit.log(Level.WARNING, line + " is not supported");
                ioTool.write("error command not supported");
                ioTool.close();
            }
        }
    }

    @Override
    public void list() {
        if (mailboxNotExists()) {
            audit.log(Level.WARNING, "error no mailbox found");
            ioTool.write("error no mailbox found");
            return;
        }
        ConcurrentHashMap<Long, Email> mailbox = mailboxes.get(connectedUser);
        StringBuilder builder = new StringBuilder();
        mailbox.forEach((k, v) -> builder
                .append(k).append(" ").append(v.getSender()).append(" ").append(v.getSubject()).append("\n"));
        ioTool.write(builder.toString());
    }

    @Override
    public void show(String messageId) {
        if (mailboxNotExists()) {
            audit.log(Level.WARNING, "error no mailbox found");
            ioTool.write("error no mailbox found");
            return;
        }
        ConcurrentHashMap<Long, Email> mailbox = mailboxes.get(connectedUser);
        long messageKey;
        try {
            messageKey = Long.parseLong(messageId.substring(DELETE.length()));
            if (mailNotExists(mailbox, messageKey)) {
                audit.log(Level.WARNING, "error no mail with id " + messageKey);
                ioTool.write("error no mail with id " + messageKey);
                return;
            }
            Email email = mailbox.get(messageKey);
            StringBuilder builder = new StringBuilder();
            builder.append("from " + email.getSender() + "\n")
                    .append("to " + email.getRecipientsForDMTP() + "\n")
                    .append("subject " + email.getSubject() + "\n")
                    .append("data " + email.getData());
            ioTool.write(builder.toString());
        } catch (NumberFormatException e) {
            audit.log(Level.WARNING, "error invalid number");
            ioTool.write("error invalid number");
        }
    }

    @Override
    public void delete(String messageId) {
        if (mailboxNotExists()) {
            ioTool.write("error no mailbox found");
            return;
        }
        ConcurrentHashMap<Long, Email> mailbox = mailboxes.get(connectedUser);
        long messageKey;
        try {
            messageKey = Long.parseLong(messageId.substring(DELETE.length()));
            if (mailNotExists(mailbox, messageKey)) {
                audit.log(Level.WARNING, "error no mail with id " + messageKey);
                ioTool.write("error no mail with id " + messageKey);
                return;
            }
            mailbox.remove(messageKey);
            mailboxes.replace(connectedUser, mailbox);
            ioTool.write(OK);
        } catch (NumberFormatException e) {
            audit.log(Level.WARNING, "error invalid number");
            ioTool.write("error invalid number");
        }
    }

    @Override
    public void logout() {
        connectedUser = null;
        ioTool.write(OK);
    }

    @Override
    public void quit() {
        connectedUser = null;
        ioTool.write(OK + " bye");
        ioTool.close();
    }

    private static boolean mailNotExists(ConcurrentHashMap<Long, Email> mailbox, long messageKey) {
        return !mailbox.containsKey(messageKey);
    }

    private boolean mailboxNotExists() {
        return !mailboxes.containsKey(connectedUser);
    }
}
