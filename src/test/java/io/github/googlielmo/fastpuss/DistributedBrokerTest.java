package io.github.googlielmo.fastpuss;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class DistributedBrokerTest {

    private DistributedBroker distributedBroker;
    private ThreadSafeSubscriberManager subscriberManager;
    private ExecutorService executorService;

    @BeforeEach
    public void setUp() {
        subscriberManager = new ThreadSafeSubscriberManager();
        executorService = Executors.newCachedThreadPool();
        DistributedBroker.ClusterConfig config = new DistributedBroker.ClusterConfig(15000, List.of(new InetSocketAddress(15000)));
        distributedBroker = new DistributedBroker(10000, executorService, subscriberManager, config);
    }

    @Test
    public void testPushSyncData() throws IOException {
        Socket socket = Mockito.mock(Socket.class);
        when(socket.getOutputStream()).thenReturn(mock(java.io.OutputStream.class));
        distributedBroker = spy(distributedBroker);
        doReturn(socket).when(distributedBroker).createSocket();

        distributedBroker.pushSyncData("topic1", "client1");

        verify(socket, times(1)).connect(any(InetSocketAddress.class));
        verify(socket.getOutputStream(), times(1)).write(any(byte[].class));
    }

    @Test
    public void testKeepInSync() throws IOException {
        Socket socket = Mockito.mock(Socket.class);
        when(socket.getInputStream()).thenReturn(mock(java.io.InputStream.class));
        when(socket.getOutputStream()).thenReturn(mock(java.io.OutputStream.class));
        distributedBroker = spy(distributedBroker);
        doReturn(socket).when(distributedBroker).createSocket();

        distributedBroker.keepInSync();

        verify(socket, times(1)).accept();
        verify(socket.getInputStream(), times(1)).read(any(byte[].class));
    }

    @Test
    public void testPullSyncData() throws IOException {
        Socket socket = Mockito.mock(Socket.class);
        when(socket.getInputStream()).thenReturn(mock(java.io.InputStream.class));
        distributedBroker = spy(distributedBroker);
        doReturn(socket).when(distributedBroker).createSocket();

        distributedBroker.pullSyncData();

        verify(socket, times(1)).connect(any(InetSocketAddress.class));
        verify(socket.getInputStream(), times(1)).read(any(byte[].class));
    }

    @Test
    public void testAddSubscription() {
        distributedBroker.addSubscription("topic1", "client1");
        assertEquals(1, subscriberManager.getMatchingSubscribers("topic1").size());
    }
}
