package ru.corelia.observability;

import io.micrometer.common.KeyValue;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/** Общие низкокардинальные метрики и spans прикладных операций Corelia. */
@Component
public class CoreliaObservability {
    private final MeterRegistry meters;
    private final ObservationRegistry observations;
    private final boolean tracingEnabled;

    public CoreliaObservability(
            MeterRegistry meters,
            ObservationRegistry observations,
            @Value("${corelia.tracing.enabled:true}") boolean tracingEnabled) {
        this.meters = meters;
        this.observations = observations;
        this.tracingEnabled = tracingEnabled;
    }

    public <T> T observe(String operation, Supplier<T> action) {
        if (!tracingEnabled) return action.get();
        Observation observation = Observation.createNotStarted(operation, observations)
                .lowCardinalityKeyValue(KeyValue.of("corelia.operation", operation));
        observation.start();
        try (Observation.Scope ignored = observation.openScope()) {
            return action.get();
        } catch (RuntimeException error) {
            observation.error(error);
            throw error;
        } finally {
            observation.stop();
        }
    }

    public void documentCreated(String documentType) {
        increment("corelia.documents.created", "document_type", documentType);
    }

    public void attachmentUploaded(String operation) {
        increment("corelia.attachment.uploads", "operation", operation, "outcome", "success");
    }

    public void attachmentUploadFailed(String operation) {
        increment("corelia.attachment.upload.failures", "operation", operation, "outcome", "error");
    }

    public void workflowStarted() {
        increment("corelia.workflow.starts", "outcome", "success");
    }

    public void workflowStartFailed() {
        increment("corelia.workflow.start.failures", "outcome", "error");
    }

    public void externalRequest(String client, String operation, String outcome) {
        increment(
                "corelia.external.http.requests",
                "client",
                client,
                "operation",
                operation,
                "outcome",
                outcome);
    }

    private void increment(String name, String... tags) {
        Counter.Builder counter = Counter.builder(name);
        for (int index = 0; index < tags.length; index += 2)
            counter.tag(tags[index], tags[index + 1]);
        counter.register(meters).increment();
    }
}
