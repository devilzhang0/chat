package org.zz.mychat.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.zz.mychat.domain.Message;
import org.zz.mychat.domain.User;
import org.zz.mychat.service.GuestService;
import org.zz.mychat.service.MessageService;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final int HISTORY_LIMIT = 100;
    private static final int MIN_NICKNAME_LEN = 2;
    private static final int MAX_NICKNAME_LEN = 20;
    private static final int MAX_MESSAGE_LEN = 500;
    private static final long RATE_LIMIT_WINDOW_MS = 10000;
    private static final int RATE_LIMIT_MAX = 10;
    private static final Set<String> VALID_COLORS = Set.of("indigo", "slate", "emerald", "amber", "rose", "cyan");

    private final OnlineUserManager onlineUserManager;
    private final MessageService messageService;
    private final GuestService guestService;
    private final ObjectMapper objectMapper;

    private final Map<WebSocketSession, ArrayDeque<Long>> sendTimestamps = new ConcurrentHashMap<>();

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode node = objectMapper.readTree(message.getPayload());
        String type = node.path("type").asText("");
        switch (type) {
            case "ping" -> send(session, pongPayload());
            case "leave" -> leave(session);
            case "join" -> join(session, node);
            case "message:send" -> handleSend(session, node);
            default -> { }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        leave(session);
    }

    private void join(WebSocketSession session, JsonNode node) {
        if (onlineUserManager.getUser(session) != null) {
            send(session, syncInitPayload(session));
            return;
        }

        String nickname = node.path("nickname").asText("").trim();
        int len = nickname.codePointCount(0, nickname.length());
        if (len < MIN_NICKNAME_LEN || len > MAX_NICKNAME_LEN) {
            send(session, errorPayload("昵称长度需在 2 到 20 个字符之间"));
            return;
        }
        String rawColor = node.path("avatarColor").asText("");
        String avatarColor = VALID_COLORS.contains(rawColor) ? rawColor : "indigo";

        String anonId = (String) session.getAttributes().get("anonId");
        if (anonId == null || anonId.isBlank()) {
            anonId = guestService.newAnonId();
        }
        String ip = (String) session.getAttributes().get("ip");

        User user = guestService.upsert(anonId, nickname, avatarColor, ip);
        GuestUser guest = new GuestUser(user.getId(), anonId, nickname, avatarColor, System.currentTimeMillis(), ip);
        onlineUserManager.add(session, guest);

        send(session, syncInitPayload(session));
        broadcast(messageNewPayload(systemMessage(guest, guest.getNickname() + " 加入了聊天室"), null), session);
        broadcast(presencePayload(), session);
    }

    private void handleSend(WebSocketSession session, JsonNode node) {
        GuestUser guest = onlineUserManager.getUser(session);
        if (guest == null) {
            send(session, errorPayload("会话未建立，请重新登录"));
            return;
        }

        long now = System.currentTimeMillis();
        ArrayDeque<Long> q = sendTimestamps.computeIfAbsent(session, k -> new ArrayDeque<>());
        while (!q.isEmpty() && now - q.peekFirst() > RATE_LIMIT_WINDOW_MS) {
            q.pollFirst();
        }
        if (q.size() >= RATE_LIMIT_MAX) {
            send(session, errorPayload("发送频繁，请稍后再试"));
            return;
        }

        String content = node.path("content").asText("").trim();
        if (content.isEmpty()) {
            return;
        }
        if (content.codePointCount(0, content.length()) > MAX_MESSAGE_LEN) {
            send(session, errorPayload("单条消息最多支持 " + MAX_MESSAGE_LEN + " 个字符"));
            return;
        }
        q.addLast(now);

        Message saved = messageService.save(guest.getId(), guest.getNickname(), guest.getAvatarColor(), content);
        String clientTempId = node.hasNonNull("clientTempId") ? node.path("clientTempId").asText() : null;
        broadcast(messageNewPayload(toWireMessage(saved), clientTempId), null);
    }

    private void leave(WebSocketSession session) {
        sendTimestamps.remove(session);
        GuestUser guest = onlineUserManager.remove(session);
        if (guest != null) {
            broadcast(messageNewPayload(systemMessage(guest, guest.getNickname() + " 离开了聊天室"), null), null);
            broadcast(presencePayload(), null);
        }
    }

    private Map<String, Object> syncInitPayload(WebSocketSession session) {
        GuestUser self = onlineUserManager.getUser(session);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "sync:init");
        payload.put("self", toWireUser(self));
        payload.put("messages", messageService.latest(HISTORY_LIMIT).stream().map(this::toWireMessage).toList());
        payload.put("onlineUsers", onlineUserManager.getOnlineUsers().stream().map(this::toWireUser).toList());
        return payload;
    }

    private Map<String, Object> messageNewPayload(Map<String, Object> message, String clientTempId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "message:new");
        payload.put("message", message);
        if (clientTempId != null) {
            payload.put("clientTempId", clientTempId);
        }
        return payload;
    }

    private Map<String, Object> presencePayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "presence:update");
        payload.put("onlineUsers", onlineUserManager.getOnlineUsers().stream().map(this::toWireUser).toList());
        return payload;
    }

    private Map<String, Object> pongPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "pong");
        payload.put("timestamp", System.currentTimeMillis());
        return payload;
    }

    private Map<String, Object> errorPayload(String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "error");
        payload.put("message", message);
        return payload;
    }

    private Map<String, Object> toWireUser(GuestUser u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("nickname", u.getNickname());
        m.put("avatarColor", u.getAvatarColor());
        m.put("joinedAt", u.getJoinedAt());
        return m;
    }

    private Map<String, Object> toWireMessage(Message msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(msg.getId()));
        m.put("type", msg.getType());
        m.put("senderId", msg.getSenderId());
        m.put("senderName", msg.getNickname());
        m.put("avatarColor", msg.getAvatarColor());
        m.put("content", msg.getContent());
        m.put("timestamp", msg.getCreateTime());
        return m;
    }

    private Map<String, Object> systemMessage(GuestUser guest, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", UUID.randomUUID().toString());
        m.put("type", "system");
        m.put("senderId", guest.getId());
        m.put("senderName", guest.getNickname());
        m.put("avatarColor", guest.getAvatarColor());
        m.put("content", content);
        m.put("timestamp", System.currentTimeMillis());
        return m;
    }

    private void send(WebSocketSession session, Map<String, Object> payload) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            }
        } catch (Exception ignored) {
        }
    }

    private void broadcast(Map<String, Object> payload, WebSocketSession exclude) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return;
        }
        onlineUserManager.broadcast(json, exclude);
    }
}
