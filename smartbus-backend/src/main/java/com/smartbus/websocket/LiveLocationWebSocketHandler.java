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
    private final com.smartbus.infrastructure.adapter.jpa.BusRepository busRepository;
    private final ObjectMapper objectMapper;
    private final com.smartbus.security.JwtTokenProvider jwtTokenProvider;

    // Client sessions viewing the live maps
    private final Set<WebSocketSession> clientSessions = ConcurrentHashMap.newKeySet();

    // Map of session to set of subscribed tripIds
    private final Map<WebSocketSession, Set<String>> tripSubscriptions = new ConcurrentHashMap<>();

    // Map of session to set of subscribed busIds
    private final Map<WebSocketSession, Set<String>> busSubscriptions = new ConcurrentHashMap<>();

    public LiveLocationWebSocketHandler(GpsUseCase gpsUseCase, TripRepository tripRepository, com.smartbus.infrastructure.adapter.jpa.BusRepository busRepository, ObjectMapper objectMapper, com.smartbus.security.JwtTokenProvider jwtTokenProvider) {
        this.gpsUseCase = gpsUseCase;
        this.tripRepository = tripRepository;
        this.busRepository = busRepository;
        this.objectMapper = objectMapper;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        authenticateSessionFromUri(session);
    }

    private void authenticateSessionFromUri(WebSocketSession session) {
        if (session.getUri() == null || session.getUri().getQuery() == null) return;
        String query = session.getUri().getQuery();
        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2) {
                if ("token".equalsIgnoreCase(pair[0])) {
                    authenticateSessionWithToken(session, pair[1]);
                }
            }
        }
    }

    private void authenticateSessionWithToken(WebSocketSession session, String token) {
        if (token != null && jwtTokenProvider.validateToken(token)) {
            String collegeIdStr = jwtTokenProvider.getCollegeIdFromJwt(token);
            if (collegeIdStr != null) {
                session.getAttributes().put("collegeId", UUID.fromString(collegeIdStr));
            }
            String role = jwtTokenProvider.getRoleFromJwt(token);
            if ("SUPER_ADMIN".equalsIgnoreCase(role)) {
                session.getAttributes().put("isSuperAdmin", true);
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            String payload = message.getPayload();
            WsMessage wsMessage = objectMapper.readValue(payload, WsMessage.class);

            if ("SUBSCRIBE_CLIENT".equalsIgnoreCase(wsMessage.getType()) || "SUBSCRIBE".equalsIgnoreCase(wsMessage.getType())) {
                if (wsMessage.getToken() != null) {
                    authenticateSessionWithToken(session, wsMessage.getToken());
                }

                boolean isSuperAdmin = Boolean.TRUE.equals(session.getAttributes().get("isSuperAdmin"));
                UUID sessionCollegeId = (UUID) session.getAttributes().get("collegeId");

                // Subscriptions strictly require an authenticated token with college tenant association
                if (!isSuperAdmin && sessionCollegeId == null) {
                    log.warn("Unauthorized WebSocket subscription rejected for session {}: token missing or invalid", session.getId());
                    session.sendMessage(new TextMessage("{\"type\":\"ERROR\",\"message\":\"Unauthorized: Missing or invalid authentication token\"}"));
                    return;
                }

                // Never trust client-supplied collegeId: verify it matches authenticated session if provided
                if (wsMessage.getCollegeId() != null && sessionCollegeId != null) {
                    try {
                        UUID requestedCollegeId = UUID.fromString(wsMessage.getCollegeId());
                        if (!sessionCollegeId.equals(requestedCollegeId)) {
                            log.warn("Cross-tenant WebSocket subscription rejected: session {} with college {} attempted college {}",
                                    session.getId(), sessionCollegeId, requestedCollegeId);
                            session.sendMessage(new TextMessage("{\"type\":\"ERROR\",\"message\":\"Forbidden: Cross-tenant subscription is not permitted\"}"));
                            return;
                        }
                    } catch (IllegalArgumentException e) {
                        session.sendMessage(new TextMessage("{\"type\":\"ERROR\",\"message\":\"Invalid collegeId format\"}"));
                        return;
                    }
                }

                // Verify tenant ownership if subscribing to a specific trip
                if (wsMessage.getTripId() != null && !isSuperAdmin) {
                    try {
                        UUID tripUuid = UUID.fromString(wsMessage.getTripId());
                        java.util.Optional<Trip> tripOpt = tripRepository.findById(tripUuid);
                        if (tripOpt.isPresent()) {
                            Trip t = tripOpt.get();
                            UUID tripCollegeId = t.getCollege() != null ? t.getCollege().getId() : (t.getBus() != null && t.getBus().getCollege() != null ? t.getBus().getCollege().getId() : null);
                            if (tripCollegeId != null && !sessionCollegeId.equals(tripCollegeId)) {
                                session.sendMessage(new TextMessage("{\"type\":\"ERROR\",\"message\":\"Forbidden: Cannot subscribe to another college trip\"}"));
                                return;
                            }
                        }
                    } catch (Exception ignored) {}
                }

                clientSessions.add(session);
                if (wsMessage.getTripId() != null) {
                    tripSubscriptions.computeIfAbsent(session, k -> ConcurrentHashMap.newKeySet()).add(wsMessage.getTripId());
                }
                if (wsMessage.getBusId() != null) {
                    busSubscriptions.computeIfAbsent(session, k -> ConcurrentHashMap.newKeySet()).add(wsMessage.getBusId());
                }
                log.debug("WebSocket Client Subscribed: {}", session.getId());
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
                    UUID collegeId = trip.getCollege() != null ? trip.getCollege().getId() : (trip.getBus() != null && trip.getBus().getCollege() != null ? trip.getBus().getCollege().getId() : null);

                    BroadcastLocation broadcast = new BroadcastLocation();
                    broadcast.setType("BUS_LOCATION_UPDATE");
                    broadcast.setCollegeId(collegeId != null ? collegeId.toString() : null);
                    broadcast.setTripId(tripId.toString());
                    broadcast.setBusId(trip.getBus().getId().toString());
                    broadcast.setBusNumber(trip.getBus().getBusNumber());
                    broadcast.setLatitude(trip.getBus().getCurrentLatitude());
                    broadcast.setLongitude(trip.getBus().getCurrentLongitude());
                    broadcast.setSpeed(wsMessage.getSpeed());
                    broadcast.setHeading(wsMessage.getHeading());
                    broadcast.setRouteName(trip.getRoute().getRouteName());

                    String jsonBroadcast = objectMapper.writeValueAsString(broadcast);
                    broadcastToClients(jsonBroadcast, tripId.toString(), trip.getBus().getId().toString(), collegeId);
                }
            }
        } catch (Exception e) {
            log.error("Error processing WebSocket message: {}", e.getMessage(), e);
        }
    }

    private void broadcastToClients(String message, String tripId, String busId, UUID eventCollegeId) {
        TextMessage textMessage = new TextMessage(message);
        for (WebSocketSession session : clientSessions) {
            if (session.isOpen()) {
                // Multi-college tenant isolation
                boolean isSuperAdmin = Boolean.TRUE.equals(session.getAttributes().get("isSuperAdmin"));
                UUID sessionCollegeId = (UUID) session.getAttributes().get("collegeId");

                // Non-SuperAdmin sessions without valid college association never receive broadcasts
                if (!isSuperAdmin && sessionCollegeId == null) {
                    continue;
                }

                if (!isSuperAdmin && eventCollegeId != null && !sessionCollegeId.equals(eventCollegeId)) {
                    continue; // Skip message - target session belongs to another college
                }

                // If the session has specific subscriptions, filter them.
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
            UUID collegeId = null;

            if (payload instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) payload;
                if (map.containsKey("tripId")) tripId = String.valueOf(map.get("tripId"));
                if (map.containsKey("busId")) busId = String.valueOf(map.get("busId"));
                if (map.containsKey("collegeId")) {
                    Object cid = map.get("collegeId");
                    if (cid instanceof UUID) collegeId = (UUID) cid;
                    else if (cid != null) {
                        try {
                            collegeId = UUID.fromString(String.valueOf(cid));
                        } catch (Exception ignored) {}
                    }
                }
            }

            if (collegeId == null && tripId != null) {
                try {
                    Trip t = tripRepository.findById(UUID.fromString(tripId)).orElse(null);
                    if (t != null) {
                        collegeId = t.getCollege() != null ? t.getCollege().getId() : (t.getBus() != null && t.getBus().getCollege() != null ? t.getBus().getCollege().getId() : null);
                    }
                } catch (Exception ignored) {}
            }

            broadcastToClients(json, tripId, busId, collegeId);
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
        private String token;
        private String collegeId;
        private Double latitude;
        private Double longitude;
        private Double speed;
        private Double heading;
    }

    @Getter
    @Setter
    public static class BroadcastLocation {
        private String type; // BUS_LOCATION_UPDATE
        private String collegeId;
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
