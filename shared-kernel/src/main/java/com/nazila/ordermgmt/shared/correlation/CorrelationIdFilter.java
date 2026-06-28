package com.nazila.ordermgmt.shared.correlation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Ensures every request carries a correlation id: reuses the inbound
 * {@code X-Correlation-Id} header if present, otherwise generates one.
 * The id is pushed into MDC (so every log line during the request includes
 * it), echoed back on the response, and exposed to handlers via
 * {@link CorrelationContext} so it can be stamped onto outgoing domain events.
 *
 * <p>Registered explicitly (with {@code HIGHEST_PRECEDENCE}) by
 * {@code SharedKernelAutoConfiguration} rather than discovered via
 * {@code @Component}, because consuming services don't component-scan the
 * {@code shared-kernel} package.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(CorrelationContext.HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(CorrelationContext.MDC_KEY, correlationId);
        response.setHeader(CorrelationContext.HEADER_NAME, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CorrelationContext.MDC_KEY);
        }
    }
}
