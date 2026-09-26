package com.crosspath.idservice.config;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * グローバル新規発行レート制限（トークンバケット）。
 * SPEC §4: 新規発行 10 件/秒。再送（既存 request_id の再送）では消費しない。
 * RegistrationService が新規発行直前に tryConsume() を呼ぶ。
 * app.rate-limit.enabled=false で無効化。
 */
@Component
public class GlobalRateLimiter {

    private final TokenBucket globalBucket;
    private final boolean enabled;

    public GlobalRateLimiter(RateLimitProperties properties) {
        this.enabled = properties.isEnabled();
        int perSecond = properties.getNewRegistrationsPerSecond();
        this.globalBucket = new TokenBucket(perSecond, perSecond, perSecond);
    }

    /**
     * 1 トークンを消費できれば true、できなければ false（レート制限超過）。
     */
    public boolean tryConsume() {
        if (!enabled) {
            return true;
        }
        return globalBucket.tryConsume();
    }

    /**
     * 簡易トークンバケット。スレッドセーフ。
     */
    static class TokenBucket {
        private final long maxTokens;
        private final double refillRatePerSecond;
        private final AtomicLong tokensNanos;
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
                return;
            }
            // 他のスレッドが先に更新した可能性 → CAS で排他
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