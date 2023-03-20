package dslab.monitoring;

import dslab.dtos.AddressDto;

import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.util.logging.Logger;

/**
 * checks if the message has the correct format for the monitor server
 */
public class MonitorRequestHandler {

    private static final Logger audit = Logger.getLogger("requests");
    private static final Logger errors = Logger.getLogger("errors");

    /**
     * handles the given request and checks if the request has the format <host>:<port> <email-address>
     *
     * @param packet that was recieved
     * @return the host, port and email as a dto object
     * @throws UnsupportedEncodingException
     */
    public AddressDto handleRequest(DatagramPacket packet) throws UnsupportedEncodingException {

        // get the data from the packet
        String request = new String(packet.getData());
        request = request.replace("\u0000","");

        audit.info("Received request-packet from client: " + request);

        return getResponse(request);
    }

    private AddressDto getResponse(String request) {


        // check if request has the correct format:
        // <host>:<port> <email-address>
        String[] lines = request.split("\\n");
        if (lines.length != 1) {
            return null;
        }
        String[] parts = lines[0].split("\\s");
        if (parts.length != 2) {
            return null;
        }
        String[] address = parts[0].split(":");
        if (address.length != 2) {
            return null;
        }
         return new AddressDto(
                address[0],
                address[1],
                parts[1]
        );

    }
}
