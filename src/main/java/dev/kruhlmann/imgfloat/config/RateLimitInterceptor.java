package dev.kruhlmann.imgfloat.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory IP-based rate limiter for the public copyright report submission endpoint.
 * Limits each IP to {@value #MAX_REQUESTS_PER_WINDOW} requests per {@value #WINDOW_MILLIS}ms window.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(RateLimitInterceptor.class);
    private static final int MAX_REQUESTS_PER_WINDOW = 5;
    private static final long WINDOW_MILLIS = 60 * 60 * 1000L; // 1 hour

    private record BucketEntry(AtomicInteger count, long windowStart) {}

    private final ConcurrentHashMap<String, BucketEntry> buckets = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull Object handler
    ) throws Exception {
        String uri = request.getRequestURI();
        if (!"POST".equalsIgnoreCase(request.getMethod()) || !uri.contains("copyright-reports")) {
            return true;
        }
        String ip = resolveClientIp(request);
        long now = System.currentTimeMillis();
        BucketEntry entry = buckets.compute(ip, (key, existing) -> {
            if (existing == null || (now - existing.windowStart()) >= WINDOW_MILLIS) {
                return new BucketEntry(new AtomicInteger(1), now);
            }
            existing.count().incrementAndGet();
            return existing;
        });
        int count = entry.count().get();
        if (count > MAX_REQUESTS_PER_WINDOW) {
            LOG.warn("Rate limit exceeded for IP {} on {}", ip, uri);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too many requests. Please try again later.\"}");
            return false;
        }
        return true;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
