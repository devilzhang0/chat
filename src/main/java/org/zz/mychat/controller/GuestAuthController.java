package org.zz.mychat.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zz.mychat.common.utils.ApiResult;
import org.zz.mychat.domain.User;
import org.zz.mychat.service.GuestService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class GuestAuthController {

    private static final String COOKIE_NAME = "guest_id";
    private static final int COOKIE_MAX_AGE = 60 * 60 * 24 * 30; // 30 天

    private final GuestService guestService;

    /** 前端加载时查询当前游客身份；无 cookie 则下发新身份 */
    @GetMapping("/me")
    public ApiResult<Map<String, Object>> me(HttpServletRequest request, HttpServletResponse response) {
        String anonId = cookieValue(request, COOKIE_NAME);
        Map<String, Object> data = new HashMap<>();
        if (anonId == null || anonId.isBlank()) {
            addCookie(response, guestService.newAnonId());
            data.put("found", false);
            return ApiResult.success(data);
        }
        User user = guestService.findByAnonId(anonId);
        if (user == null) {
            data.put("found", false);
        } else {
            data.put("found", true);
            data.put("nickname", user.getNickname());
            data.put("avatarColor", user.getAvatarColor());
        }
        return ApiResult.success(data);
    }

    /** 清除身份 cookie（忘记当前设备上的身份；不删除用户记录与历史） */
    @PostMapping("/clear")
    public ApiResult<Void> clear(HttpServletResponse response) {
        Cookie cookie = new Cookie(COOKIE_NAME, "");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        Cookie recentCookie = new Cookie("recent_rooms", "");
        recentCookie.setPath("/");
        recentCookie.setMaxAge(0);
        response.addCookie(recentCookie);
        return ApiResult.success();
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

    private void addCookie(HttpServletResponse response, String value) {
        Cookie cookie = new Cookie(COOKIE_NAME, value);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(COOKIE_MAX_AGE);
        response.addCookie(cookie);
    }
}
