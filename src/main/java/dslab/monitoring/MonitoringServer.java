package dslab.monitoring;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.Config;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.DatagramSocket;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MonitoringServer implements IMonitoringServer {

    private static final Logger audit = Logger.getLogger("requests");
    private static final Logger errors = Logger.getLogger("errors");

    private final int udpPort; // the port used for instantiating the DatagramSocket
    private DatagramSocket datagramSocket;
    private final HashMap<String, Integer> addresses = new HashMap<>();
    private final HashMap<String, Integer> servers = new HashMap<>();

    private final Shell shell;
    private MonitoringListener listener;

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config      the component config
     * @param in          the input stream to read console input from
     * @param out         the output stream to write console output to
     */
    public MonitoringServer(String componentId, Config config, InputStream in, PrintStream out) {
        this.udpPort = config.getInt("udp.port");

        shell = new Shell(in, out);
        shell.register(this);
        shell.setPrompt(componentId + " > ");
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket(udpPort)) {
            audit.info("starting Server...");
            datagramSocket = socket;
            listener = new MonitoringListener(datagramSocket, addresses, servers);
            listener.start();
            shell.run();
        } catch (IOException e) {
            errors.log(Level.SEVERE, e.getMessage(), e);
            throw new RuntimeException("Cannot listen on UDP port.", e);
        } catch (RuntimeException e) {
            errors.log(Level.SEVERE, e.getMessage(), e);
        } finally {
            // close socket and listening thread
            shutdown();
        }

    }

    @Override
    @Command
    public void addresses() {
        addresses.forEach((k, v) -> shell.out().println(k + " " + v));
    }

    @Override
    @Command
    public void servers() {
        servers.forEach((k, v) -> shell.out().println(k + " " + v));
    }

    @Override
    @Command
    public void shutdown() {
        audit.info("shutting down");
        if (datagramSocket != null) {
            datagramSocket.close();
        }
        listener.interrupt();
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        IMonitoringServer server = ComponentFactory.createMonitoringServer(args[0], System.in, System.out);
        server.run();
    }

}
