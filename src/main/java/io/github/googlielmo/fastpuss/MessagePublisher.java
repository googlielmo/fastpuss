package io.github.googlielmo.fastpuss;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;

public class MessagePublisher implements Runnable {

    private static final Logger logger = Logger.getLogger("MessagePublisher");
    private static final int NUM_TOPICS = 100;
    public static final int PORT = 11000;
    private final int port;
    private final InetSocketAddress brokerSocket;

    public MessagePublisher() {
        this(PORT);
    }

    public MessagePublisher(int port) {
        this(port, new InetSocketAddress("localhost", 10000));
    }

    public MessagePublisher(int port, InetSocketAddress brokerSocket) {
        this.port = port;
        this.brokerSocket = brokerSocket;
    }

    @Override
    public void run() {

        try (DatagramSocket socket = new DatagramSocket(port)) {
            Random rand = new Random();
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(rand.nextInt(150));
                } catch (InterruptedException e) {
                    logger.log(Level.WARNING, "sleep interrupted");
                    Thread.currentThread().interrupt(); // restore interrupt state
                    throw e;
                }
                String topic = "topic" + rand.nextInt(NUM_TOPICS);
                String body = Double.toString(rand.nextDouble());
                String msg = String.format("PUB %s\n%d %s", topic, System.currentTimeMillis(), body);

                logger.log(INFO, "publishing to topic "+ topic);

                byte[] bytes = msg.getBytes();
                DatagramPacket p = new DatagramPacket(bytes, 0, bytes.length, brokerSocket);
                socket.send(p);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
