package io.github.googlielmo.fastpuss;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Logger;

public class ThreadSafeSubscriberManager {

    private static final Logger logger = Logger.getLogger("ThreadSafeSubscriberManager");

    final Map<String, Collection<String>> topicMap = new ConcurrentHashMap<>();

    public void addSubscription(final @NotNull String topicFilter, final @NotNull String clientId) {
        final Collection<String> oneElement = new ConcurrentLinkedDeque<>(List.of(clientId));
        topicMap.merge(topicFilter, oneElement,
                (list, singleton) -> {
                    list.add(singleton.iterator().next());
                    return list;
                });
    }

    @NotNull
    public Collection<String> getMatchingSubscribers(final @NotNull String topic) {
        Collection<String> subs = topicMap.getOrDefault(topic, Collections.emptyList());
        if (subs.isEmpty()) {
            logger.info("no subscription for " + topic);
        }
        else {
            logger.info(String.format("msg for %s, subscriptions: %s", topic, subs));
        }
        return subs;
    }

    protected void marshal(Writer w) throws IOException {
        for (Map.Entry<String, Collection<String>> entry : topicMap.entrySet()) {
            String key = entry.getKey();
            Collection<String> collection = entry.getValue();

            w.write(key);
            w.write("\n");

            for (String id : collection) {
                w.write(id);
                w.write("\n");
            }

            w.write("\n");
        }
    }

    protected void unmarshal(BufferedReader r) throws IOException {
        String s, id = null;
        boolean readId = true;
        while ((s = r.readLine()) != null) {
            if (s.isEmpty()) {
                readId = true;
            } else {
                if (readId) {
                    id = s;
                    readId = false;
                } else {
                    addSubscription(s, id);
                }
            }
        }
    }
}
