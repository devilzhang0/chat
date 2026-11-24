package org.zz.mychat.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
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
import org.zz.mychat.service.RateLimitService;
import org.zz.mychat.service.RateLimitService.RateLimitResult;
import org.zz.mychat.service.RoomService;
import org.zz.mychat.websocket.OnlineUserManager.RoomSession;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final int HISTORY_LIMIT = 100;
    private static final int MIN_NICKNAME_LEN = 2;
    private static final int MAX_NICKNAME_LEN = 20;
    private static final int MAX_MESSAGE_LEN = 500;
    private static final int MAX_PAYLOAD_BYTES = 4096;
    private static final int TRANSPORT_BUFFER_BYTES = 8192;
    private static final long HEARTBEAT_TIMEOUT_MS = 90_000L;
    private static final long UNJOINED_TIMEOUT_MS = 30_000L;
    private static final long ZOMBIE_SCAN_INTERVAL_MS = 30_000L;
    private static final CloseStatus HEARTBEAT_TIMEOUT_CLOSE = new CloseStatus(4000, "Heartbeat timeout");
    private static final Set<String> VALID_COLORS = Set.of("indigo", "slate", "emerald", "amber", "rose", "cyan");

    private final OnlineUserManager onlineUserManager;
    private final MessageService messageService;
    private final GuestService guestService;
    private final RoomService roomService;
    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        session.setTextMessageSizeLimit(TRANSPORT_BUFFER_BYTES);
        String ip = (String) session.getAttributes().get("ip");
        if (!onlineUserManager.registerConnection(session, ip)) {
            send(session, errorPayload("CONNECTION_LIMIT", "当前网络并发连接数过多，请稍后再试"));
            session.close(CloseStatus.POLICY_VIOLATION);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        onlineUserManager.touch(session);
        if (message.getPayloadLength() > MAX_PAYLOAD_BYTES) {
            send(session, errorPayload("PAYLOAD_TOO_LARGE", "单次请求数据过大"));
            return;
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(message.getPayload());
        } catch (JsonProcessingException e) {
            send(session, errorPayload("INVALID_PAYLOAD", "消息格式无效"));
            return;
        }
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
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        evictSession(session, CloseStatus.SERVER_ERROR);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        onlineUserManager.unregisterConnection(session);
        leave(session);
    }

    /** 定时扫描并清理超时未心跳 / 已失效的僵尸连接，保证在线成员列表准确 */
    @Scheduled(fixedDelay = ZOMBIE_SCAN_INTERVAL_MS)
    public void cleanZombieSessions() {
        List<WebSocketSession> zombies = onlineUserManager.findZombieSessions(HEARTBEAT_TIMEOUT_MS, UNJOINED_TIMEOUT_MS);
        if (zombies.isEmpty()) {
            return;
        }
        log.info("Cleaning {} zombie WebSocket session(s)", zombies.size());
        for (WebSocketSession zombie : zombies) {
            evictSession(zombie, HEARTBEAT_TIMEOUT_CLOSE);
        }
    }

    private void evictSession(WebSocketSession session, CloseStatus closeStatus) {
        onlineUserManager.unregisterConnection(session);
        leave(session);
        if (session != null && session.isOpen()) {
            try {
                session.close(closeStatus);
            } catch (Exception ignored) {
            }
        }
    }

    private void join(WebSocketSession session, JsonNode node) {
        String ip = (String) session.getAttributes().get("ip");
        RateLimitResult joinLimit = rateLimitService.checkWsJoin(ip);
        if (!joinLimit.allowed()) {
            send(session, errorPayload("RATE_LIMITED", joinLimit.message()));
            return;
        }

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

        String anonId = (String) session.getAttributes().get("anonId");
        if (anonId == null || anonId.isBlank()) {
            anonId = guestService.newAnonId();
        }

        Room room = roomService.findByCode(roomCode);
        if (room == null) {
            send(session, errorPayload("ROOM_NOT_FOUND", "房间未创建"));
            return;
        }
        if (room.isPrivateRoom()) {
            RateLimitResult lockCheck = rateLimitService.checkPasswordLock(room.getCode(), ip, anonId);
            if (!lockCheck.allowed()) {
                send(session, errorPayload("PASSWORD_LOCKED", lockCheck.message()));
                return;
            }
            String password = node.path("password").asText("");
            if (!roomService.verifyPassword(room, password)) {
                RateLimitResult fail = rateLimitService.recordPasswordFailure(room.getCode(), ip, anonId);
                String errCode = fail.allowed() ? "INVALID_PASSWORD" : "PASSWORD_LOCKED";
                send(session, errorPayload(errCode, fail.message()));
                return;
            }
            rateLimitService.clearPasswordFailure(room.getCode(), ip, anonId);
        }

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

        String content = node.path("content").asText("").trim();
        if (content.isEmpty()) {
            return;
        }
        if (content.codePointCount(0, content.length()) > MAX_MESSAGE_LEN) {
            send(session, errorPayload("MESSAGE_TOO_LONG", "单条消息最多支持 " + MAX_MESSAGE_LEN + " 个字符"));
            return;
        }

        RateLimitResult msgLimit = rateLimitService.checkSendMessage(guest.getId(), guest.getIp());
        if (!msgLimit.allowed()) {
            send(session, errorPayload("RATE_LIMITED", msgLimit.message()));
            return;
        }

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
