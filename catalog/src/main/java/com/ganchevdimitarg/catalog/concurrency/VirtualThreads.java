package com.ganchevdimitarg.catalog.concurrency;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * Virtual-thread fan-out helpers for blocking I/O.
 *
 * <p>Uses a virtual-thread-per-task executor (stable since JDK 21) rather than
 * {@code StructuredTaskScope}, which is still a preview API in JDK 25 and would require
 * {@code --enable-preview}.
 */
public final class VirtualThreads {

    private VirtualThreads() {
    }

    /**
     * Apply {@code task} to every input concurrently, one virtual thread per input, and return the
     * results in input order. Fails fast: the first task failure propagates (unchecked exceptions
     * unchanged, so domain exceptions such as {@code NotFoundException} are preserved).
     */
    public static <T, R> List<R> mapParallel(List<T> inputs, Function<? super T, ? extends R> task) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<R>> futures = inputs.stream()
                    .<Future<R>>map(input -> executor.submit(() -> task.apply(input)))
                    .toList();
            List<R> results = new ArrayList<>(futures.size());
            for (Future<R> future : futures) {
                results.add(join(future));
            }
            return results;
        }
    }

    private static <R> R join(Future<R> future) {
        try {
            return future.get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException("Parallel task failed", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while awaiting parallel tasks", e);
        }
    }
}
