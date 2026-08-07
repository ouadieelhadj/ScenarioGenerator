package com.staging.sg.switchlab.bff.config;

import com.staging.sg.switchlab.bff.service.SwitchLabTraceService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SwitchLabCorrelationFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Correlation-ID";
    private final SwitchLabTraceService traces;

    public SwitchLabCorrelationFilter(SwitchLabTraceService traces) {
        this.traces = traces;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER);
        if (correlationId == null || correlationId.isBlank()) correlationId = UUID.randomUUID().toString();
        long started = System.nanoTime();
        response.setHeader(HEADER, correlationId);
        request.setAttribute(HEADER, correlationId);
        MDC.put("correlationId", correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - started) / 1_000_000;
            String level = response.getStatus() >= 500 ? "ERROR" : response.getStatus() >= 400 ? "WARN" : "INFO";
            traces.record(correlationId, level, "sg-switchlab-bff",
                    request.getMethod() + " " + request.getRequestURI() + " -> " + response.getStatus()
                            + " (" + durationMs + " ms)");
            MDC.remove("correlationId");
        }
    }
}
