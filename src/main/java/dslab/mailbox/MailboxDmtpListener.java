package dslab.mailbox;

import dslab.dtos.Email;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles DMTP request for the MailboxServer and spawns threads for new connections
 */
public class MailboxDmtpListener extends Thread {

    private static final Logger audit = Logger.getLogger("requests");
    private static final Logger errors = Logger.getLogger("errors");
    private final HashMap<String, ConcurrentHashMap<Long, Email>> mailbox;
    private final AtomicLong messageSequence;
    private final String domain;
    private final String userConfig;
    private final ServerSocket socket;
    private final ExecutorService pool = Executors.newFixedThreadPool(10);
    private final List<Socket> connections = new ArrayList<>();

    public MailboxDmtpListener(ServerSocket socket, HashMap<String, ConcurrentHashMap<Long, Email>> mailbox, AtomicLong messageSequence, String domain, String userConfig) {
        this.socket = socket;
        this.mailbox = mailbox;
        this.messageSequence = messageSequence;
        this.domain = domain;
        this.userConfig = userConfig;
    }

    @Override
    public void run() {
        audit.info("start listening for incoming transfers (DMTP)");
        try {
            while (true) {
                Socket connection = socket.accept();
                connections.add(connection);
                Runnable task = new MailboxDmtpThread(connection, mailbox, messageSequence, domain, userConfig);
                pool.submit(task);
            }
        } catch (IOException e) {
            errors.log(Level.SEVERE, e.getMessage(), e);
            throw new RuntimeException(e);
        } catch (RuntimeException e) {
            errors.log(Level.SEVERE, e.getMessage(), e);
        } finally {
            audit.info("shutting down");
            connections.forEach(con -> {
                try {
                    con.close();
                } catch (IOException e) {
                    // ignore
                }
            });
            pool.shutdownNow();
        }
    }
}
