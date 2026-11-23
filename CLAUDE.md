# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

MyChat is a Spring Boot 3.4.3 (Java 21, Maven) chat application. REST and WebSocket share one server on port `8080`.

- **REST API** — Spring Boot embedded Tomcat on port `8080`.
- **WebSocket** — Spring native WebSocket registered at `/ws` on the same Tomcat. `config/WebSocketConfig` (`WebSocketConfigurer`) wires `websocket/ChatWebSocketHandler`; a `HandshakeInterceptor` reads the `guest_id` cookie (anonymous identity) and captures the client IP before the connection is established.

Two separate auth paths coexist:

- **Admin REST login** (`POST /api/auth/login`) still uses JWT via `JwtAuthFilter` (demo scaffolding, hardcoded `admin`).
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

- `controller/` — `LoginController` (admin JWT login), `GuestAuthController` (`/api/auth/me` + `/api/auth/clear`), `HelloController` (event demo). Return `ApiResult<T>`.
- `service/` — `GuestService` (anonymous identity: anonId + user upsert), `MessageService` (persist/load chat history), `UserDetailsServiceImpl` (admin lookup).
- `mapper/` — MyBatis-Plus mappers (`UserMapper`, `MessageMapper` extend `BaseMapper`).
- `domain/` — `User` (maps `user` table, implements `UserDetails`), `Message` (maps `message` table).
- `config/` — `SecurityConfig`, `JwtConfig`, `SwaggerConfig`, `WebSocketConfig`.
- `websocket/` — `ChatWebSocketHandler`, `OnlineUserManager`, `GuestUser`.
- `common/` — `filter/JwtAuthFilter`, `utils/` (JWT, `ApiResult`), `enums/`.
- `event/` — Spring `ApplicationEvent` demo (`MessagePublisher`/`MessageListener`).

### Guest identity + chat flow

1. Frontend loads → `GET /api/auth/me`. If no `guest_id` cookie, Spring generates an `anonId` (UUID), sets the `HttpOnly` cookie (30 days), returns `{found:false}`. If the cookie resolves to a `user` row, returns `{found:true, nickname, avatarColor}`.
2. User enters a nickname (or continues as the remembered one) → frontend opens `/ws`.
3. `HandshakeInterceptor` reads `guest_id` + captures IP (`X-Forwarded-For` → `remoteAddress`), stores them in session attributes.
4. On WS `join` (client sends `{type:"join", nickname, avatarColor}`), `GuestService.upsert` creates/updates the `user` row (`anon_id, nickname, avatar_color, ip, first_seen_at, last_seen_at`), then the handler sends `sync:init` (self + history + online) and broadcasts join/leave system messages + presence updates.
5. `POST /api/auth/clear` clears the `guest_id` cookie (forget identity on this device) — the user row and history are NOT deleted.

### WebSocket protocol (frontend ↔ backend)

Client → server: `join` `{nickname, avatarColor}`, `message:send` `{content, clientTempId}`, `ping`, `leave`.
Server → client: `sync:init` `{self, messages, onlineUsers}`, `message:new` `{message, clientTempId?}`, `presence:update` `{onlineUsers}`, `pong` `{timestamp}`, `error` `{message}`.

Wire message shape: `{id: string, type: "text"|"system", senderId: anonId, senderName, avatarColor, content, timestamp(ms)}`. Only `text` messages are persisted to `message`; `system` (join/leave) are broadcast ephemerally. Payloads are flat — there is no `data` wrapper on WS frames (unlike the REST `ApiResult`).

## Gotchas

- **Hardcoded secrets/credentials** live in `application.yml` (MySQL host/username/password, JWT secret) and in `UserDetailsServiceImpl` (admin password hash). Do not copy these into new code.
- **DB schema migration required.** `user` needs `anon_id/avatar_color/ip/first_seen_at/last_seen_at/nickname` columns, and `message` is recreated with `sender_id/avatar_color/create_time(BIGINT ms)` — see `src/main/resources/db/schema.sql`. Run it before starting, or MyBatis-Plus queries fail on unknown columns.
- **Identity is the `guest_id` cookie, not the nickname.** `anonId` is the stable ID; nickname/avatarColor are mutable (changing nickname updates the same `user` row). `message.sender_id` stores the `anonId` (string), not `user.id` (numeric).
- **Frontend is WS-only + text-only.** No image/emoji upload (removed along with WeChat login). The Vite dev proxy forwards `/api` and `/ws` to `:8080`.
- **`mapper/UserInterface.java` is dead code** — unused stub implementing `IService<User>`; use `UserMapper` instead.
- **`config/SwaggerServerCustomizer.java` is `@Deprecated`** with its `@Component` commented out.
- **`src/test/java/.../Test.java` and `test1.java` are scratch `main()` files**, not JUnit tests. Real tests: `MyChatApplicationTests`, `MybatisTest`.
- **`ApiResult.fail(String code, String msg, T data)` is buggy** — sets `success=true` and drops `code`/`msg`. Prefer `ApiResult.fail(ResultEnum)`.
- `MybatisTest` asserts hardcoded DB state (`5 == userList.size()`), so it fails against a fresh/empty database.
