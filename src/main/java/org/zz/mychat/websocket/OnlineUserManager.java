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

@Component
public class OnlineUserManager {

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
