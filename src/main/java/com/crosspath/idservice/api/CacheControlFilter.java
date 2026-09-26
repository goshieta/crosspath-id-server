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
 * 全レスポンスに Cache-Control: no-store を付与するフィルター。
 */
@Component
@Order(4)
public class CacheControlFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        filterChain.doFilter(request, response);
        if (!response.isCommitted() && !response.containsHeader("Cache-Control")) {
            response.setHeader("Cache-Control", "no-store");
        }
    }

}