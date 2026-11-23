package org.zz.mychat.config;

import jakarta.servlet.http.Cookie;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.zz.mychat.service.GuestService;
import org.zz.mychat.websocket.ChatWebSocketHandler;

import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final GuestService guestService;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws")
                .addInterceptors(new HandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
                        String anonId = cookieValue(request, "guest_id");
                        if (anonId == null || anonId.isBlank()) {
                            anonId = guestService.newAnonId();
                        }
                        attributes.put("anonId", anonId);
                        attributes.put("ip", clientIp(request));
                        return true;
                    }

                    @Override
                    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                               WebSocketHandler wsHandler, Exception exception) {
                    }
                })
                .setAllowedOrigins("*");
    }

    private String cookieValue(ServerHttpRequest request, String name) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            Cookie[] cookies = servletRequest.getServletRequest().getCookies();
            if (cookies != null) {
                for (Cookie c : cookies) {
                    if (name.equals(c.getName())) {
                        return c.getValue();
                    }
                }
            }
        }
        return null;
    }

    private String clientIp(ServerHttpRequest request) {
        List<String> xff = request.getHeaders().get("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            String first = xff.get(0);
            int comma = first.indexOf(',');
            return (comma > 0 ? first.substring(0, comma) : first).trim();
        }
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        return null;
    }
}
