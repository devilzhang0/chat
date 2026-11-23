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
import org.zz.mychat.domain.Room;
import org.zz.mychat.domain.User;
import org.zz.mychat.service.GuestService;
import org.zz.mychat.service.MessageService;
import org.zz.mychat.service.RoomService;
import org.zz.mychat.websocket.OnlineUserManager.RoomSession;

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
    private final RoomService roomService;
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
        String roomCode = node.path("roomCode").asText("").trim();
        if (roomCode.isEmpty()) {
            send(session, errorPayload("ROOM_NOT_FOUND", "请输入房间号"));
            return;
        }

        RoomSession existing = onlineUserManager.getSession(session);
        if (existing != null) {
            if (existing.roomCode().equals(roomCode)) {
                send(session, syncInitPayload(existing));
                return;
            }
            leave(session);
        }

        String nickname = node.path("nickname").asText("").trim();
        int len = nickname.codePointCount(0, nickname.length());
        if (len < MIN_NICKNAME_LEN || len > MAX_NICKNAME_LEN) {
            send(session, errorPayload("INVALID_NICKNAME", "昵称长度需在 2 到 20 个字符之间"));
            return;
        }
        String rawColor = node.path("avatarColor").asText("");
        String avatarColor = VALID_COLORS.contains(rawColor) ? rawColor : "indigo";

        Room room = roomService.findByCode(roomCode);
        if (room == null) {
            send(session, errorPayload("ROOM_NOT_FOUND", "房间未创建"));
            return;
        }
        if (room.isPrivateRoom()) {
            String password = node.path("password").asText("");
            if (!roomService.verifyPassword(room, password)) {
                send(session, errorPayload("INVALID_PASSWORD", "房间密码错误"));
                return;
            }
        }

        String anonId = (String) session.getAttributes().get("anonId");
        if (anonId == null || anonId.isBlank()) {
            anonId = guestService.newAnonId();
        }
        String ip = (String) session.getAttributes().get("ip");

        User user = guestService.upsert(anonId, nickname, avatarColor, ip);
        GuestUser guest = new GuestUser(user.getId(), anonId, nickname, avatarColor, System.currentTimeMillis(), ip);

        boolean alreadyInRoom = onlineUserManager.hasSessionInRoom(room.getId(), anonId);
        RoomSession roomSession = new RoomSession(guest, room.getId(), room.getCode(), room.getName(), room.isPrivateRoom());
        onlineUserManager.join(session, roomSession);

        send(session, syncInitPayload(roomSession));
        if (!alreadyInRoom) {
            broadcastToRoom(
                    room.getId(),
                    messageNewPayload(room.getCode(), systemMessage(guest, guest.getNickname() + " 加入了聊天室"), null),
                    session
            );
        }
        broadcastToRoom(room.getId(), presencePayload(room.getId(), room.getCode()), session);
    }

    private void handleSend(WebSocketSession session, JsonNode node) {
        RoomSession roomSession = onlineUserManager.getSession(session);
        if (roomSession == null) {
            send(session, errorPayload("NOT_JOINED", "会话未建立，请重新进入房间"));
            return;
        }
        GuestUser guest = roomSession.user();

        long now = System.currentTimeMillis();
        ArrayDeque<Long> q = sendTimestamps.computeIfAbsent(session, k -> new ArrayDeque<>());
        while (!q.isEmpty() && now - q.peekFirst() > RATE_LIMIT_WINDOW_MS) {
            q.pollFirst();
        }
        if (q.size() >= RATE_LIMIT_MAX) {
            send(session, errorPayload("RATE_LIMITED", "发送频繁，请稍后再试"));
            return;
        }

        String content = node.path("content").asText("").trim();
        if (content.isEmpty()) {
            return;
        }
        if (content.codePointCount(0, content.length()) > MAX_MESSAGE_LEN) {
            send(session, errorPayload("MESSAGE_TOO_LONG", "单条消息最多支持 " + MAX_MESSAGE_LEN + " 个字符"));
            return;
        }
        q.addLast(now);

        Message saved = messageService.save(
                roomSession.roomId(),
                guest.getId(),
                guest.getNickname(),
                guest.getAvatarColor(),
                content
        );
        String clientTempId = node.hasNonNull("clientTempId") ? node.path("clientTempId").asText() : null;
        broadcastToRoom(
                roomSession.roomId(),
                messageNewPayload(roomSession.roomCode(), toWireMessage(saved), clientTempId),
                null
        );
    }

    private void leave(WebSocketSession session) {
        sendTimestamps.remove(session);
        RoomSession removed = onlineUserManager.remove(session);
        if (removed != null) {
            boolean stillInRoom = onlineUserManager.hasSessionInRoom(removed.roomId(), removed.user().getId());
            if (!stillInRoom) {
                broadcastToRoom(
                        removed.roomId(),
                        messageNewPayload(
                                removed.roomCode(),
                                systemMessage(removed.user(), removed.user().getNickname() + " 离开了聊天室"),
                                null
                        ),
                        null
                );
            }
            broadcastToRoom(removed.roomId(), presencePayload(removed.roomId(), removed.roomCode()), null);
        }
    }

    private Map<String, Object> syncInitPayload(RoomSession roomSession) {
        Map<String, Object> room = new LinkedHashMap<>();
        room.put("code", roomSession.roomCode());
        room.put("name", roomSession.roomName());
        room.put("isPrivate", roomSession.isPrivate());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "sync:init");
        payload.put("room", room);
        payload.put("self", toWireUser(roomSession.user()));
        payload.put("messages", messageService.latest(roomSession.roomId(), HISTORY_LIMIT)
                .stream()
                .map(this::toWireMessage)
                .toList());
        payload.put("onlineUsers", onlineUserManager.getOnlineUsers(roomSession.roomId())
                .stream()
                .map(this::toWireUser)
                .toList());
        return payload;
    }

    private Map<String, Object> messageNewPayload(String roomCode, Map<String, Object> message, String clientTempId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "message:new");
        payload.put("roomCode", roomCode);
        payload.put("message", message);
        if (clientTempId != null) {
            payload.put("clientTempId", clientTempId);
        }
        return payload;
    }

    private Map<String, Object> presencePayload(Long roomId, String roomCode) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "presence:update");
        payload.put("roomCode", roomCode);
        payload.put("onlineUsers", onlineUserManager.getOnlineUsers(roomId)
                .stream()
                .map(this::toWireUser)
                .toList());
        return payload;
    }

    private Map<String, Object> pongPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "pong");
        payload.put("timestamp", System.currentTimeMillis());
        return payload;
    }

    private Map<String, Object> errorPayload(String code, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "error");
        if (code != null) {
            payload.put("code", code);
        }
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
            onlineUserManager.sendText(session, objectMapper.writeValueAsString(payload));
        } catch (Exception ignored) {
        }
    }

    private void broadcastToRoom(Long roomId, Map<String, Object> payload, WebSocketSession exclude) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return;
        }
        onlineUserManager.broadcastToRoom(roomId, json, exclude);
    }
}
