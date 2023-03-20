package dslab.monitoring;

import dslab.dtos.AddressDto;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Listens for incoming packets
 */
public class MonitoringListener extends Thread {

    private static final Logger audit = Logger.getLogger("requests");
    private static final Logger errors = Logger.getLogger("errors");
    private static final MonitorRequestHandler handler = new MonitorRequestHandler();

    private final DatagramSocket socket;
    private final HashMap<String, Integer> addresses;
    private final HashMap<String, Integer> servers;


    public MonitoringListener(DatagramSocket socket, HashMap<String, Integer> addresses, HashMap<String, Integer> servers) {
        this.socket = socket;
        this.addresses = addresses;
        this.servers = servers;
    }

    @Override
    public void run() {
        DatagramPacket packet;

        try {
            while (true) {
                packet = new DatagramPacket(new byte[1024], 1024);

                // wait for incoming packets from client, blocks until something is there
                socket.receive(packet);

                String response = "!error provided message does not fit the expected format: !ping <client-name>";
                AddressDto data = handler.handleRequest(packet);

                if (data != null) {
                    saveStatistics(data);
                    response = "success";
                }

                audit.info(response + " " + packet.getAddress());
                byte[] buffer = response.getBytes(StandardCharsets.US_ASCII);
                packet = new DatagramPacket(buffer, buffer.length, packet.getAddress(), packet.getPort());

                // finally send the response
                socket.send(packet);
            }

        } catch (SocketException e) { // socket is closed
            errors.log(Level.WARNING, "SocketException while waiting for/handling packets: " + e.getMessage(), e);
        } catch (IOException e) {
            errors.log(Level.SEVERE, e.getMessage(), e);
            throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
            errors.log(Level.SEVERE, e.getMessage(), e);
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }

    }

    private void saveStatistics(AddressDto data) {
        String server = data.getHost() + ":" + data.getPort();
        if (servers.containsKey(server)) {
            servers.put(server, servers.get(server) + 1);
        } else {
            servers.put(server, 1);
        }
        String address = data.getEmail();
        if (addresses.containsKey(address)) {
            addresses.put(address, addresses.get(address) + 1);
        } else {
            addresses.put(address, 1);
        }
    }
}
