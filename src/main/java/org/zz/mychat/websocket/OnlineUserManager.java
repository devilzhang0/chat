package org.zz.mychat.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class OnlineUserManager {

    private static final int MAX_CONNECTIONS_PER_IP = 20;

    public record RoomSession(
            GuestUser user,
            Long roomId,
            String roomCode,
            String roomName,
            boolean isPrivate
    ) {
    }

    private final Map<WebSocketSession, RoomSession> sessions = new ConcurrentHashMap<>();
    private final Map<Long, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, String> connectedIps = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> ipConnectionCounts = new ConcurrentHashMap<>();

    /** 注册新连接并校验单 IP 并发连接数上限 */
    public boolean registerConnection(WebSocketSession session, String ip) {
        String safeIp = (ip == null || ip.isBlank()) ? "unknown" : ip.trim();
        AtomicBoolean admitted = new AtomicBoolean(false);
        ipConnectionCounts.compute(safeIp, (k, counter) -> {
            if (counter == null) {
                admitted.set(true);
                return new AtomicInteger(1);
            }
            if (counter.get() >= MAX_CONNECTIONS_PER_IP) {
                return counter;
            }
            counter.incrementAndGet();
            admitted.set(true);
            return counter;
        });
        if (admitted.get()) {
            connectedIps.put(session, safeIp);
        }
        return admitted.get();
    }

    /** 释放连接对应的 IP 并发计数（幂等） */
    public void unregisterConnection(WebSocketSession session) {
        String ip = connectedIps.remove(session);
        if (ip == null) {
            return;
        }
        ipConnectionCounts.computeIfPresent(ip, (k, counter) ->
                counter.decrementAndGet() <= 0 ? null : counter
        );
    }

    public void join(WebSocketSession session, RoomSession roomSession) {
        remove(session);
        sessions.put(session, roomSession);
        roomSessions.compute(roomSession.roomId(), (id, set) -> {
            Set<WebSocketSession> target = (set != null) ? set : ConcurrentHashMap.newKeySet();
            target.add(session);
            return target;
        });
    }

    public RoomSession remove(WebSocketSession session) {
        RoomSession removed = sessions.remove(session);
        if (removed != null) {
            roomSessions.computeIfPresent(removed.roomId(), (id, set) -> {
                set.remove(session);
                return set.isEmpty() ? null : set;
            });
        }
        return removed;
    }

    public RoomSession getSession(WebSocketSession session) {
        return sessions.get(session);
    }

    /** 指定房间的在线用户列表，按 anonId 去重（同一用户同房多标签只算一个） */
    public List<GuestUser> getOnlineUsers(Long roomId) {
        Set<WebSocketSession> set = roomSessions.get(roomId);
        if (set == null || set.isEmpty()) {
            return List.of();
        }
        Map<String, GuestUser> dedup = new LinkedHashMap<>();
        for (WebSocketSession s : set) {
            RoomSession rs = sessions.get(s);
            if (rs != null) {
                dedup.putIfAbsent(rs.user().getId(), rs.user());
            }
        }
        return new ArrayList<>(dedup.values());
    }

    public int getOnlineCount(Long roomId) {
        return getOnlineUsers(roomId).size();
    }

    public boolean hasSessionInRoom(Long roomId, String anonId) {
        Set<WebSocketSession> set = roomSessions.get(roomId);
        if (set == null || set.isEmpty() || anonId == null) {
            return false;
        }
        for (WebSocketSession s : set) {
            RoomSession rs = sessions.get(s);
            if (rs != null && anonId.equals(rs.user().getId())) {
                return true;
            }
        }
        return false;
    }

    public void sendText(WebSocketSession session, String json) {
        if (session == null || !session.isOpen()) {
            return;
        }
        synchronized (session) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(json));
                } catch (Exception ignored) {
                }
            }
        }
    }

    public void broadcastToRoom(Long roomId, String json, WebSocketSession exclude) {
        Set<WebSocketSession> set = roomSessions.get(roomId);
        if (set == null || set.isEmpty()) {
            return;
        }
        for (WebSocketSession session : set) {
            if (session != exclude) {
                sendText(session, json);
            }
        }
    }
}
