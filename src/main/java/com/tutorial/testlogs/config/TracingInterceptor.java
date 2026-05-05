package com.tutorial.testlogs.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@RequiredArgsConstructor
public class TracingInterceptor implements HandlerInterceptor {

    private static final String TRACE_ID = "traceId";
    private static final String SPAN_ID = "spanId";
    private static final String SPAN_ATTRIBUTE = TracingInterceptor.class.getName() + ".SPAN";
    private static final String SCOPE_ATTRIBUTE = TracingInterceptor.class.getName() + ".SCOPE";

    private final Tracer tracer;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Span span = tracer.nextSpan().name(spanName(request)).start();
        Tracer.SpanInScope scope = tracer.withSpan(span);

        request.setAttribute(SPAN_ATTRIBUTE, span);
        request.setAttribute(SCOPE_ATTRIBUTE, scope);

        MDC.put(TRACE_ID, span.context().traceId());
        MDC.put(SPAN_ID, span.context().spanId());

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Tracer.SpanInScope scope = (Tracer.SpanInScope) request.getAttribute(SCOPE_ATTRIBUTE);
        Span span = (Span) request.getAttribute(SPAN_ATTRIBUTE);

        try {
            if (scope != null) {
                scope.close();
            }
        } finally {
            if (span != null) {
                span.end();
            }

            MDC.remove(TRACE_ID);
            MDC.remove(SPAN_ID);
        }
    }

    private String spanName(HttpServletRequest request) {
        return request.getMethod() + " " + request.getRequestURI();
    }
}

