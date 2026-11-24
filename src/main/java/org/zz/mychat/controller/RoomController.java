package org.zz.mychat.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zz.mychat.common.utils.ApiResult;
import org.zz.mychat.common.utils.ClientIpUtils;
import org.zz.mychat.domain.Room;
import org.zz.mychat.service.GuestService;
import org.zz.mychat.service.RateLimitService;
import org.zz.mychat.service.RateLimitService.RateLimitResult;
import org.zz.mychat.service.RoomService;
import org.zz.mychat.websocket.OnlineUserManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private static final String COOKIE_NAME = "guest_id";
    private static final String RECENT_ROOMS_COOKIE = "recent_rooms";
    private static final int MAX_RECENT_ROOMS = 10;
    private static final int COOKIE_MAX_AGE = 60 * 60 * 24 * 30; // 30 天

    private final RoomService roomService;
    private final GuestService guestService;
    private final OnlineUserManager onlineUserManager;
    private final RateLimitService rateLimitService;

    @PostMapping
    public ApiResult<Map<String, Object>> create(@RequestBody CreateRoomRequest req,
                                                 HttpServletRequest request,
                                                 HttpServletResponse response) {
        String ip = ClientIpUtils.resolve(request);
        String anonId = cookieValue(request, COOKIE_NAME);
        if (anonId == null || anonId.isBlank()) {
            anonId = guestService.newAnonId();
            addGuestCookie(request, response, anonId);
        }

        RateLimitResult limit = rateLimitService.checkCreateRoom(ip, anonId);
        if (!limit.allowed()) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(limit.retryAfterSeconds()));
            return ApiResult.fail(429, limit.message());
        }

        try {
            boolean isPrivate = Boolean.TRUE.equals(req.getIsPrivate());
            Room room = roomService.createRoom(req.getName(), isPrivate, req.getPassword(), anonId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("code", room.getCode());
            data.put("name", room.getName());
            data.put("isPrivate", room.isPrivateRoom());
            return ApiResult.success(data);
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResult.fail(500, e.getMessage());
        }
    }

    @GetMapping("/recent")
    public ApiResult<List<Map<String, Object>>> recent(HttpServletRequest request,
                                                       HttpServletResponse response) {
        RateLimitResult limit = rateLimitService.checkRoomLookup(ClientIpUtils.resolve(request));
        if (!limit.allowed()) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(limit.retryAfterSeconds()));
            return ApiResult.fail(429, limit.message());
        }

        String raw = cookieValue(request, RECENT_ROOMS_COOKIE);
        if (raw == null || raw.isBlank()) {
            return ApiResult.success(List.of());
        }
        Set<String> orderedCodes = new LinkedHashSet<>();
        for (String part : raw.split("[-,]")) {
            String code = part.trim();
            if (!code.isEmpty() && code.length() <= 16) {
                orderedCodes.add(code);
                if (orderedCodes.size() >= MAX_RECENT_ROOMS) {
                    break;
                }
            }
        }
        if (orderedCodes.isEmpty()) {
            return ApiResult.success(List.of());
        }

        List<Room> found = roomService.findByCodes(new ArrayList<>(orderedCodes));
        Map<String, Room> byCode = found.stream()
                .collect(Collectors.toMap(Room::getCode, Function.identity(), (a, b) -> a));

        List<Map<String, Object>> result = new ArrayList<>();
        for (String code : orderedCodes) {
            Room room = byCode.get(code);
            if (room != null) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("code", room.getCode());
                item.put("name", room.getName());
                item.put("isPrivate", room.isPrivateRoom());
                item.put("onlineCount", onlineUserManager.getOnlineCount(room.getId()));
                result.add(item);
            }
        }
        return ApiResult.success(result);
    }

    @GetMapping("/{code}")
    public ApiResult<Map<String, Object>> lookup(@PathVariable String code,
                                                 HttpServletRequest request,
                                                 HttpServletResponse response) {
        RateLimitResult limit = rateLimitService.checkRoomLookup(ClientIpUtils.resolve(request));
        if (!limit.allowed()) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(limit.retryAfterSeconds()));
            return ApiResult.fail(429, limit.message());
        }

        Room room = roomService.findByCode(code);
        Map<String, Object> data = new LinkedHashMap<>();
        if (room == null) {
            data.put("exists", false);
            return ApiResult.success(data);
        }
        data.put("exists", true);
        data.put("code", room.getCode());
        data.put("name", room.getName());
        data.put("isPrivate", room.isPrivateRoom());
        data.put("onlineCount", onlineUserManager.getOnlineCount(room.getId()));
        return ApiResult.success(data);
    }

    private String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    private void addGuestCookie(HttpServletRequest request, HttpServletResponse response, String value) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, value)
                .path("/")
                .httpOnly(true)
                .secure(ClientIpUtils.isHttps(request))
                .sameSite("Lax")
                .maxAge(COOKIE_MAX_AGE)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    @Data
    public static class CreateRoomRequest {
        private String name;
        private Boolean isPrivate;
        private String password;
    }
}
