package io.github.googlielmo.fastpuss;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;

public class DistributedBroker extends MessageBroker {

    private static final Logger logger = Logger.getLogger("DistributedBroker");

    public static class ClusterConfig {
        int syncPort; // for this node
        List<InetSocketAddress> nodes; // all nodes, including this one

        public ClusterConfig(int syncPort) {
            // one node (useful for local testing and debugging)
            this(syncPort, new ArrayList<>(List.of(new InetSocketAddress(syncPort))));
        }

        public ClusterConfig(int syncPort, List<InetSocketAddress> nodes) {
            this.syncPort = syncPort;
            this.nodes = nodes;
        }
    }

    static final int SYNC_PORT = 15000;

    final ClusterConfig config;

    public DistributedBroker() {
        this.config = new ClusterConfig(SYNC_PORT);
    }

    public DistributedBroker(ClusterConfig config) {
        this.config = config;
    }

    public DistributedBroker(int port, ClusterConfig config) {
        super(port);
        this.config = config;
    }

    public DistributedBroker(int port, ExecutorService executorService, ThreadSafeSubscriberManager subscriberManager, ClusterConfig config) {
        super(port, executorService, subscriberManager);
        this.config = config;
    }

    @Override
    public void broker() {
        pullSyncData();
        Executors.newSingleThreadExecutor().execute(this::keepInSync);
        super.broker();
    }

    @Override
    protected void addSubscription(String topic, String clientId) {
        super.addSubscription(topic, clientId);
        pushSyncData(topic, clientId);
    }

    private void pushSyncData(String topic, String clientId) {
        Random rand = new Random();
        int numNodes = config.nodes.size();
        int startNode = rand.nextInt(numNodes);

        for (int i = 0; i < numNodes; i++) {
            try {
                InetSocketAddress node = config.nodes.get((startNode + i) % numNodes);
                if (isLocalAddress(node.getAddress())) {
                    continue;
                }
                logger.info("sending sync data to node " + node);
                Socket socket = new Socket();
                socket.connect(node);
                new OutputStreamWriter(socket.getOutputStream()).write("PUSH\n");
                sendSyncDataUpdate(socket, topic, clientId);
                socket.close();
            } catch (IOException e) {
                logger.log(SEVERE, "sync error", e);
            }
        }
    }

    void keepInSync() {
        ServerSocket serverSocket;

        int syncPort = SYNC_PORT;
        try {
            serverSocket = new ServerSocket(syncPort);
            logger.info("listening to sync port " + syncPort);
        } catch (IOException e) {
            logger.log(SEVERE, "cannot listen to sync port " + syncPort, e);
            return;
        }
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Socket socket = serverSocket.accept();
                logger.info("new sync request from " + socket.getInetAddress().getHostAddress() + ":" + socket.getPort());
                String cmd = new BufferedReader(new InputStreamReader(socket.getInputStream())).readLine();
                switch (cmd) {
                    case "PULL":
                        sendSyncData(socket);
                        break;

                    case "PUSH":
                        receiveSyncData(socket);
                        break;

                    default:
                        logger.log(WARNING, "unknown verb " + cmd);
                }
                socket.close();
            } catch (IOException e) {
                logger.log(WARNING, "sync error", e);
            }
        }
    }

    private void sendSyncData(Socket socket) throws IOException {
        try (Writer w = new OutputStreamWriter(socket.getOutputStream())) {
            subscriberManager.marshal(w);
            w.flush();
        }
    }

    private void sendSyncDataUpdate(Socket socket, String topic, String clientId) throws IOException {
        try (Writer w = new OutputStreamWriter(socket.getOutputStream())) {
            w.write(topic);
            w.write("\n");
            w.write(clientId);
            w.write("\n\n");
            w.flush();
        }
    }

    void pullSyncData() {
        Random rand = new Random();
        int numNodes = config.nodes.size();
        int startNode = rand.nextInt(numNodes);

        for (int i = 0; i < numNodes; i++) {
            try {
                InetSocketAddress node = config.nodes.get((startNode + i) % numNodes);
                if (isLocalAddress(node.getAddress())) {
                    continue;
                }
                logger.info("requesting sync data from node " + node);
                Socket socket = new Socket();
                socket.connect(node);
                new OutputStreamWriter(socket.getOutputStream()).write("PULL\n");
                receiveSyncData(socket);
                socket.close();
                return;
            } catch (IOException e) {
                logger.log(SEVERE, "sync error", e);
            }
        }
    }

    private void receiveSyncData(Socket socket) throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            subscriberManager.unmarshal(r);
        }
    }

    /**
     * @see <a href="https://stackoverflow.com/a/2406819">this Stack Overflow answer</a>
     */
    private boolean isLocalAddress(InetAddress addr) {
        // Check if the address is a valid special local or loop back
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress())
            return true; // Was local subnet.

        // Check if the Non-local address is defined on any Local-interface.
        try {
            return NetworkInterface.getByInetAddress(addr) != null;
        } catch (SocketException e) {
            return false;
        }
    }
}
