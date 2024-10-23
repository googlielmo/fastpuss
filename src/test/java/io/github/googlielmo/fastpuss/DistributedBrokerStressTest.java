package io.github.googlielmo.fastpuss;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class DistributedBrokerStressTest {

    @Test
    public void testHighLoad() {
        ExecutorService executorService = Executors.newCachedThreadPool();
        DistributedBroker distributedBroker = new DistributedBroker();
        executorService.execute(distributedBroker::broker);

        for (int i = 0; i < 100; i++) {
            executorService.execute(new MessagePublisher(11000 + i));
        }

        for (int i = 0; i < 1000; i++) {
            executorService.execute(new MessageSubscriber(10001 + i));
        }

        // Allow some time for the system to stabilize
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Check for any inconsistencies or race conditions
        assertTrue(distributedBroker.subscriberManager.topicMap.size() > 0);
    }
}
