package org.zz.mychat.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OnlineUserManager {

    private final Map<WebSocketSession, GuestUser> sessions = new ConcurrentHashMap<>();

    public void add(WebSocketSession session, GuestUser user) {
        sessions.put(session, user);
    }

    public GuestUser remove(WebSocketSession session) {
        return sessions.remove(session);
    }

    public GuestUser getUser(WebSocketSession session) {
        return sessions.get(session);
    }

    public Collection<WebSocketSession> getSessions() {
        return sessions.keySet();
    }

    /** 在线用户列表，按 anonId 去重（同一用户多标签只算一个） */
    public List<GuestUser> getOnlineUsers() {
        Map<String, GuestUser> dedup = new LinkedHashMap<>();
        for (GuestUser user : sessions.values()) {
            dedup.putIfAbsent(user.getId(), user);
        }
        return new ArrayList<>(dedup.values());
    }

    public void broadcast(String json, WebSocketSession exclude) {
        for (WebSocketSession session : sessions.keySet()) {
            if (session != exclude && session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(json));
                } catch (Exception ignored) {
                }
            }
        }
    }
}
