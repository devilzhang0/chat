package org.zz.mychat.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.function.LongSupplier;

/**
 * 基于 Guava 有界过期缓存的内存防刷与安全限流服务：
 * - 创建房间限流（按 IP 与按访客双重滑动窗口）
 * - 查询房间号限流（防枚举 6 位房间号）
 * - 私密房密码防暴力破解（连续失败锁定 + 冷却倒计时）
 * - WebSocket 入房与发消息限流（跨多标签页统一按 anonId + IP 限流）
 */
@Service
public class RateLimitService {

    // 创建房间：单访客 3次/分钟，单 IP 6次/分钟
    private static final long CREATE_ROOM_WINDOW_MS = 60_000L;
    private static final int CREATE_ROOM_ANON_MAX = 3;
    private static final int CREATE_ROOM_IP_MAX = 6;

    // 查询房间（防枚举）：单 IP 30次/分钟
    private static final long LOOKUP_WINDOW_MS = 60_000L;
    private static final int LOOKUP_IP_MAX = 30;

    // WS 入房尝试：单 IP 20次/分钟
    private static final long WS_JOIN_WINDOW_MS = 60_000L;
    private static final int WS_JOIN_IP_MAX = 20;

    // 发消息：单访客 10条/10秒（跨标签页生效），单 IP 30条/10秒（防伪造多 Cookie 刷屏）
    private static final long SEND_MSG_WINDOW_MS = 10_000L;
    private static final int SEND_MSG_ANON_MAX = 10;
    private static final int SEND_MSG_IP_MAX = 30;

    // 向上拉取历史消息：单访客 20次/10秒
    private static final long HISTORY_LOAD_WINDOW_MS = 10_000L;
    private static final int HISTORY_LOAD_ANON_MAX = 20;

    // 私密房猜密码保护：10分钟内连错 5 次锁定 120 秒
    private static final long PWD_FAIL_WINDOW_MS = 10 * 60_000L;
    private static final int PWD_FAIL_MAX = 5;
    private static final long PWD_LOCK_DURATION_MS = 120_000L;

    private final LongSupplier clock;

    private final Cache<String, ArrayDeque<Long>> windowCache = CacheBuilder.newBuilder()
            .maximumSize(50_000)
            .expireAfterAccess(Duration.ofMinutes(15))
            .build();

    private final Cache<String, PasswordAttemptState> passwordCache = CacheBuilder.newBuilder()
            .maximumSize(50_000)
            .expireAfterAccess(Duration.ofMinutes(20))
            .build();

    public RateLimitService() {
        this(System::currentTimeMillis);
    }

    RateLimitService(LongSupplier clock) {
        this.clock = clock;
    }

    public record RateLimitResult(boolean allowed, long retryAfterSeconds, String message) {
        public static RateLimitResult ok() {
            return new RateLimitResult(true, 0, null);
        }

        public static RateLimitResult blocked(long retryAfterSeconds, String message) {
            return new RateLimitResult(false, Math.max(1, retryAfterSeconds), message);
        }
    }

    private static final class PasswordAttemptState {
        final ArrayDeque<Long> failures = new ArrayDeque<>();
        long lockedUntilMs = 0L;
    }

    /** 校验并记录创建房间频次 */
    public RateLimitResult checkCreateRoom(String ip, String anonId) {
        String safeIp = normalizeKey(ip);
        long ipRetry = peekWindowRetry("create:ip:" + safeIp, CREATE_ROOM_WINDOW_MS, CREATE_ROOM_IP_MAX);
        if (ipRetry > 0) {
            return RateLimitResult.blocked(ipRetry, "创建房间过于频繁，请 " + ipRetry + " 秒后再试");
        }
        String safeAnon = normalizeKey(anonId);
        if (!"unknown".equals(safeAnon)) {
            long anonRetry = peekWindowRetry("create:anon:" + safeAnon, CREATE_ROOM_WINDOW_MS, CREATE_ROOM_ANON_MAX);
            if (anonRetry > 0) {
                return RateLimitResult.blocked(anonRetry, "创建房间过于频繁，请 " + anonRetry + " 秒后再试");
            }
        }

        // 双重预检都通过后再同时扣减配额
        recordWindow("create:ip:" + safeIp, CREATE_ROOM_WINDOW_MS);
        if (!"unknown".equals(safeAnon)) {
            recordWindow("create:anon:" + safeAnon, CREATE_ROOM_WINDOW_MS);
        }
        return RateLimitResult.ok();
    }

    /** 校验并记录查询房间号频次（防枚举） */
    public RateLimitResult checkRoomLookup(String ip) {
        long retry = tryAcquireWindow("lookup:ip:" + normalizeKey(ip), LOOKUP_WINDOW_MS, LOOKUP_IP_MAX);
        if (retry > 0) {
            return RateLimitResult.blocked(retry, "查询房间过于频繁，请 " + retry + " 秒后再试");
        }
        return RateLimitResult.ok();
    }

    /** 校验并记录 WS 入房尝试频次 */
    public RateLimitResult checkWsJoin(String ip) {
        long retry = tryAcquireWindow("wsjoin:ip:" + normalizeKey(ip), WS_JOIN_WINDOW_MS, WS_JOIN_IP_MAX);
        if (retry > 0) {
            return RateLimitResult.blocked(retry, "操作过于频繁，请 " + retry + " 秒后再试");
        }
        return RateLimitResult.ok();
    }

    /** 校验并记录发消息频次（跨多标签页统一按 anonId 限流，并叠加 IP 上限） */
    public RateLimitResult checkSendMessage(String anonId, String ip) {
        String safeAnon = normalizeKey(anonId);
        String safeIp = normalizeKey(ip);

        long anonRetry = peekWindowRetry("msg:anon:" + safeAnon, SEND_MSG_WINDOW_MS, SEND_MSG_ANON_MAX);
        if (anonRetry > 0) {
            return RateLimitResult.blocked(anonRetry, "发送频繁，请稍后再试");
        }
        long ipRetry = peekWindowRetry("msg:ip:" + safeIp, SEND_MSG_WINDOW_MS, SEND_MSG_IP_MAX);
        if (ipRetry > 0) {
            return RateLimitResult.blocked(ipRetry, "当前网络发送过于频繁，请稍后再试");
        }

        recordWindow("msg:anon:" + safeAnon, SEND_MSG_WINDOW_MS);
        recordWindow("msg:ip:" + safeIp, SEND_MSG_WINDOW_MS);
        return RateLimitResult.ok();
    }

    /** 校验并记录历史消息向上分页频次 */
    public RateLimitResult checkHistoryLoad(String anonId) {
        long retry = tryAcquireWindow("history:anon:" + normalizeKey(anonId), HISTORY_LOAD_WINDOW_MS, HISTORY_LOAD_ANON_MAX);
        if (retry > 0) {
            return RateLimitResult.blocked(retry, "加载历史消息过于频繁，请稍后再试");
        }
        return RateLimitResult.ok();
    }

    /** 检查是否处于私密房密码错误锁定中 */
    public RateLimitResult checkPasswordLock(String roomCode, String ip, String anonId) {
        long now = clock.getAsLong();
        long lockSec = Math.max(
                remainingLockSeconds(pwdKey("ip", roomCode, normalizeKey(ip)), now),
                remainingLockSeconds(pwdKey("anon", roomCode, anonId), now)
        );
        if (lockSec > 0) {
            return RateLimitResult.blocked(lockSec, "密码错误次数过多，请 " + lockSec + " 秒后再试");
        }
        return RateLimitResult.ok();
    }

    /** 记录一次私密房密码错误；达到阈值立即锁定并返回锁定提示，否则返回剩余尝试次数提示。 */
    public RateLimitResult recordPasswordFailure(String roomCode, String ip, String anonId) {
        long now = clock.getAsLong();
        FailureOutcome ipOutcome = addFailure(pwdKey("ip", roomCode, normalizeKey(ip)), now);
        FailureOutcome anonOutcome = isPresent(anonId)
                ? addFailure(pwdKey("anon", roomCode, anonId), now)
                : new FailureOutcome(0, PWD_FAIL_MAX);

        long lockSec = Math.max(ipOutcome.lockedSeconds(), anonOutcome.lockedSeconds());
        if (lockSec > 0) {
            return RateLimitResult.blocked(lockSec, "密码错误次数过多，请 " + lockSec + " 秒后再试");
        }
        int remaining = Math.min(ipOutcome.remainingAttempts(), anonOutcome.remainingAttempts());
        String msg = remaining <= 2
                ? "房间密码错误（还可尝试 " + remaining + " 次）"
                : "房间密码错误";
        return new RateLimitResult(true, 0, msg);
    }

    /** 密码验证成功后清零该房间的失败计数 */
    public void clearPasswordFailure(String roomCode, String ip, String anonId) {
        passwordCache.invalidate(pwdKey("ip", roomCode, normalizeKey(ip)));
        if (isPresent(anonId)) {
            passwordCache.invalidate(pwdKey("anon", roomCode, anonId));
        }
    }

    private record FailureOutcome(long lockedSeconds, int remainingAttempts) {
    }

    private FailureOutcome addFailure(String key, long now) {
        if (key == null) {
            return new FailureOutcome(0, PWD_FAIL_MAX);
        }
        PasswordAttemptState state = getOrCreatePasswordState(key);
        synchronized (state) {
            if (state.lockedUntilMs > now) {
                return new FailureOutcome(ceilSeconds(state.lockedUntilMs - now), 0);
            }
            while (!state.failures.isEmpty() && now - state.failures.peekFirst() > PWD_FAIL_WINDOW_MS) {
                state.failures.pollFirst();
            }
            state.failures.addLast(now);
            if (state.failures.size() >= PWD_FAIL_MAX) {
                state.lockedUntilMs = now + PWD_LOCK_DURATION_MS;
                state.failures.clear();
                return new FailureOutcome(ceilSeconds(PWD_LOCK_DURATION_MS), 0);
            }
            return new FailureOutcome(0, Math.max(1, PWD_FAIL_MAX - state.failures.size()));
        }
    }

    private long remainingLockSeconds(String key, long now) {
        if (key == null) {
            return 0L;
        }
        PasswordAttemptState state = passwordCache.getIfPresent(key);
        if (state == null) {
            return 0L;
        }
        synchronized (state) {
            if (state.lockedUntilMs > now) {
                return ceilSeconds(state.lockedUntilMs - now);
            }
            return 0L;
        }
    }

    private long tryAcquireWindow(String key, long windowMs, int maxCount) {
        long now = clock.getAsLong();
        ArrayDeque<Long> queue = getOrCreateWindow(key);
        synchronized (queue) {
            prune(queue, now, windowMs);
            if (queue.size() >= maxCount) {
                long oldest = queue.peekFirst();
                return ceilSeconds(windowMs - (now - oldest));
            }
            queue.addLast(now);
            return 0L;
        }
    }

    private long peekWindowRetry(String key, long windowMs, int maxCount) {
        long now = clock.getAsLong();
        ArrayDeque<Long> queue = windowCache.getIfPresent(key);
        if (queue == null) {
            return 0L;
        }
        synchronized (queue) {
            prune(queue, now, windowMs);
            if (queue.size() >= maxCount) {
                long oldest = queue.peekFirst();
                return ceilSeconds(windowMs - (now - oldest));
            }
            return 0L;
        }
    }

    private void recordWindow(String key, long windowMs) {
        long now = clock.getAsLong();
        ArrayDeque<Long> queue = getOrCreateWindow(key);
        synchronized (queue) {
            prune(queue, now, windowMs);
            queue.addLast(now);
        }
    }

    private void prune(ArrayDeque<Long> queue, long now, long windowMs) {
        while (!queue.isEmpty() && now - queue.peekFirst() >= windowMs) {
            queue.pollFirst();
        }
    }

    private ArrayDeque<Long> getOrCreateWindow(String key) {
        try {
            return windowCache.get(key, ArrayDeque::new);
        } catch (Exception e) {
            return new ArrayDeque<>();
        }
    }

    private PasswordAttemptState getOrCreatePasswordState(String key) {
        try {
            return passwordCache.get(key, PasswordAttemptState::new);
        } catch (Exception e) {
            return new PasswordAttemptState();
        }
    }

    private String pwdKey(String dimension, String roomCode, String actor) {
        if (!isPresent(actor)) {
            return null;
        }
        return "pwd:" + dimension + ":" + normalizeKey(roomCode) + ":" + actor.trim();
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizeKey(String value) {
        return (value == null || value.isBlank()) ? "unknown" : value.trim();
    }

    private long ceilSeconds(long ms) {
        return Math.max(1L, (ms + 999L) / 1000L);
    }
}
