package io.github.googlielmo.fastpuss;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ThreadSafeSubscriberManagerTest {

    public static final int MILLIONS = 2_000_000;
    private @NotNull ThreadSafeSubscriberManager subscriberManager;

    @BeforeEach
    public void setUp() {
        subscriberManager = new ThreadSafeSubscriberManager();
    }

    @Test
    public void assertMillionClients() {
        Random rand = new Random();
        for (int i = 0; i < MILLIONS; i++) {
            int howManyTopics = rand.nextInt(10);
            for (int j = 0; j <= howManyTopics; j++) {
                subscriberManager.addSubscription("topic" + j, "client-" + i);
            }
        }
        assertTrue(subscriberManager.getMatchingSubscribers("topic0").contains("client-" + (MILLIONS - 1)));
    }

    @Test
    public void assertMillionTopics() {
        Random rand = new Random();
        int howManyClients = rand.nextInt(10);
        for (int i = 0; i <= howManyClients; i++) {
            for (int j = 0; j < MILLIONS; j++) {
                subscriberManager.addSubscription("topic" + j, "client-" + i);
            }
        }
        assertTrue(subscriberManager.getMatchingSubscribers("topic" + (MILLIONS - 1)).contains("client-0"));
    }

    @Test
    public void getMatchingSubscribers_returnsExactMatches() {
        subscriberManager.addSubscription("fastpuss/firstfloor/livingroom/temperature", "client-1");
        subscriberManager.addSubscription("fastpuss/firstfloor/livingroom/temperature", "client-3");
        subscriberManager.addSubscription("fastpuss/firstfloor/kitchen/temperature", "client-2");
        assertSubscribersMatch("fastpuss/firstfloor/livingroom/temperature", "client-1", "client-3");
        assertSubscribersMatch("fastpuss/firstfloor/kitchen/temperature", "client-2");
    }

    private void assertSubscribersMatch(final @NotNull String topic, final @NotNull String @NotNull ... clientIds) {
        final Collection<String> subscribers = subscriberManager.getMatchingSubscribers(topic);
        assertEquals(clientIds.length, subscribers.size());
        assertTrue(subscribers.containsAll(Arrays.asList(clientIds)));
    }
}
