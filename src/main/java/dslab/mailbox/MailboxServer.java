package dslab.mailbox;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.dtos.Email;
import dslab.util.Config;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MailboxServer implements IMailboxServer, Runnable {

    private static final Logger audit = Logger.getLogger("requests");
    private static final Logger errors = Logger.getLogger("errors");

    private final String domain;
    private final String userConfig;
    private final int dmtpTcpPort;
    private final int dmapTcpPort;
    private final HashMap<String, ConcurrentHashMap<Long, Email>> mailbox = new HashMap<>();
    private final AtomicLong messageSequence = new AtomicLong(0L);

    private ServerSocket dmtpSocket;
    private ServerSocket dmapSocket;
    private MailboxDmtpListener dmtpListener;
    private MailboxDmapListener dmapListener;
    private final Shell shell;

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config      the component config
     * @param in          the input stream to read console input from
     * @param out         the output stream to write console output to
     */
    public MailboxServer(String componentId, Config config, InputStream in, PrintStream out) {
        this.domain = config.getString("domain");
        this.userConfig = config.getString("users.config");
        this.dmtpTcpPort = config.getInt("dmtp.tcp.port");
        this.dmapTcpPort = config.getInt("dmap.tcp.port");

        shell = new Shell(in, out);
        shell.register(this);
        shell.setPrompt(componentId + " > ");
    }

    @Override
    public void run() {
        audit.info("starting Mailbox Server");
        try (ServerSocket dmtp = new ServerSocket(dmtpTcpPort);
             ServerSocket dmap = new ServerSocket(dmapTcpPort)) {

            dmtpSocket = dmtp;
            dmtpListener = new MailboxDmtpListener(dmtpSocket, mailbox, messageSequence, domain, userConfig);
            dmtpListener.start();

            dmapSocket = dmap;
            dmapListener = new MailboxDmapListener(dmapSocket, mailbox, domain, userConfig);
            dmapListener.start();

            shell.run();
        } catch (IOException e) {
            errors.log(Level.SEVERE, e.getMessage(), e);
            throw new UncheckedIOException("Error while creating server socket", e);
        }  catch (RuntimeException e) {
            errors.log(Level.SEVERE, e.getMessage(), e);
        } finally {
            shutdown();
        }
    }

    @Override
    @Command
    public void shutdown() {
        audit.info("shutting down");
        if (dmtpSocket != null) {
            try {
                dmtpSocket.close();
            } catch (IOException e) {
                errors.log(Level.WARNING, "Error while closing server socket: " + e.getMessage(), e);
            }
        }
        dmtpListener.interrupt();
        if (dmapSocket != null) {
            try {
                dmapSocket.close();
            } catch (IOException e) {
                errors.log(Level.WARNING, "Error while closing server socket: " + e.getMessage(), e);
            }
        }
        dmapListener.interrupt();
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        IMailboxServer server = ComponentFactory.createMailboxServer(args[0], System.in, System.out);
        server.run();
    }
}
