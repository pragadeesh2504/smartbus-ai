package com.smartbus.infrastructure.controller;

import com.smartbus.application.service.AuditLogService;
import com.smartbus.domain.exception.BadRequestException;
import com.smartbus.domain.exception.ResourceNotFoundException;
import com.smartbus.domain.model.Bus;
import com.smartbus.domain.model.BusQrToken;
import com.smartbus.domain.model.GpsDevice;
import com.smartbus.domain.model.Schedule;
import com.smartbus.domain.model.Trip;
import com.smartbus.infrastructure.adapter.jpa.BusQrTokenRepository;
import com.smartbus.infrastructure.adapter.jpa.BusRepository;
import com.smartbus.infrastructure.adapter.jpa.GpsDeviceRepository;
import com.smartbus.infrastructure.adapter.jpa.ScheduleRepository;
import com.smartbus.infrastructure.adapter.jpa.TripRepository;
import com.smartbus.infrastructure.dto.ApiResponse;
import com.smartbus.infrastructure.dto.BusDto;
import com.smartbus.infrastructure.dto.EtaResponse;
import com.smartbus.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/buses")
@RequiredArgsConstructor
@Slf4j
@org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
public class AdminBusController {

    private final BusRepository busRepository;
    private final BusQrTokenRepository busQrTokenRepository;
    private final GpsDeviceRepository gpsDeviceRepository;
    private final TripRepository tripRepository;
    private final ScheduleRepository scheduleRepository;
    private final AuditLogService auditLogService;
    private final com.smartbus.application.service.EtaCalculationService etaCalculationService;
    private final com.smartbus.infrastructure.adapter.jpa.TripLocationRepository tripLocationRepository;
    private final com.smartbus.infrastructure.adapter.jpa.RouteStopRepository routeStopRepository;
    private final com.smartbus.infrastructure.mapper.RouteStopMapper routeStopMapper;

    @GetMapping("/active-fleet")
    public ResponseEntity<ApiResponse<List<com.smartbus.infrastructure.dto.ActiveBusFleetDto>>> getActiveFleet(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        List<Trip> activeTrips = tripRepository.findByStatusIn(Arrays.asList("IN_PROGRESS", "PAUSED", "EN_ROUTE"));
        if (userPrincipal != null && userPrincipal.getCollegeId() != null) {
            UUID collegeId = userPrincipal.getCollegeId();
            activeTrips = activeTrips.stream()
                    .filter(t -> t.getCollege() != null && collegeId.equals(t.getCollege().getId()))
                    .collect(Collectors.toList());
        }
        List<com.smartbus.infrastructure.dto.ActiveBusFleetDto> fleet = new ArrayList<>();

        for (Trip trip : activeTrips) {
            Bus bus = trip.getBus();
            if (bus == null || bus.getDeletedAt() != null) continue;

            // Retrieve latest authoritative coordinates
            Double lat = null;
            Double lng = null;
            Double speed = 0.0;
            Double heading = 0.0;
            Double accuracy = 10.0;
            String trackingSource = "AUTHORITATIVE_GPS";
            LocalDateTime lastUpdated = null;

            // Check if latest TripLocation is present for this trip
            Optional<com.smartbus.domain.model.TripLocation> latestLoc = tripLocationRepository.findFirstByTripIdOrderByTimestampDesc(trip.getId());
            if (latestLoc.isPresent()) {
                com.smartbus.domain.model.TripLocation loc = latestLoc.get();
                if (loc.getLatitude() != null && loc.getLongitude() != null) {
                    lat = loc.getLatitude();
                    lng = loc.getLongitude();
                }
                if (loc.getSpeed() != null) speed = loc.getSpeed();
                if (loc.getHeading() != null) heading = loc.getHeading();
                if (loc.getAccuracy() != null) accuracy = loc.getAccuracy();
                if (loc.getTrackingSource() != null) trackingSource = loc.getTrackingSource();
                if (loc.getTimestamp() != null) lastUpdated = loc.getTimestamp();
            } else if (bus.getCurrentLatitude() != null && bus.getLastUpdated() != null &&
                       (trip.getStartTime() == null || !bus.getLastUpdated().isBefore(trip.getStartTime()))) {
                lat = bus.getCurrentLatitude();
                lng = bus.getCurrentLongitude();
                lastUpdated = bus.getLastUpdated();
            }

            boolean hasAcceptedGps = (lat != null && lng != null && lastUpdated != null);
            boolean isGpsStale = false;
            String gpsStatus = "UNAVAILABLE";

            if (hasAcceptedGps) {
                if (Duration.between(lastUpdated, LocalDateTime.now()).getSeconds() > 60) {
                    isGpsStale = true;
                    gpsStatus = "GPS_STALE";
                } else {
                    gpsStatus = "LIVE";
                }
            } else {
                lat = null;
                lng = null;
                gpsStatus = "UNAVAILABLE";
            }

            Integer etaMinutes = null;
            String etaStatus = "ON_TIME";
            Double distanceMeters = null;
            String nextStopName = null;
            Integer nextStopSeq = null;
            boolean offRoute = false;
            Double deviationMeters = null;
            Integer delayMins = 0;
            java.util.List<EtaResponse.UpcomingStopEta> upcomingStops = Collections.emptyList();
            EtaResponse.RouteProgressInfo routeProgress = null;

            try {
                com.smartbus.infrastructure.dto.EtaResponse eta = etaCalculationService.calculateLiveTripEta(trip.getId());
                if (eta != null) {
                    etaMinutes = eta.getMinutesRemaining();
                    etaStatus = eta.getStatus();
                    distanceMeters = eta.getDistanceMeters();
                    nextStopName = eta.getNextStopName();
                    nextStopSeq = eta.getNextStopSequence();
                    offRoute = eta.isOffRoute();
                    deviationMeters = eta.getRouteDeviationMeters();
                    delayMins = eta.getDelayMinutes();
                    if (!hasAcceptedGps) {
                        gpsStatus = "UNAVAILABLE";
                    } else if (eta.getGpsStatus() != null && !"UNAVAILABLE".equals(eta.getGpsStatus())) {
                        gpsStatus = eta.getGpsStatus();
                    }
                    if (eta.getUpcomingStops() != null) {
                        upcomingStops = eta.getUpcomingStops();
                    }
                    if (eta.getRouteProgress() != null) {
                        routeProgress = eta.getRouteProgress();
                    }
                }
            } catch (Exception e) {
                log.warn("ETA calculation failed for trip {}: {}", trip.getId(), e.getMessage());
            }

            if (nextStopSeq == null && trip.getRoute() != null) {
                int seq = (trip.getCurrentStopSequence() != null && trip.getCurrentStopSequence() >= 1)
                        ? trip.getCurrentStopSequence() : 1;
                nextStopSeq = seq;
                List<com.smartbus.domain.model.RouteStop> rStops = routeStopRepository.findByRouteIdOrderBySequenceNumberAsc(trip.getRoute().getId());
                for (com.smartbus.domain.model.RouteStop rs : rStops) {
                    if (rs.getSequenceNumber() == seq) {
                        nextStopName = rs.getStop().getStopName();
                        break;
                    }
                }
            }

            String driverName = trip.getDriver() != null && trip.getDriver().getUser() != null
                    ? trip.getDriver().getUser().getFirstName() + " " + trip.getDriver().getUser().getLastName()
                    : "Assigned Driver";

            Double startLat = null;
            Double startLng = null;
            Double endLat = null;
            Double endLng = null;
            String polyline = null;
            List<com.smartbus.infrastructure.dto.RouteStopDto> stops = Collections.emptyList();

            if (trip.getRoute() != null) {
                com.smartbus.domain.model.Route r = trip.getRoute();
                startLat = r.getStartLatitude() != null ? r.getStartLatitude().doubleValue() : null;
                startLng = r.getStartLongitude() != null ? r.getStartLongitude().doubleValue() : null;
                endLat = r.getEndLatitude() != null ? r.getEndLatitude().doubleValue() : null;
                endLng = r.getEndLongitude() != null ? r.getEndLongitude().doubleValue() : null;
                polyline = r.getPolyline();
                List<com.smartbus.domain.model.RouteStop> rStops = routeStopRepository.findByRouteIdOrderBySequenceNumberAsc(r.getId());
                stops = rStops.stream().map(routeStopMapper::toDto).collect(Collectors.toList());
            }

            fleet.add(com.smartbus.infrastructure.dto.ActiveBusFleetDto.builder()
                    .tripId(trip.getId())
                    .busId(bus.getId())
                    .busCode(bus.getBusCode())
                    .busNumber(bus.getBusNumber())
                    .routeId(trip.getRoute() != null ? trip.getRoute().getId() : null)
                    .routeName(trip.getRoute() != null ? trip.getRoute().getRouteName() : "N/A")
                    .driverId(trip.getDriver() != null ? trip.getDriver().getId() : null)
                    .driverName(driverName)
                    .tripStatus(trip.getStatus())
                    .latitude(lat)
                    .longitude(lng)
                    .speed(speed)
                    .heading(heading)
                    .accuracy(accuracy)
                    .trackingSource(trackingSource)
                    .lastUpdated(lastUpdated)
                    .etaMinutes(etaMinutes)
                    .etaStatus(etaStatus)
                    .distanceMeters(distanceMeters)
                    .nextStopName(nextStopName)
                    .nextStopSequence(nextStopSeq)
                    .offRoute(offRoute)
                    .routeDeviationMeters(deviationMeters)
                    .delayMinutes(delayMins)
                    .gpsStale(isGpsStale)
                    .gpsStatus(gpsStatus)
                    .upcomingStops(upcomingStops)
                    .routeProgress(routeProgress)
                    .startLatitude(startLat)
                    .startLongitude(startLng)
                    .endLatitude(endLat)
                    .endLongitude(endLng)
                    .polyline(polyline)
                    .stops(stops)
                    .build());
        }

        return ResponseEntity.ok(ApiResponse.success("Active fleet fetched successfully", fleet));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<BusDto>>> getAllBuses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "busNumber") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        Sort sort = Sort.by(Sort.Direction.fromString(direction), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        List<Bus> allBuses = busRepository.findByDeletedAtIsNull();
        if (userPrincipal != null && userPrincipal.getCollegeId() != null) {
            UUID collegeId = userPrincipal.getCollegeId();
            allBuses = allBuses.stream()
                    .filter(b -> b.getCollege() != null && collegeId.equals(b.getCollege().getId()))
                    .collect(Collectors.toList());
        }

        // Apply filters in memory for flexibility
        if (search != null && !search.trim().isEmpty()) {
            String lowerSearch = search.toLowerCase();
            allBuses = allBuses.stream()
                    .filter(b -> b.getBusNumber().toLowerCase().contains(lowerSearch) ||
                            (b.getBusCode() != null && b.getBusCode().toLowerCase().contains(lowerSearch)) ||
                            (b.getRegistrationNumber() != null && b.getRegistrationNumber().toLowerCase().contains(lowerSearch)))
                    .collect(Collectors.toList());
        }

        if (status != null && !status.trim().isEmpty()) {
            allBuses = allBuses.stream()
                    .filter(b -> status.equalsIgnoreCase(b.getStatus()))
                    .collect(Collectors.toList());
        }

        // Paginate in memory
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), allBuses.size());

        List<BusDto> content = new ArrayList<>();
        if (start <= allBuses.size()) {
            content = allBuses.subList(start, end).stream()
                    .map(this::mapToDto)
                    .collect(Collectors.toList());
        }

        Page<BusDto> pageResult = new PageImpl<>(content, pageable, allBuses.size());
        return ResponseEntity.ok(ApiResponse.success("Buses retrieved successfully", pageResult));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BusDto>> createBus(
            @RequestBody BusDto busDto,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        if (userPrincipal != null && userPrincipal.getCollegeId() != null) {
            if (busRepository.findByCollegeIdAndBusNumberAndDeletedAtIsNull(userPrincipal.getCollegeId(), busDto.getBusNumber()).isPresent()) {
                throw new BadRequestException("Bus number " + busDto.getBusNumber() + " already exists in this college");
            }
        } else if (busRepository.findByBusNumberAndDeletedAtIsNull(busDto.getBusNumber()).isPresent()) {
            throw new BadRequestException("Bus number " + busDto.getBusNumber() + " already exists");
        }

        if (busDto.getRegistrationNumber() != null &&
                busRepository.findByRegistrationNumberAndDeletedAtIsNull(busDto.getRegistrationNumber()).isPresent()) {
            throw new BadRequestException("Registration number " + busDto.getRegistrationNumber() + " already exists");
        }

        String busCode = generateUniqueBusCode();
        Bus bus = Bus.builder()
                .busNumber(busDto.getBusNumber())
                .registrationNumber(busDto.getRegistrationNumber())
                .manufacturer(busDto.getManufacturer())
                .manufacturingYear(busDto.getManufacturingYear())
                .busType(busDto.getBusType())
                .busCode(busCode)
                .model(busDto.getModel() != null ? busDto.getModel() : "Default Model")
                .capacity(busDto.getCapacity() > 0 ? busDto.getCapacity() : 40)
                .college(userPrincipal != null ? userPrincipal.getUser().getCollege() : null)
                .status("ACTIVE")
                .build();

        if (busDto.getGpsDeviceId() != null) {
            Optional<GpsDevice> device = gpsDeviceRepository.findById(UUID.fromString(busDto.getGpsDeviceId()));
            device.ifPresent(bus::setGpsDevice);
        }

        Bus savedBus = busRepository.save(bus);

        // Auto generate first QR token
        generateNewQrToken(savedBus);

        auditLogService.logAction(
                userPrincipal != null ? userPrincipal.getUser() : null,
                "BUS_CREATED",
                "Created bus: " + savedBus.getBusNumber() + " (Code: " + busCode + ")",
                "0.0.0.0",
                "Bus",
                savedBus.getId().toString(),
                null,
                savedBus.getBusNumber()
        );

        return ResponseEntity.ok(ApiResponse.success("Bus created successfully", mapToDto(savedBus)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BusDto>> getBusById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Bus bus = busRepository.findById(id)
                .filter(b -> b.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Bus not found with id: " + id));
        validateBusBelongsToCollege(bus, userPrincipal);
        return ResponseEntity.ok(ApiResponse.success("Bus retrieved successfully", mapToDto(bus)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BusDto>> updateBus(
            @PathVariable UUID id,
            @RequestBody BusDto busDto,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        Bus bus = busRepository.findById(id)
                .filter(b -> b.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Bus not found with id: " + id));
        validateBusBelongsToCollege(bus, userPrincipal);

        // Duplicate validations
        if (userPrincipal != null && userPrincipal.getCollegeId() != null) {
            busRepository.findByCollegeIdAndBusNumberAndDeletedAtIsNull(userPrincipal.getCollegeId(), busDto.getBusNumber())
                    .ifPresent(existing -> {
                        if (!existing.getId().equals(id)) {
                            throw new BadRequestException("Bus number already exists in this college");
                        }
                    });
        } else {
            busRepository.findByBusNumberAndDeletedAtIsNull(busDto.getBusNumber())
                    .ifPresent(existing -> {
                        if (!existing.getId().equals(id)) {
                            throw new BadRequestException("Bus number already exists");
                        }
                    });
        }

        if (busDto.getRegistrationNumber() != null) {
            busRepository.findByRegistrationNumberAndDeletedAtIsNull(busDto.getRegistrationNumber())
                    .ifPresent(existing -> {
                        if (!existing.getId().equals(id)) {
                            throw new BadRequestException("Registration number already exists");
                        }
                    });
        }

        String oldValue = bus.getBusNumber() + " - " + bus.getStatus();

        bus.setBusNumber(busDto.getBusNumber());
        bus.setRegistrationNumber(busDto.getRegistrationNumber());
        bus.setManufacturer(busDto.getManufacturer());
        bus.setManufacturingYear(busDto.getManufacturingYear());
        bus.setBusType(busDto.getBusType());
        bus.setModel(busDto.getModel());
        bus.setCapacity(busDto.getCapacity());
        if (busDto.getStatus() != null && !busDto.getStatus().trim().isEmpty()) {
            bus.setStatus(busDto.getStatus().toUpperCase());
        }

        if (busDto.getGpsDeviceId() != null && !busDto.getGpsDeviceId().isEmpty()) {
            GpsDevice device = gpsDeviceRepository.findById(UUID.fromString(busDto.getGpsDeviceId()))
                    .orElseThrow(() -> new ResourceNotFoundException("GPS device not found"));
            bus.setGpsDevice(device);
        } else {
            bus.setGpsDevice(null);
        }

        Bus savedBus = busRepository.save(bus);

        auditLogService.logAction(
                userPrincipal != null ? userPrincipal.getUser() : null,
                "BUS_UPDATED",
                "Updated bus: " + savedBus.getBusNumber(),
                "0.0.0.0",
                "Bus",
                savedBus.getId().toString(),
                oldValue,
                savedBus.getBusNumber() + " - " + savedBus.getStatus()
        );

        return ResponseEntity.ok(ApiResponse.success("Bus updated successfully", mapToDto(savedBus)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<BusDto>> updateStatus(
            @PathVariable UUID id,
            @RequestParam String status,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        Bus bus = busRepository.findById(id)
                .filter(b -> b.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Bus not found with id: " + id));
        validateBusBelongsToCollege(bus, userPrincipal);

        String oldStatus = bus.getStatus();
        bus.setStatus(status.toUpperCase());
        Bus savedBus = busRepository.save(bus);

        auditLogService.logAction(
                userPrincipal != null ? userPrincipal.getUser() : null,
                "BUS_STATUS_CHANGED",
                "Changed status of bus " + bus.getBusNumber() + " from " + oldStatus + " to " + status,
                "0.0.0.0",
                "Bus",
                bus.getId().toString(),
                oldStatus,
                status
        );

        return ResponseEntity.ok(ApiResponse.success("Bus status updated to " + status, mapToDto(savedBus)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteBus(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        Bus bus = busRepository.findById(id)
                .filter(b -> b.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Bus not found with id: " + id));
        validateBusBelongsToCollege(bus, userPrincipal);

        // Check active trips
        Optional<Trip> activeTrip = tripRepository.findByBusIdAndStatusIn(id, Arrays.asList("EN_ROUTE", "IN_PROGRESS", "PAUSED"));
        if (activeTrip.isPresent()) {
            throw new BadRequestException("This bus cannot be deleted because it is currently on an active trip.");
        }

        // Check active schedules
        List<Schedule> activeSchedules = scheduleRepository.findByBusIdAndDeletedAtIsNull(id);
        if (!activeSchedules.isEmpty()) {
            throw new BadRequestException("This bus cannot be deleted because it is assigned to an active schedule.");
        }

        bus.setDeletedAt(LocalDateTime.now());
        bus.setStatus("INACTIVE");
        busRepository.save(bus);

        auditLogService.logAction(
                userPrincipal != null ? userPrincipal.getUser() : null,
                "BUS_DELETED",
                "Soft-deleted bus: " + bus.getBusNumber(),
                "0.0.0.0",
                "Bus",
                bus.getId().toString(),
                bus.getBusNumber(),
                "DELETED"
        );

        return ResponseEntity.ok(ApiResponse.success("Bus deleted successfully"));
    }

    @GetMapping("/{id}/qr")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getQrCode(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Bus bus = busRepository.findById(id)
                .filter(b -> b.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Bus not found with id: " + id));
        validateBusBelongsToCollege(bus, userPrincipal);

        BusQrToken activeToken = busQrTokenRepository.findByBusIdAndIsActiveTrue(id)
                .orElseGet(() -> generateNewQrToken(bus));

        String payload = "smartbus://bus/" + bus.getBusCode() + "/" + activeToken.getToken();

        Map<String, Object> data = new HashMap<>();
        data.put("busCode", bus.getBusCode());
        data.put("token", activeToken.getToken());
        data.put("payload", payload);
        data.put("createdAt", activeToken.getCreatedAt());

        return ResponseEntity.ok(ApiResponse.success("QR Token retrieved successfully", data));
    }

    @PostMapping("/{id}/qr/regenerate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> regenerateQr(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        Bus bus = busRepository.findById(id)
                .filter(b -> b.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Bus not found with id: " + id));
        validateBusBelongsToCollege(bus, userPrincipal);

        // Revoke active token
        busQrTokenRepository.findByBusIdAndIsActiveTrue(id).ifPresent(token -> {
            token.setActive(false);
            token.setRevokedAt(LocalDateTime.now());
            busQrTokenRepository.save(token);
        });

        // Generate new token
        BusQrToken newToken = generateNewQrToken(bus);
        String payload = "smartbus://bus/" + bus.getBusCode() + "/" + newToken.getToken();

        auditLogService.logAction(
                userPrincipal != null ? userPrincipal.getUser() : null,
                "QR_REGENERATED",
                "Regenerated QR token for bus " + bus.getBusNumber(),
                "0.0.0.0",
                "Bus",
                bus.getId().toString(),
                null,
                newToken.getToken()
        );

        Map<String, Object> data = new HashMap<>();
        data.put("busCode", bus.getBusCode());
        data.put("token", newToken.getToken());
        data.put("payload", payload);
        data.put("createdAt", newToken.getCreatedAt());

        return ResponseEntity.ok(ApiResponse.success("QR Token regenerated successfully", data));
    }

    private BusQrToken generateNewQrToken(Bus bus) {
        String secureToken = UUID.randomUUID().toString().replace("-", "");
        BusQrToken token = BusQrToken.builder()
                .bus(bus)
                .token(secureToken)
                .isActive(true)
                .build();
        return busQrTokenRepository.save(token);
    }

    private String generateUniqueBusCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        SecureRandom random = new SecureRandom();
        String code;
        do {
            StringBuilder sb = new StringBuilder("SB-BUS-");
            for (int i = 0; i < 6; i++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            code = sb.toString();
        } while (busRepository.findByBusCodeAndDeletedAtIsNull(code).isPresent());
        return code;
    }

    private BusDto mapToDto(Bus bus) {
        return BusDto.builder()
                .id(bus.getId())
                .busNumber(bus.getBusNumber())
                .registrationNumber(bus.getRegistrationNumber())
                .manufacturer(bus.getManufacturer())
                .manufacturingYear(bus.getManufacturingYear())
                .busType(bus.getBusType())
                .busCode(bus.getBusCode())
                .gpsDeviceId(bus.getGpsDevice() != null ? bus.getGpsDevice().getId().toString() : null)
                .gpsDeviceStatus(bus.getGpsDevice() != null ? bus.getGpsDevice().getStatus() : "NOT_CONFIGURED")
                .model(bus.getModel())
                .capacity(bus.getCapacity())
                .status(bus.getStatus())
                .currentLatitude(bus.getCurrentLatitude())
                .currentLongitude(bus.getCurrentLongitude())
                .lastUpdated(bus.getLastUpdated())
                .build();
    }

    private void validateBusBelongsToCollege(Bus bus, UserPrincipal userPrincipal) {
        if (userPrincipal != null && userPrincipal.getCollegeId() != null) {
            if (bus.getCollege() == null || !userPrincipal.getCollegeId().equals(bus.getCollege().getId())) {
                throw new ResourceNotFoundException("Bus not found with id: " + bus.getId());
            }
        }
    }
}
