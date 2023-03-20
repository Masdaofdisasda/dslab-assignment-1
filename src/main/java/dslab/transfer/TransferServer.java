package dslab.transfer;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.Config;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TransferServer implements ITransferServer, Runnable {

    private static final Logger audit = Logger.getLogger("requests");
    private static final Logger errors = Logger.getLogger("errors");

    private final int tcpPort;
    private final InetAddress monitoringHost;
    private final int monitoringPort;
    private final Shell shell;
    private ServerSocket dmtpSocket;
    private Thread listener;

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config      the component config
     * @param in          the input stream to read console input from
     * @param out         the output stream to write console output to
     */
    public TransferServer(String componentId, Config config, InputStream in, PrintStream out) {
        this.tcpPort = config.getInt("tcp.port");
        try {
            this.monitoringHost = InetAddress.getByName(config.getString("monitoring.host"));
        } catch (UnknownHostException e) {
            errors.log(Level.SEVERE, e.getMessage(), e);
            throw new RuntimeException(e);
        }
        this.monitoringPort = config.getInt("monitoring.port");

        shell = new Shell(in, out);
        shell.register(this);
        shell.setPrompt(componentId + " > ");
    }

    @Override
    public void run() {
        audit.info("starting Transfer Server");
        try (ServerSocket socket = new ServerSocket(tcpPort)) {
            dmtpSocket = socket;
            listener = new TransferListener(dmtpSocket, monitoringHost, monitoringPort);
            listener.start();
            shell.run();
        } catch (IOException e) {
            errors.log(Level.SEVERE, e.getMessage(), e);
        } catch (RuntimeException e) {
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
                errors.log(Level.SEVERE, e.getMessage(), e);
            }
        }
        listener.interrupt();
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        ITransferServer server = ComponentFactory.createTransferServer(args[0], System.in, System.out);
        server.run();
    }

}
