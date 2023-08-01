package io.github.googlielmo.fastpuss;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;

public class MessageBroker {

    private static final Logger logger = Logger.getLogger("MessageBroker");
    private static final int PORT = 10000;
    private static final int RCV_BUF_SIZE = 1024 * 64;
    private static final int MAX_MSG_SIZE = 1024 * 4;
    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("\\s+");
    private final ExecutorService executorService;
    private final int port;
    private DatagramSocket socket;
    protected final ThreadSafeSubscriberManager subscriberManager;

    /**
     * Create a default broker listening on the default port
     */
    public MessageBroker() {
        this(PORT, Executors.newCachedThreadPool(), new ThreadSafeSubscriberManager());
    }

    /**
     * @param port UDP port number for listening
     */
    public MessageBroker(int port) {
        this(port, Executors.newCachedThreadPool(), new ThreadSafeSubscriberManager());
    }

    /**
     * @param port              UDP port number for listening
     * @param executorService   {@link ExecutorService}
     * @param subscriberManager {@link ThreadSafeSubscriberManager}
     */
    public MessageBroker(int port, ExecutorService executorService, ThreadSafeSubscriberManager subscriberManager) {
        this.port = port;
        this.executorService = executorService;
        this.subscriberManager = subscriberManager;
    }

    public void broker() {
        try {
            socket = createBrokerSocket(port);
            logger.info("receiving on port " + port);

            byte[] buf = new byte[MAX_MSG_SIZE];
            DatagramPacket packet = new DatagramPacket(buf, MAX_MSG_SIZE);

            while (!Thread.currentThread().isInterrupted()) {
                socket.receive(packet);
                String msg = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                String clientId = packet.getSocketAddress().toString();
                executorService.execute(() -> handleMessage(msg, clientId));
            }
        } catch (IOException e) {
            logger.log(SEVERE, "error receiving on port " + port, e);
        }
    }

    private DatagramSocket createBrokerSocket(int port) throws SocketException {
        DatagramSocket socket = new DatagramSocket(null);
        socket.setReceiveBufferSize(RCV_BUF_SIZE);
        socket.bind(new InetSocketAddress(port)); // UDP *:10000
        return socket;
    }

    private void handleMessage(String msg, String clientId) {
        String[] parts = SEPARATOR_PATTERN.split(msg, 3);
        if (parts.length < 3) {
            logger.log(WARNING, "malformed message discarded: " + msg);
            return;
        }

        String verb = parts[0];
        String topic = parts[1];
        String body = parts[2];

        switch (verb) {
            case "SUB":
                addSubscription(topic, clientId);
                break;

            case "PUB":
                publish(topic, body, subscriberManager.getMatchingSubscribers(topic));
                break;

            default:
                logger.log(WARNING, "unknown verb " + verb);
        }
    }

    protected void addSubscription(String topic, String clientId) {
        subscriberManager.addSubscription(topic, clientId);
    }

    private void publish(String topic, String body, Collection<String> matchingSubscribers) {
        matchingSubscribers.forEach(
                clientId -> {
                    String outPacket = String.format("MSG %s\n%s", topic, body);
                    InetSocketAddress clientSocket = clientId2SocketAddress(clientId);
                    byte[] packetBytes = outPacket.getBytes();
                    DatagramPacket datagramPacket = new DatagramPacket(packetBytes, 0, packetBytes.length, clientSocket);
                    try {
                        socket.send(datagramPacket);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
        );
    }

    private InetSocketAddress clientId2SocketAddress(String clientId) {
        // clientIds have the form "/127.0.0.1:10002"
        int sep = clientId.indexOf(":");
        String hostname = clientId.substring(1, sep);
        int port = Integer.parseInt(clientId.substring(sep + 1));
        return new InetSocketAddress(hostname, port);
    }
}
