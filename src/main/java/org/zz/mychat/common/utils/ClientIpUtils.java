package org.zz.mychat.common.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.ServerHttpRequest;

import java.util.List;

/** 统一提取客户端真实 IP（兼容 Nginx 反向代理 X-Forwarded-For / X-Real-IP） */
public final class ClientIpUtils {

    private static final String UNKNOWN = "unknown";

    private ClientIpUtils() {
    }

    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        String xff = request.getHeader("X-Forwarded-For");
        String ip = firstValidIp(xff);
        if (ip != null) {
            return ip;
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String remote = request.getRemoteAddr();
        return (remote != null && !remote.isBlank()) ? remote : UNKNOWN;
    }

    public static String resolve(ServerHttpRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        List<String> xff = request.getHeaders().get("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            String ip = firstValidIp(xff.get(0));
            if (ip != null) {
                return ip;
            }
        }
        List<String> realIp = request.getHeaders().get("X-Real-IP");
        if (realIp != null && !realIp.isEmpty() && realIp.get(0) != null && !realIp.get(0).isBlank()) {
            return realIp.get(0).trim();
        }
        if (request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        return UNKNOWN;
    }

    public static boolean isHttps(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        if (request.isSecure()) {
            return true;
        }
        String proto = request.getHeader("X-Forwarded-Proto");
        return proto != null && "https".equalsIgnoreCase(proto.trim());
    }

    private static String firstValidIp(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        int comma = header.indexOf(',');
        String first = (comma > 0 ? header.substring(0, comma) : header).trim();
        return first.isEmpty() ? null : first;
    }
}
