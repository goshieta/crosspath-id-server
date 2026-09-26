package com.crosspath.idservice.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 各リクエストに traceId を割り当て、MDC とレスポンスヘッダ X-Trace-Id にセットする。
 */
@Component
@Order(1)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final ThreadLocal<SecureRandom> RANDOM = ThreadLocal.withInitial(SecureRandom::new);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        byte[] bytes = new byte[8];
        RANDOM.get().nextBytes(bytes);
        String traceId = HexFormat.of().formatHex(bytes);
        MDC.put("traceId", traceId);
        response.setHeader("X-Trace-Id", traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("traceId");
        }
    }

}