package dslab.monitoring;

/**
 * The monitoring service accepts incoming monitoring packets via UDP. It provides CLI commands to access the
 * information.
 *
 * The purpose of the monitoring server is to receive and display usage statistics of the outgoing traffic
 * of transfers servers. Specifically, it records the amount of messages sent from specific servers and users.
 * This is useful for analyzing message throughput of individual servers and users to, e.g., detect server
 * abuse.
 */
public interface IMonitoringServer extends Runnable {

    /**
     * Starts the server and spawns a thread listening for udp packets
     */
    @Override
    void run();

    /**
     * CLI command to shut down the server. After this method, all resources should be closed, and the application
     * should terminate.
     */
    void shutdown();

    /**
     * CLI command to report usage statistics for transfer servers.
     */
    void servers();

    /**
     * CLI command to report usage statistics for individual senders.
     */
    void addresses();

}
