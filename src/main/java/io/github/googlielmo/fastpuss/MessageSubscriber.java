package io.github.googlielmo.fastpuss;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

public class MessageSubscriber implements Runnable {

    private static final Logger logger = Logger.getLogger("MessageSubscriber");
    private static final int NUM_TOPICS = 100;
    private static final int MAX_SUBS = 25;
    private static final int MAX_MSG_SIZE = 1024 * 4;
    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("\\s+");
    private static final int PORT = 10001;
    private final int port;
    private final InetSocketAddress brokerSocket;
    private DatagramSocket socket;

    public MessageSubscriber() {
        this(PORT);
    }

    public MessageSubscriber(int port) {
        this(port, new InetSocketAddress("127.0.0.1", 10000));
    }

    public MessageSubscriber(int port, InetSocketAddress brokerSocket) {
        this.port = port;
        this.brokerSocket = brokerSocket;
    }

    @Override
    public void run() {
        try {
            socket = new DatagramSocket(port);
            subscribeAll();

            byte[] buf = new byte[MAX_MSG_SIZE];
            DatagramPacket packet = new DatagramPacket(buf, MAX_MSG_SIZE);

            while (!Thread.currentThread().isInterrupted()) {
                socket.receive(packet);
                String msg = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                handleMessage(msg);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void handleMessage(String msg) {
        String[] parts = SEPARATOR_PATTERN.split(msg, 3);
        if (parts.length < 3) {
            logger.log(WARNING, "malformed message discarded: " + msg);
            return;
        }

        String verb = parts[0];
        if (verb.equals("MSG")) {
            logger.log(INFO, "message received; topic=" + parts[1] + " body=" + parts[2]);
        } else {
            logger.log(WARNING, "unknown message type " + verb);
        }
    }

    private void subscribeAll() throws IOException {
        Random rand = new Random();
        int numSubs = rand.nextInt(MAX_SUBS);
        for (int i = 0; i < numSubs; i++) {
            String topic = "topic" + rand.nextInt(NUM_TOPICS);
            subscribe(topic);
        }
    }

    private void subscribe(String topic) throws IOException {
        logger.log(INFO, "subscribing to " + topic);
        String msg = String.format("SUB %s\n%d", topic, System.currentTimeMillis());
        byte[] bytes = msg.getBytes();
        DatagramPacket packet = new DatagramPacket(bytes, 0, bytes.length, brokerSocket);
        socket.send(packet);
    }
}
