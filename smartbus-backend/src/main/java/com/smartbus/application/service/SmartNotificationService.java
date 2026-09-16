package com.smartbus.application.service;

import com.smartbus.domain.model.*;
import com.smartbus.infrastructure.adapter.jpa.DriverNotificationRepository;
import com.smartbus.infrastructure.adapter.jpa.NotificationRepository;
import com.smartbus.infrastructure.adapter.jpa.StudentRepository;
import com.smartbus.infrastructure.adapter.jpa.RouteStopRepository;
import com.smartbus.websocket.LiveLocationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SmartNotificationService {

    private final StudentRepository studentRepository;
    private final NotificationRepository notificationRepository;
    private final DriverNotificationRepository driverNotificationRepository;
    private final LiveLocationWebSocketHandler webSocketHandler;

    @Autowired(required = false)
    private RouteStopRepository routeStopRepository;

    // Deduplication tracking map: Key = eventKey, Value = timestamp
    private final Set<String> sentNotificationKeys = ConcurrentHashMap.newKeySet();

    /**
     * Triggered when driver starts a trip.
     */
    public void handleTripStarted(Trip trip) {
        if (trip == null || trip.getBus() == null) return;
        String dedupKey = trip.getId() + "_TRIP_STARTED";
        if (sentNotificationKeys.contains(dedupKey)) {
            return;
        }
        sentNotificationKeys.add(dedupKey);

        String busNumber = trip.getBus().getBusNumber();
        String routeName = trip.getRoute() != null ? trip.getRoute().getRouteName() : "assigned route";
        String title = "🚌 Bus " + busNumber + " Started";
        String message = "Your bus " + busNumber + " on route " + routeName + " has started its trip!";

        Set<Student> targetStudents = new HashSet<>();

        // 1. Students who favorited this bus
        targetStudents.addAll(studentRepository.findStudentsByFavoriteBusId(trip.getBus().getId()));

        // 2. Students whose preferred stop is on this route
        if (trip.getRoute() != null && routeStopRepository != null) {
            List<RouteStop> rStops = routeStopRepository.findByRouteIdOrderBySequenceNumberAsc(trip.getRoute().getId());
            for (RouteStop rs : rStops) {
                if (rs.getStop() != null) {
                    targetStudents.addAll(studentRepository.findByPreferredStopId(rs.getStop().getId()));
                }
            }
        }

        for (Student student : targetStudents) {
            String prefs = student.getNotificationPreferences();
            if (prefs == null) {
                prefs = "APPROACHING,ARRIVED,DEPARTED,DELAYED,OFF_ROUTE,TRIP_STARTED";
            }
            if (prefs.toUpperCase().contains("TRIP_STARTED") || prefs.toUpperCase().contains("BUS_STARTED") || "ALL".equalsIgnoreCase(prefs) || prefs.contains("APPROACHING")) {
                Notification notif = Notification.builder()
                        .user(student.getUser())
                        .title(title)
                        .message(message)
                        .type("INFO")
                        .isRead(false)
                        .createdAt(LocalDateTime.now())
                        .build();
                notificationRepository.save(notif);

                Map<String, Object> personalAlert = new HashMap<>();
                personalAlert.put("type", "NOTIFICATION");
                personalAlert.put("studentId", student.getId().toString());
                personalAlert.put("title", title);
                personalAlert.put("message", message);
                personalAlert.put("priority", "INFO");
                personalAlert.put("createdAt", LocalDateTime.now().toString());
                webSocketHandler.broadcast(personalAlert);
            }
        }

        // Broadcast general BUS_STARTED WebSocket event
        Map<String, Object> wsEvent = new HashMap<>();
        wsEvent.put("type", "BUS_STARTED");
        wsEvent.put("tripId", trip.getId().toString());
        wsEvent.put("busId", trip.getBus().getId().toString());
        wsEvent.put("busNumber", busNumber);
        wsEvent.put("routeName", routeName);
        wsEvent.put("timestamp", LocalDateTime.now().toString());
        webSocketHandler.broadcast(wsEvent);
    }

    /**
     * Triggered when a bus enters approaching radius (~500m) of a stop.
     */
    public void handleApproachingStop(Trip trip, Stop stop, int etaMins) {
        String dedupKey = trip.getId() + "_" + stop.getId() + "_APPROACHING";
        if (sentNotificationKeys.contains(dedupKey)) {
            return;
        }
        sentNotificationKeys.add(dedupKey);

        String busNumber = trip.getBus().getBusNumber();
        String title = "🚌 " + busNumber + " APPROACHING";
        String message = busNumber + " is approaching " + stop.getStopName() + ". Estimated arrival: " + Math.max(1, etaMins) + " minutes.";

        dispatchStudentNotifications(trip, stop, "APPROACHING", title, message, "INFO");

        // Broadcast to live WebSocket
        Map<String, Object> wsEvent = new HashMap<>();
        wsEvent.put("type", "BUS_APPROACHING_STOP");
        wsEvent.put("tripId", trip.getId().toString());
        wsEvent.put("busId", trip.getBus().getId().toString());
        wsEvent.put("busNumber", busNumber);
        wsEvent.put("stopId", stop.getId().toString());
        wsEvent.put("stopName", stop.getStopName());
        wsEvent.put("etaMinutes", etaMins);
        wsEvent.put("timestamp", LocalDateTime.now().toString());
        webSocketHandler.broadcast(wsEvent);
    }

    /**
     * Triggered when a bus enters arrival radius (~100m) of a stop.
     */
    public void handleArrivedStop(Trip trip, Stop stop) {
        String dedupKey = trip.getId() + "_" + stop.getId() + "_ARRIVED";
        if (sentNotificationKeys.contains(dedupKey)) {
            return;
        }
        sentNotificationKeys.add(dedupKey);

        String busNumber = trip.getBus().getBusNumber();
        String title = "🚌 " + busNumber + " HAS ARRIVED";
        String message = busNumber + " has arrived at " + stop.getStopName() + ". Please board the bus.";

        dispatchStudentNotifications(trip, stop, "ARRIVED", title, message, "SUCCESS");

        // Broadcast to live WebSocket
        Map<String, Object> wsEvent = new HashMap<>();
        wsEvent.put("type", "BUS_ARRIVED_STOP");
        wsEvent.put("tripId", trip.getId().toString());
        wsEvent.put("busId", trip.getBus().getId().toString());
        wsEvent.put("busNumber", busNumber);
        wsEvent.put("stopId", stop.getId().toString());
        wsEvent.put("stopName", stop.getStopName());
        wsEvent.put("timestamp", LocalDateTime.now().toString());
        webSocketHandler.broadcast(wsEvent);
    }

    /**
     * Triggered when a bus departs a stop.
     */
    public void handleDepartedStop(Trip trip, Stop stop) {
        String dedupKey = trip.getId() + "_" + stop.getId() + "_DEPARTED";
        if (sentNotificationKeys.contains(dedupKey)) {
            return;
        }
        sentNotificationKeys.add(dedupKey);

        String busNumber = trip.getBus().getBusNumber();
        String title = "🚌 " + busNumber + " DEPARTED";
        String message = busNumber + " has departed " + stop.getStopName() + " and is en route to the next stop.";

        dispatchStudentNotifications(trip, stop, "DEPARTED", title, message, "INFO");

        // Broadcast to live WebSocket
        Map<String, Object> wsEvent = new HashMap<>();
        wsEvent.put("type", "BUS_DEPARTED_STOP");
        wsEvent.put("tripId", trip.getId().toString());
        wsEvent.put("busId", trip.getBus().getId().toString());
        wsEvent.put("busNumber", busNumber);
        wsEvent.put("stopId", stop.getId().toString());
        wsEvent.put("stopName", stop.getStopName());
        wsEvent.put("timestamp", LocalDateTime.now().toString());
        webSocketHandler.broadcast(wsEvent);
    }

    /**
     * Triggered when a trip crosses the delay threshold.
     */
    public void handleTripDelayed(Trip trip, int delayMins, Stop nextStop) {
        String dedupKey = trip.getId() + "_" + (nextStop != null ? nextStop.getId() : "GENERAL") + "_DELAYED";
        if (sentNotificationKeys.contains(dedupKey)) {
            return;
        }
        sentNotificationKeys.add(dedupKey);

        String busNumber = trip.getBus().getBusNumber();
        String title = "⚠️ " + busNumber + " DELAYED";
        String message = busNumber + " is running approximately " + delayMins + " minutes behind schedule.";
        if (nextStop != null) {
            message += " Next expected stop: " + nextStop.getStopName() + ".";
        }

        dispatchStudentNotifications(trip, nextStop, "DELAYED", title, message, "WARNING");

        // Broadcast to live WebSocket
        Map<String, Object> wsEvent = new HashMap<>();
        wsEvent.put("type", "BUS_DELAYED");
        wsEvent.put("tripId", trip.getId().toString());
        wsEvent.put("busId", trip.getBus().getId().toString());
        wsEvent.put("busNumber", busNumber);
        wsEvent.put("delayMinutes", delayMins);
        wsEvent.put("nextStopName", nextStop != null ? nextStop.getStopName() : null);
        wsEvent.put("timestamp", LocalDateTime.now().toString());
        webSocketHandler.broadcast(wsEvent);
    }

    /**
     * Triggered when a bus persistently deviates from the assigned route.
     */
    public void handleRouteDeviation(Trip trip, double deviationMeters, boolean isOffRoute) {
        String busNumber = trip.getBus().getBusNumber();

        if (isOffRoute) {
            String dedupKey = trip.getId() + "_OFF_ROUTE";
            if (sentNotificationKeys.contains(dedupKey)) {
                return;
            }
            sentNotificationKeys.add(dedupKey);
            sentNotificationKeys.remove(trip.getId() + "_BACK_ON_ROUTE"); // Allow future back-on-route alert

            String title = "🚨 " + busNumber + " OFF-ROUTE";
            String studentMessage = busNumber + " is experiencing a route deviation (~" + Math.round(deviationMeters) + "m). Live arrival times may adjust.";
            String driverMessage = "⚠ Route deviation detected (~" + Math.round(deviationMeters) + "m). Please return to the assigned route.";

            // 1. Notify relevant students
            dispatchStudentNotifications(trip, null, "OFF_ROUTE", title, studentMessage, "CRITICAL");

            // 2. Notify Driver
            if (trip.getDriver() != null) {
                DriverNotification driverNotif = DriverNotification.builder()
                        .driver(trip.getDriver())
                        .title("⚠ ROUTE DEVIATION")
                        .message(driverMessage)
                        .type("CRITICAL")
                        .read(false)
                        .createdAt(LocalDateTime.now())
                        .build();
                driverNotificationRepository.save(driverNotif);
            }

            // 3. Broadcast WebSocket event
            Map<String, Object> wsEvent = new HashMap<>();
            wsEvent.put("type", "BUS_OFF_ROUTE");
            wsEvent.put("tripId", trip.getId().toString());
            wsEvent.put("busId", trip.getBus().getId().toString());
            wsEvent.put("busNumber", busNumber);
            wsEvent.put("deviationMeters", deviationMeters);
            wsEvent.put("timestamp", LocalDateTime.now().toString());
            webSocketHandler.broadcast(wsEvent);

        } else {
            // Back on route
            String dedupKey = trip.getId() + "_BACK_ON_ROUTE";
            if (sentNotificationKeys.contains(dedupKey)) {
                return;
            }
            sentNotificationKeys.add(dedupKey);
            sentNotificationKeys.remove(trip.getId() + "_OFF_ROUTE"); // Reset off-route flag

            String title = "✅ " + busNumber + " BACK ON ROUTE";
            String message = busNumber + " has returned to its assigned route path.";

            dispatchStudentNotifications(trip, null, "OFF_ROUTE", title, message, "INFO");

            Map<String, Object> wsEvent = new HashMap<>();
            wsEvent.put("type", "BUS_BACK_ON_ROUTE");
            wsEvent.put("tripId", trip.getId().toString());
            wsEvent.put("busId", trip.getBus().getId().toString());
            wsEvent.put("busNumber", busNumber);
            wsEvent.put("timestamp", LocalDateTime.now().toString());
            webSocketHandler.broadcast(wsEvent);
        }
    }

    /**
     * Triggered when GPS signal becomes stale (> 60s).
     */
    public void handleGpsStale(Trip trip, long secondsSinceLastUpdate) {
        String dedupKey = trip.getId() + "_GPS_STALE";
        if (sentNotificationKeys.contains(dedupKey)) {
            return;
        }
        sentNotificationKeys.add(dedupKey);

        String busNumber = trip.getBus().getBusNumber();

        Map<String, Object> wsEvent = new HashMap<>();
        wsEvent.put("type", "GPS_STALE");
        wsEvent.put("tripId", trip.getId().toString());
        wsEvent.put("busId", trip.getBus().getId().toString());
        wsEvent.put("busNumber", busNumber);
        wsEvent.put("secondsOffline", secondsSinceLastUpdate);
        wsEvent.put("timestamp", LocalDateTime.now().toString());
        webSocketHandler.broadcast(wsEvent);
    }

    /**
     * Helper to dispatch notifications to students with matching stop/bus and preference criteria.
     */
    private void dispatchStudentNotifications(Trip trip, Stop stop, String preferenceCategory, String title, String message, String priority) {
        Set<Student> targetStudents = new HashSet<>();

        // 1. Students whose preferred stop is this stop
        if (stop != null) {
            targetStudents.addAll(studentRepository.findByPreferredStopId(stop.getId()));
        }

        // 2. Students who favorited this bus
        if (trip.getBus() != null) {
            targetStudents.addAll(studentRepository.findStudentsByFavoriteBusId(trip.getBus().getId()));
        }

        for (Student student : targetStudents) {
            String prefs = student.getNotificationPreferences();
            if (prefs == null) {
                prefs = "APPROACHING,ARRIVED,DEPARTED,DELAYED,OFF_ROUTE";
            }

            // Check if student enabled this category
            if (prefs.toUpperCase().contains(preferenceCategory.toUpperCase()) || "ALL".equalsIgnoreCase(prefs)) {
                Notification notif = Notification.builder()
                        .user(student.getUser())
                        .title(title)
                        .message(message)
                        .type(priority)
                        .isRead(false)
                        .createdAt(LocalDateTime.now())
                        .build();
                notificationRepository.save(notif);

                // Broadcast personal notification packet
                Map<String, Object> personalAlert = new HashMap<>();
                personalAlert.put("type", "NOTIFICATION");
                personalAlert.put("studentId", student.getId().toString());
                personalAlert.put("title", title);
                personalAlert.put("message", message);
                personalAlert.put("priority", priority);
                personalAlert.put("createdAt", LocalDateTime.now().toString());
                webSocketHandler.broadcast(personalAlert);
            }
        }
    }

    public void clearTripCache(UUID tripId) {
        sentNotificationKeys.removeIf(key -> key.startsWith(tripId.toString()));
    }
}
