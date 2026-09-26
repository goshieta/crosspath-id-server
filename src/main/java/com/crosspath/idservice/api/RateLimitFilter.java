package com.crosspath.idservice.api;

import com.crosspath.idservice.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * レート制限フィルター（per-IP のみ）。
 * per-IP: トークンバケット（上限20、毎秒10/60補充）、既定 10 req/min。
 * グローバル新規発行レート制限は RegistrationService が行う。
 * 環境変数で上書き可能: RATE_LIMIT_PER_IP_PER_MINUTE, RATE_LIMIT_PER_IP_BURST
 * ヘルスチェック（/actuator/health）とルート（/）は対象外。
 */
@Component
@Order(3)
public class RateLimitFilter extends OncePerRequestFilter {

    private final ClientIpResolver clientIpResolver;
    private final boolean enabled;

    // per-IP token bucket 設定
    private final int ipPerMinute;
    private final int ipBurst;

    // per-IP buckets
    private final Map<String, TokenBucket> ipBuckets = new ConcurrentHashMap<>();

    public RateLimitFilter(ClientIpResolver clientIpResolver,
                           RateLimitProperties properties) {
        this.clientIpResolver = clientIpResolver;
        this.enabled = properties.isEnabled();
        this.ipPerMinute = parseIntEnvOrProperty(properties, "RATE_LIMIT_PER_IP_PER_MINUTE", properties.getPerIpPerMinute());
        this.ipBurst = parseIntEnvOrProperty(properties, "RATE_LIMIT_PER_IP_BURST", properties.getPerIpBurst());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // ヘルスチェックとルートパスはレート制限の対象外
        String path = request.getRequestURI();
        if ("/actuator/health".equals(path) || "/".equals(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (enabled) {
            // per-IP rate limit
            String clientIp = clientIpResolver.resolve(request);
            TokenBucket ipBucket = ipBuckets.computeIfAbsent(clientIp, k -> new TokenBucket(ipBurst, ipBurst, ipPerMinute / 60.0));
            if (!ipBucket.tryConsume()) {
                sendRateLimited(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void sendRateLimited(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");
        response.setHeader("Retry-After", "6");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"error\":{\"code\":\"RATE_LIMITED\",\"retryable\":true}}");
    }

    private static int parseIntEnvOrProperty(RateLimitProperties properties, String envName, int configDefault) {
        // 環境変数 > プロパティ > 既定値
        String val = System.getenv(envName);
        if (val != null && !val.isBlank()) {
            try {
                return Integer.parseInt(val);
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        return configDefault;
    }

    /**
     * 簡易トークンバケット。スレッドセーフ。
     */
    static class TokenBucket {
        private final long maxTokens;
        private final double refillRatePerSecond;
        private final AtomicLong tokensNanos; // tokens in nanos (1 token = 1_000_000_000 nanos)
        private volatile long lastRefillNanos;

        TokenBucket(long maxTokens, long initialTokens, double refillRatePerSecond) {
            this.maxTokens = maxTokens;
            this.refillRatePerSecond = refillRatePerSecond;
            this.tokensNanos = new AtomicLong(initialTokens * 1_000_000_000L);
            this.lastRefillNanos = System.nanoTime();
        }

        boolean tryConsume() {
            refill();
            while (true) {
                long current = tokensNanos.get();
                if (current < 1_000_000_000L) {
                    return false;
                }
                if (tokensNanos.compareAndSet(current, current - 1_000_000_000L)) {
                    return true;
                }
            }
        }

        private void refill() {
            long now = System.nanoTime();
            long last = lastRefillNanos;
            if (now - last < 1_000_000_000L) {
                return; // 前回の補充から1秒未満
            }
            // 他のスレッドが先に更新した可能性
            if (!lastRefillNanosCas(last, now)) {
                return;
            }
            double elapsedSec = (now - last) / 1_000_000_000.0;
            long addTokens = (long) (elapsedSec * refillRatePerSecond * 1_000_000_000L);
            if (addTokens > 0) {
                tokensNanos.accumulateAndGet(addTokens, (cur, inc) -> Math.min(cur + inc, maxTokens * 1_000_000_000L));
            }
        }

        private boolean lastRefillNanosCas(long expected, long newValue) {
            if (lastRefillNanos == expected) {
                lastRefillNanos = newValue;
                return true;
            }
            return false;
        }
    }

}