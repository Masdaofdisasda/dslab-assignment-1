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
import java.util.logging.Level;
import java.util.logging.Logger;

public class MailboxDmapListener extends Thread {

    private static final Logger audit = Logger.getLogger("requests");
    private static final Logger errors = Logger.getLogger("errors");
    private final HashMap<String, ConcurrentHashMap<Long, Email>> mailbox;
    private final String domain;
    private final String userConfig;
    private final ServerSocket server;
    private final ExecutorService pool = Executors.newFixedThreadPool(10);
    private final List<Socket> connections = new ArrayList<>();

    public MailboxDmapListener(ServerSocket server, HashMap<String, ConcurrentHashMap<Long, Email>> mailbox, String domain, String userConfig) {
        this.server = server;
        this.mailbox = mailbox;
        this.domain = domain;
        this.userConfig = userConfig;
    }

    @Override
    public void run() {
        audit.info("start listening for client connections (DMAP)");
        try {
            while (true) {
                Socket connection = server.accept();
                connections.add(connection);
                Runnable task = new MailboxDmapThread(connection, mailbox, domain, userConfig);
                pool.submit(task);
            }
        } catch (IOException e) {
            errors.log(Level.SEVERE, e.getMessage());
            throw new RuntimeException(e);
        } catch (Exception e) {
            errors.log(Level.SEVERE, e.getMessage());
        } finally {
            connections.forEach(con -> {
                try {
                    con.close();
                } catch (IOException e) {
                    // ignore
                }
            });
            audit.info("shutting down");
            pool.shutdownNow();
        }
    }
}
