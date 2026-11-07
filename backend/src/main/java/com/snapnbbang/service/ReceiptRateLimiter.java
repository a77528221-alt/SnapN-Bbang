package com.snapnbbang.service;

import com.snapnbbang.common.RateLimitExceededException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 영수증 파싱 API 호출 횟수 제한기 (IP당 1시간 최대 20회, 메모리 기반).
 */
@Component
public class ReceiptRateLimiter {

    public static final int MAX_REQUESTS_PER_HOUR = 20;
    private static final Duration WINDOW = Duration.ofHours(1);

    private final Clock clock;
    private final ConcurrentMap<String, Deque<Instant>> requestHistory = new ConcurrentHashMap<>();

    @Autowired
    public ReceiptRateLimiter() {
        this(Clock.systemUTC());
    }

    public ReceiptRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public void checkAndRecord(String clientIp) {
        String key = (clientIp == null || clientIp.isBlank()) ? "unknown" : clientIp.trim();
        Instant now = clock.instant();
        Instant cutoff = now.minus(WINDOW);

        Deque<Instant> timestamps = requestHistory.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && !timestamps.peekFirst().isAfter(cutoff)) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= MAX_REQUESTS_PER_HOUR) {
                throw new RateLimitExceededException();
            }
            timestamps.addLast(now);
        }
    }
}

