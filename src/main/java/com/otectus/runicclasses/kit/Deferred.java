package com.otectus.runicclasses.kit;

import com.otectus.runicclasses.RunicClasses;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Work that must happen after the current hit has finished: an effect granted because a hit took the
 * player below a threshold has to land after that hit spends absorption and health, or the new
 * Absorption would soak the very hit that triggered it. Drained at the end of the server tick.
 */
public final class Deferred {

    private static final Queue<Runnable> QUEUE = new ConcurrentLinkedQueue<>();
    private static final int MAX_PER_TICK = 4096;

    private Deferred() {}

    public static void later(Runnable task) {
        QUEUE.add(task);
    }

    public static void drain() {
        Runnable task;
        int ran = 0;
        while (ran++ < MAX_PER_TICK && (task = QUEUE.poll()) != null) {
            try {
                task.run();
            } catch (RuntimeException e) {
                RunicClasses.LOGGER.error("A deferred class effect failed", e);
            }
        }
    }

    public static void clear() {
        QUEUE.clear();
    }
}
