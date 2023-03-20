package dslab.transfer;

import dslab.dtos.Email;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TransferListener extends Thread {

    private static final Logger audit = Logger.getLogger("requests");
    private static final Logger errors = Logger.getLogger("errors");
    private static final BlockingQueue<Email> queue = new LinkedBlockingDeque<>(100);

    private final ServerSocket server;
    private final ExecutorService pool = Executors.newFixedThreadPool(20);
    private final List<Socket> connections = new ArrayList<>();

    public TransferListener(ServerSocket server, InetAddress monitoringHost, int monitoringPort) {
        this.server = server;
        for (int i = 0; i < 10; i++) {
            Runnable consumerThread = new TransferConsumerThread(queue, monitoringHost, monitoringPort);
            pool.submit(consumerThread);
        }
    }

    @Override
    public void run() {
        audit.info("start listening for connections");
        try {
            while (true) {
                Socket connection = server.accept();
                connections.add(connection);
                Runnable task = new TransferProducerThread(connection, queue);
                pool.submit(task);
            }
        } catch (IOException e) {
            errors.log(Level.SEVERE, e.getMessage(), e);
        } catch (RuntimeException e) {
            errors.log(Level.SEVERE, e.getMessage(), e);
        } finally {
            audit.info("shutting down");
            try {
                for (int i = 0; i < 1; i++) {
                    queue.put(new Email(null, "@@", null, null));
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
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
