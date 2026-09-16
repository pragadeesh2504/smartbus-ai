package com.smartbus.config;

import com.smartbus.websocket.LiveLocationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.List;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final LiveLocationWebSocketHandler liveLocationWebSocketHandler;

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:8080}")
    private List<String> allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = allowedOrigins.toArray(new String[0]);
        registry.addHandler(liveLocationWebSocketHandler, "/ws/live")
                .setAllowedOrigins(origins);
    }
}
