package ru.corelia.observability;

import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.http.HttpRequest;

/** Передаёт текущий W3C trace context через существующие JDK HTTP-клиенты. */
@Component
public class TraceContextPropagation {
    private final Tracer tracer;
    private final Propagator propagator;
    private final boolean tracingEnabled;

    public TraceContextPropagation(
            Tracer tracer,
            Propagator propagator,
            @Value("${corelia.tracing.enabled:true}") boolean tracingEnabled) {
        this.tracer = tracer;
        this.propagator = propagator;
        this.tracingEnabled = tracingEnabled;
    }

    public void inject(HttpRequest.Builder request) {
        if (!tracingEnabled) return;
        TraceContext context = tracer.currentTraceContext().context();
        if (context != null) propagator.inject(context, request, HttpRequest.Builder::header);
    }
}
