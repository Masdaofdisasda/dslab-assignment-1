package dslab.transfer;

import dslab.Exceptions.DMTProtocolException;
import dslab.dtos.Email;
import dslab.protocols.DslabMessageTransferProtocolClient;
import dslab.util.Config;
import dslab.util.SocketIOTool;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.net.*;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TransferConsumerThread extends Thread implements DslabMessageTransferProtocolClient {

    private static final Logger audit = Logger.getLogger("requests");
    private static final Logger errors = Logger.getLogger("errors");

    private final BlockingQueue<Email> queue;
    private final InetAddress monitoringHost;
    private final int monitoringPort;
    private SocketIOTool ioTool;
    private final Config domains = new Config("domains");
    private InetAddress localAddress;
    private int localPort;

    public TransferConsumerThread(BlockingQueue<Email> queue, InetAddress monitoringHost, int monitoringPort) {
        this.queue = queue;
        this.monitoringHost = monitoringHost;
        this.monitoringPort = monitoringPort;
    }

    @Override
    public void run() {
        audit.info("starting Consumer...");
        try {
            while (true) {
                Email email = queue.take();
                audit.info("Consumer took " + email);
                if (Objects.equals(email.getSender(), "@@")) {
                    return;
                }
                transfer(email);
            }
        } catch (InterruptedException | UnknownHostException e) {
            errors.log(Level.SEVERE, "Shutting down with " + e.getMessage(), e);
        } catch (RuntimeException e) {
            errors.log(Level.SEVERE, e.getMessage(), e);
        }
    }

    private void transfer(Email email) throws UnknownHostException {
        String senderDomain = email.getSender().split("@")[1];
        if (!domains.containsKey(senderDomain)) {
            audit.log(Level.SEVERE, "sender " + senderDomain + " does not exist");
            return;
        }

        Set<String> knownDomains = new HashSet<>();
        Set<String> unknownDomains = new HashSet<>();
        for (String recipient : email.getRecipients())
            collectUniqueDomains(knownDomains, unknownDomains, recipient);

        if (!knownDomains.isEmpty()) {
            for (String domain : knownDomains) {
                try {
                    sendEmail(email, domain);
                } catch (DMTProtocolException e) {
                    errors.log(Level.SEVERE, e.getMessage(), e);
                    sendErrorEmail(email, domains.getString(senderDomain),
                            "There was an error transferring this email");
                }
            }
        } else {
            audit.log(Level.SEVERE, "No given domain was found");
            sendErrorEmail(email, domains.getString(senderDomain),
                    "Email couldn't be sent to these unknown domains: " + knownDomains.toString());
        }
    }

    private void sendErrorEmail(Email email, String domain, String message) {
        try {
            Email errorMail = new Email(
                    new String[]{email.getSender()},
                    "mailer@" + InetAddress.getLocalHost().toString(),
                    message,
                    email.toString()
            );
            sendEmail(errorMail, domain);
        } catch (Exception e) {
            errors.log(Level.SEVERE, e.getMessage(), e);
        }
    }

    private void collectUniqueDomains(Set<String> uniqueDomains, Set<String> unknownDomains, String recipient) {
        String recipientDomain = recipient.split("@")[1];
        if (domains.containsKey(recipientDomain)) {
            uniqueDomains.add(domains.getString(recipientDomain));
        } else unknownDomains.add(recipientDomain);
    }

    private void sendEmail(Email email, String domain) {
        String[] socketAddress = domain.split(":");
        String domainAddress = socketAddress[0];
        int port = Integer.parseInt(socketAddress[1]);

        try (Socket socket = new Socket(domainAddress, port)) {
            this.localAddress = socket.getLocalAddress();
            this.localPort = socket.getLocalPort();
            ioTool = new SocketIOTool(
                    new OutputStreamWriter(socket.getOutputStream()),
                    new InputStreamReader(socket.getInputStream())
            );

            audit.info("Consumer beginning transferring " + email);
            if (!Objects.equals(ioTool.read(), OK + " DMTP")) {
                throw new DMTProtocolException("No response after connecting");
            }

            begin();
            to(email.getRecipientsForDMTP(), email.getRecipients().length);
            from(email.getSender());
            subject(email.getSubject());
            data(email.getData());
            send();
            audit.info(email + " was sent");
            sendToMonitorServer(email.getSender());
            quit();

        } catch (UnknownHostException e) {
            errors.log(Level.SEVERE, "Cannot connect to host: " + e.getMessage(), e);
        } catch (SocketException e) {
            errors.log(Level.SEVERE, "SocketException while handling socket: " + e.getMessage(), e);
        } catch (IOException e) {
            errors.log(Level.SEVERE, e.getMessage(), e);
            throw new UncheckedIOException(e);
        }
    }

    private void sendToMonitorServer(String sender) {
        try (DatagramSocket socket = new DatagramSocket()) {
            byte[] buffer;
            DatagramPacket packet;

            // send request-packet to server
            String monitorData = localAddress.getHostAddress() + ":" + localPort + " " + sender;
            buffer = monitorData.getBytes();
            packet = new DatagramPacket(buffer, buffer.length, monitoringHost, monitoringPort);
            audit.info("send data to monitor server: " + monitorData + monitoringHost + monitoringPort);
            socket.send(packet);

            // create a fresh packet
            // wait for response-packet from server
            packet = new DatagramPacket(new byte[1024], 1024);
            socket.receive(packet);
            String response = new String(packet.getData()).replace("\u0000","");
            audit.info("Received response from Monitoring Server: " + response);

        } catch (UnknownHostException e) {
            errors.log(Level.SEVERE, "Cannot connect to host: " + e.getMessage(), e);
        } catch (SocketException e) {
            errors.log(Level.SEVERE, "SocketException: " + e.getMessage(), e);
        } catch (IOException e) {
            errors.log(Level.SEVERE, e.getMessage(), e);
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void begin() throws IOException {
        sendLine(BEGIN);
    }

    @Override
    public void to(String addresses, int numberOfRecipients) throws IOException {
        ioTool.write(TO + addresses);
        if (!ioTool.read().startsWith(OK)) {
            throw new DMTProtocolException("Error sending " + TO + addresses);
        }
    }

    @Override
    public void from(String sender) throws IOException {
        sendLine(FROM + sender);
    }

    @Override
    public void subject(String subject) throws IOException {
        sendLine(SUBJECT + subject);
    }

    @Override
    public void data(String data) throws IOException {
        sendLine(DATA + data);
    }

    @Override
    public void send() throws IOException {
        sendLine(SEND);
    }

    @Override
    public void quit() {
        ioTool.write(QUIT);
    }

    private void sendLine(String line) throws IOException {
        ioTool.write(line);
        if (!Objects.equals(ioTool.read(), OK)) {
            throw new DMTProtocolException("Error sending " + line);
        }
    }
}
