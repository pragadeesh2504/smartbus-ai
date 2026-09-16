package com.smartbus.application.port.in;

import com.smartbus.infrastructure.dto.RouteDto;
import com.smartbus.infrastructure.dto.StopDto;
import java.util.List;
import java.util.UUID;

public interface RouteUseCase {
    RouteDto getRouteById(UUID id);
    List<RouteDto> getAllRoutes();
    RouteDto createRoute(RouteDto routeDto);
    RouteDto updateRoute(UUID id, RouteDto routeDto);
    void deleteRoute(UUID id);

    StopDto getStopById(UUID id);
    List<StopDto> getAllStops();
    StopDto createStop(StopDto stopDto);
    void deleteStop(UUID id);

    void addStopToRoute(UUID routeId, UUID stopId, int sequenceNumber, Double distanceFromStart, int durationFromStartMins);
}
