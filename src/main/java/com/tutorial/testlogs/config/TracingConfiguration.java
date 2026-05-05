package com.tutorial.testlogs.config;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TracingConfiguration {

    @Bean(destroyMethod = "close")
    SdkTracerProvider sdkTracerProvider(@Value("${spring.application.name:testlogs}") String applicationName) {
        return SdkTracerProvider.builder()
                .setSampler(Sampler.alwaysOn())
                .setResource(Resource.getDefault().toBuilder()
                        .put("service.name", applicationName)
                        .build())
                .build();
    }

    @Bean
    OpenTelemetry openTelemetry(SdkTracerProvider sdkTracerProvider) {
        return OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
    }

    @Bean
    OtelCurrentTraceContext otelCurrentTraceContext() {
        return new OtelCurrentTraceContext();
    }

    @Bean
    Tracer tracer(OpenTelemetry openTelemetry,
                  OtelCurrentTraceContext currentTraceContext,
                  @Value("${spring.application.name:testlogs}") String applicationName) {
        return new OtelTracer(
                openTelemetry.getTracer(applicationName),
                currentTraceContext,
                event -> {
                }
        );
    }
}

