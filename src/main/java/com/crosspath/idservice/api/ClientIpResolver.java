package com.crosspath.idservice.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * クライアントIP解決: X-Forwarded-For の先頭値、無ければ remote address。
 */
@Component
public class ClientIpResolver {

    public String resolve(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // 先頭のIPアドレス（カンマ区切りの最初）
            String[] parts = xff.split(",");
            return parts[0].trim();
        }
        return request.getRemoteAddr();
    }

}