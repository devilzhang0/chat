# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

MyChat is a Spring Boot 3.4.3 (Java 21, Maven) chat application. REST and WebSocket share one server on port `8080`.

- **REST API** — Spring Boot embedded Tomcat on port `8080`.
- **WebSocket** — Spring native WebSocket registered at `/ws` on the same Tomcat. `config/WebSocketConfig` (`WebSocketConfigurer`) wires `websocket/ChatWebSocketHandler`; a `HandshakeInterceptor` reads the `guest_id` cookie (anonymous identity) and captures the client IP before the connection is established.

- **Chat (guest) identity** uses an opaque `anonId` stored in an `HttpOnly` cookie (`guest_id`). No password/WeChat login — a user just picks a nickname. See the guest flow below.

The frontend is a separate Vue 3 + Vite + Tailwind app in `frontend/` (text-only chat, WS-based). In dev it talks to Spring through a Vite proxy; production serving of `dist/` from Spring is not wired up.

## Commands

```bash
./mvnw clean package          # full build (backend)
./mvnw compile                # compile only
./mvnw spring-boot:run        # start the backend (REST + WS on :8080)
./mvnw test                   # run all tests

cd frontend && npm install    # one-time
cd frontend && npm run dev    # Vite dev server (proxies /api + /ws to :8080)
cd frontend && npm run build  # production build (dist/)
cd frontend && npm run lint   # tsc --noEmit
```

The app requires MySQL; the datasource (host, credentials) is in `src/main/resources/application.yml`.

## Architecture

Package root: `org.zz.mychat`.

- `controller/` — `GuestAuthController` (`/api/auth/me` + `/api/auth/clear`), `RoomController` (`/api/rooms` create + `/api/rooms/recent` + `/api/rooms/{code}` lookup). Return `ApiResult<T>`.
- `service/` — `GuestService` (anonymous identity: anonId + user upsert), `RoomService` (room creation, 6-digit code generation, BCrypt password verification), `MessageService` (persist/load chat history per room with cursor pagination), `RateLimitService` (Guava sliding-window & password lockout).
- `mapper/` — MyBatis-Plus mappers (`UserMapper`, `RoomMapper`, `MessageMapper` extend `BaseMapper`).
- `domain/` — `User` (maps `user` table), `Room` (maps `room` table), `Message` (maps `message` table with `room_id`).
- `config/` — `SecurityConfig`, `SwaggerConfig`, `WebSocketConfig`.
- `websocket/` — `ChatWebSocketHandler`, `OnlineUserManager`, `GuestUser`.
- `common/` — `utils/` (`ApiResult`, `ClientIpUtils`), `enums/`.

### Guest identity + chat flow

1. Frontend loads → `GET /api/auth/me`. If no `guest_id` cookie, Spring generates an `anonId` (UUID), sets the `HttpOnly` cookie (30 days), returns `{found:false}`. If the cookie resolves to a `user` row, returns `{found:true, nickname, avatarColor}`.
2. User enters a nickname (or continues as the remembered one) → frontend opens `/ws`.
3. `HandshakeInterceptor` reads `guest_id` + captures IP (`X-Forwarded-For` → `remoteAddress`), stores them in session attributes.
4. On WS `join` (client sends `{type:"join", nickname, avatarColor}`), `GuestService.upsert` creates/updates the `user` row (`anon_id, nickname, avatar_color, ip, first_seen_at, last_seen_at`), then the handler sends `sync:init` (self + history + online) and broadcasts join/leave system messages + presence updates.
5. `POST /api/auth/clear` clears the `guest_id` cookie (forget identity on this device) — the user row and history are NOT deleted.

### WebSocket protocol (frontend ↔ backend)

Client → server: `join` `{nickname, avatarColor, roomCode, password?}`, `message:send` `{content, clientTempId}`, `ping`, `leave`.
Server → client: `sync:init` `{room, self, messages, onlineUsers}`, `message:new` `{roomCode, message, clientTempId?}`, `presence:update` `{roomCode, onlineUsers}`, `pong` `{timestamp}`, `error` `{code?, message}`.

Wire message shape: `{id: string, type: "text"|"system", senderId: anonId, senderName, avatarColor, content, timestamp(ms)}`. Only `text` messages are persisted to `message`; `system` (join/leave) are broadcast ephemerally. Payloads are flat — there is no `data` wrapper on WS frames (unlike the REST `ApiResult`).

## Gotchas

- **Externalized credentials & CORS**: `application.yml` uses `${SPRING_DATASOURCE_URL}`, `${SPRING_DATASOURCE_USERNAME}`, `${SPRING_DATASOURCE_PASSWORD}`, and `${MYCHAT_ALLOWED_ORIGIN_PATTERNS}`. For local dev, copy `application-dev.yml.example` to `application-dev.yml` (gitignored) and run with `-Dspring-boot.run.profiles=dev`. In production, set `MYCHAT_ALLOWED_ORIGIN_PATTERNS` to the actual frontend domain (do not use `*`).
- **DB schema migration required.** `user`, `room`, and `message` (with `room_id`) tables must be initialized — see `src/main/resources/db/init.sql` (fresh DB) or `schema.sql` (upgrade).
- **Identity is the `guest_id` cookie, not the nickname.** `anonId` is the stable ID; nickname/avatarColor are mutable (changing nickname updates the same `user` row). `message.sender_id` stores the `anonId` (string), not `user.id` (numeric).
- **Frontend is WS-only + text-only.** The Vite dev proxy forwards `/api` and `/ws` to `:8080`.
- **Unit tests without DB**: Run `./mvnw test -Dtest=RateLimitServiceTest,OnlineUserManagerTest,MessageServiceTest`. `MyChatApplicationTests` boots the full Spring context and requires a reachable MySQL datasource.
