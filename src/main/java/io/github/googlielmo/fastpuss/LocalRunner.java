package io.github.googlielmo.fastpuss;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;

public class LocalRunner {

    public static final int NUM_PUBS = 20;
    public static final int NUM_SUBS = 200;

    private static final Logger logger = Logger.getLogger("LocalRunner");

    public static void main(String[] args) {
        new LocalRunner().simulate();
    }

    private void simulate() {
        ExecutorService executorService = Executors.newCachedThreadPool();

        executorService.execute(() -> {
            MessageBroker messageBroker = new MessageBroker();
            messageBroker.broker();
        });


        for (int i = 0; i < NUM_SUBS; i++) {
            int port = 10001 + i;
            logger.log(INFO, "starting subscriber on port " + port);
            executorService.execute(new MessageSubscriber(port));
        }

        for (int i = 0; i < NUM_PUBS; i++) {
            int port = 11000 + i;
            logger.log(INFO, "starting publisher on port " + port);
            executorService.execute(new MessagePublisher(port));
        }
    }

}
