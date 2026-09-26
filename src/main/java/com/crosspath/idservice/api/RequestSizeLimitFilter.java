package com.crosspath.idservice.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * リクエストボディのサイズ上限を超えた場合に 400 INVALID_REQUEST を返すフィルター。
 */
@Component
@Order(2)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    static final int MAX_BODY_SIZE = 1024;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        int contentLength = request.getContentLength();
        if (contentLength > MAX_BODY_SIZE) {
            response.setStatus(400);
            response.setContentType("application/json");
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write("{\"error\":{\"code\":\"INVALID_REQUEST\",\"retryable\":false}}");
            return;
        }
        filterChain.doFilter(request, response);
    }

}