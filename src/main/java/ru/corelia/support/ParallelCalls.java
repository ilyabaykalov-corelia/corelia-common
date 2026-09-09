package ru.corelia.support;

import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Component;

import ru.corelia.http.ApiException;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/** Выполняет независимые запросы на виртуальных потоках, сохраняя порядок результатов. */
@Component
public class ParallelCalls {
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public <T, R> List<R> map(List<T> inputs, Function<T, R> operation) {
        List<Future<R>> futures =
                inputs.stream()
                        .map(input -> executor.submit(() -> operation.apply(input)))
                        .toList();
        List<R> results = new ArrayList<>(inputs.size());
        try {
            for (Future<R> future : futures) results.add(future.get());
            return results;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ApiException(503, "Параллельные запросы к платформе прерваны");
        } catch (ExecutionException error) {
            if (error.getCause() instanceof RuntimeException cause) throw cause;
            throw new IllegalStateException(
                    "Ошибка параллельного вызова платформы", error.getCause());
        } finally {
            futures.stream()
                    .filter(future -> !future.isDone())
                    .forEach(future -> future.cancel(true));
        }
    }

    @PreDestroy
    public void close() {
        executor.shutdownNow();
    }
}
