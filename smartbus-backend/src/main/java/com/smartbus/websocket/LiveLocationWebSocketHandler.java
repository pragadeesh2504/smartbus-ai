package com.smartbus.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbus.application.port.in.GpsUseCase;
import com.smartbus.domain.model.Trip;
import com.smartbus.infrastructure.adapter.jpa.TripRepository;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class LiveLocationWebSocketHandler extends TextWebSocketHandler {

    private final GpsUseCase gpsUseCase;
    private final TripRepository tripRepository;
    private final ObjectMapper objectMapper;

    // Client sessions viewing the live maps
    private final Set<WebSocketSession> clientSessions = ConcurrentHashMap.newKeySet();

    // Map of session to set of subscribed tripIds
    private final Map<WebSocketSession, Set<String>> tripSubscriptions = new ConcurrentHashMap<>();

    // Map of session to set of subscribed busIds
    private final Map<WebSocketSession, Set<String>> busSubscriptions = new ConcurrentHashMap<>();

    public LiveLocationWebSocketHandler(GpsUseCase gpsUseCase, TripRepository tripRepository, ObjectMapper objectMapper) {
        this.gpsUseCase = gpsUseCase;
        this.tripRepository = tripRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // Just track connection, wait for subscription message
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            String payload = message.getPayload();
            WsMessage wsMessage = objectMapper.readValue(payload, WsMessage.class);

            if ("SUBSCRIBE_CLIENT".equalsIgnoreCase(wsMessage.getType()) || "SUBSCRIBE".equalsIgnoreCase(wsMessage.getType())) {
                clientSessions.add(session);
                if (wsMessage.getTripId() != null) {
                    tripSubscriptions.computeIfAbsent(session, k -> ConcurrentHashMap.newKeySet()).add(wsMessage.getTripId());
                }
                if (wsMessage.getBusId() != null) {
                    busSubscriptions.computeIfAbsent(session, k -> ConcurrentHashMap.newKeySet()).add(wsMessage.getBusId());
                }
                System.out.println("WebSocket Client Subscribed: " + session.getId());
            } else if ("DRIVER_UPDATE".equalsIgnoreCase(wsMessage.getType())) {
                if (wsMessage.getTripId() == null) return;

                UUID tripId = UUID.fromString(wsMessage.getTripId());
                
                // Process the location update (filters and saves coordinates)
                gpsUseCase.processLocationUpdate(
                        tripId,
                        wsMessage.getLatitude(),
                        wsMessage.getLongitude(),
                        wsMessage.getSpeed(),
                        wsMessage.getHeading()
                );

                // Fetch trip and bus info to construct broadcast message
                Trip trip = tripRepository.findById(tripId).orElse(null);
                if (trip != null) {
                    BroadcastLocation broadcast = new BroadcastLocation();
                    broadcast.setType("BUS_LOCATION_UPDATE");
                    broadcast.setTripId(tripId.toString());
                    broadcast.setBusId(trip.getBus().getId().toString());
                    broadcast.setBusNumber(trip.getBus().getBusNumber());
                    broadcast.setLatitude(trip.getBus().getCurrentLatitude());
                    broadcast.setLongitude(trip.getBus().getCurrentLongitude());
                    broadcast.setSpeed(wsMessage.getSpeed());
                    broadcast.setHeading(wsMessage.getHeading());
                    broadcast.setRouteName(trip.getRoute().getRouteName());

                    String jsonBroadcast = objectMapper.writeValueAsString(broadcast);
                    broadcastToClients(jsonBroadcast, tripId.toString(), trip.getBus().getId().toString());
                }
            }
        } catch (Exception e) {
            log.error("Error processing WebSocket message: {}", e.getMessage(), e);
        }
    }

    private void broadcastToClients(String message, String tripId, String busId) {
        TextMessage textMessage = new TextMessage(message);
        for (WebSocketSession session : clientSessions) {
            if (session.isOpen()) {
                // If the session has specific subscriptions, filter them.
                // If it has NO subscriptions at all (like admin map), let all updates pass.
                Set<String> tripSubs = tripSubscriptions.get(session);
                Set<String> busSubs = busSubscriptions.get(session);

                boolean hasTripSubs = (tripSubs != null && !tripSubs.isEmpty());
                boolean hasBusSubs = (busSubs != null && !busSubs.isEmpty());

                boolean match = false;
                if (!hasTripSubs && !hasBusSubs) {
                    match = true;
                } else {
                    if (hasTripSubs && tripId != null && tripSubs.contains(tripId)) {
                        match = true;
                    }
                    if (hasBusSubs && busId != null && busSubs.contains(busId)) {
                        match = true;
                    }
                }

                if (match) {
                    try {
                        session.sendMessage(textMessage);
                    } catch (IOException e) {
                        removeSession(session);
                    }
                }
            } else {
                removeSession(session);
            }
        }
    }

    public void broadcast(Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            String tripId = null;
            String busId = null;

            if (payload instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) payload;
                if (map.containsKey("tripId")) tripId = String.valueOf(map.get("tripId"));
                if (map.containsKey("busId")) busId = String.valueOf(map.get("busId"));
            }

            broadcastToClients(json, tripId, busId);
        } catch (Exception e) {
            log.error("Error broadcasting payload: {}", e.getMessage(), e);
        }
    }

    private void removeSession(WebSocketSession session) {
        clientSessions.remove(session);
        tripSubscriptions.remove(session);
        busSubscriptions.remove(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        removeSession(session);
    }

    @Getter
    @Setter
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class WsMessage {
        @com.fasterxml.jackson.annotation.JsonAlias({"action", "type"})
        private String type; // SUBSCRIBE_CLIENT, DRIVER_UPDATE, SUBSCRIBE
        private String tripId;
        private String busId;
        private Double latitude;
        private Double longitude;
        private Double speed;
        private Double heading;
    }

    @Getter
    @Setter
    public static class BroadcastLocation {
        private String type; // BUS_LOCATION_UPDATE
        private String tripId;
        private String busId;
        private String busNumber;
        private Double latitude;
        private Double longitude;
        private Double speed;
        private Double heading;
        private String routeName;
    }
}
