package com.smartbus.application.service;

import com.smartbus.application.port.in.RouteUseCase;
import com.smartbus.domain.exception.ResourceNotFoundException;
import com.smartbus.domain.model.Route;
import com.smartbus.domain.model.RouteStop;
import com.smartbus.domain.model.Stop;
import com.smartbus.infrastructure.adapter.jpa.RouteRepository;
import com.smartbus.infrastructure.adapter.jpa.RouteStopRepository;
import com.smartbus.infrastructure.adapter.jpa.StopRepository;
import com.smartbus.infrastructure.dto.RouteDto;
import com.smartbus.infrastructure.dto.RouteStopDto;
import com.smartbus.infrastructure.dto.StopDto;
import com.smartbus.infrastructure.mapper.RouteMapper;
import com.smartbus.infrastructure.mapper.RouteStopMapper;
import com.smartbus.infrastructure.mapper.StopMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class RouteService implements RouteUseCase {

    private final RouteRepository routeRepository;
    private final RouteStopRepository routeStopRepository;
    private final StopRepository stopRepository;
    
    private final RouteMapper routeMapper;
    private final RouteStopMapper routeStopMapper;
    private final StopMapper stopMapper;

    @Override
    @Transactional(readOnly = true)
    public RouteDto getRouteById(UUID id) {
        Route route = routeRepository.findById(id)
                .filter(r -> r.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found with id: " + id));
        RouteDto dto = routeMapper.toDto(route);
        dto.setStops(getRouteStops(route));
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RouteDto> getAllRoutes() {
        return routeRepository.findByDeletedAtIsNull().stream()
                .map(route -> {
                    RouteDto dto = routeMapper.toDto(route);
                    dto.setStops(getRouteStops(route));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private List<RouteStopDto> getRouteStops(Route route) {
        return routeStopRepository.findByRouteOrderBySequenceNumberAsc(route).stream()
                .map(routeStopMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public RouteDto createRoute(RouteDto routeDto) {
        Route route = routeMapper.toEntity(routeDto);
        Route savedRoute = routeRepository.save(route);
        return routeMapper.toDto(savedRoute);
    }

    @Override
    public RouteDto updateRoute(UUID id, RouteDto routeDto) {
        Route route = routeRepository.findById(id)
                .filter(r -> r.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found with id: " + id));
        
        route.setRouteName(routeDto.getRouteName());
        route.setStartPoint(routeDto.getStartPoint());
        route.setEndPoint(routeDto.getEndPoint());
        route.setDistance(routeDto.getDistance());
        route.setEstimatedDurationMins(routeDto.getEstimatedDurationMins());
        
        Route updatedRoute = routeRepository.save(route);
        return routeMapper.toDto(updatedRoute);
    }

    @Override
    public void deleteRoute(UUID id) {
        Route route = routeRepository.findById(id)
                .filter(r -> r.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found with id: " + id));
        route.setDeletedAt(LocalDateTime.now());
        routeRepository.save(route);
    }

    @Override
    @Transactional(readOnly = true)
    public StopDto getStopById(UUID id) {
        Stop stop = stopRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stop not found with id: " + id));
        return stopMapper.toDto(stop);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StopDto> getAllStops() {
        return stopRepository.findAll().stream()
                .map(stopMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public StopDto createStop(StopDto stopDto) {
        Stop stop = stopMapper.toEntity(stopDto);
        Stop savedStop = stopRepository.save(stop);
        return stopMapper.toDto(savedStop);
    }

    @Override
    public void deleteStop(UUID id) {
        Stop stop = stopRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stop not found with id: " + id));
        stopRepository.delete(stop);
    }

    @Override
    public void addStopToRoute(UUID routeId, UUID stopId, int sequenceNumber, Double distanceFromStart, int durationFromStartMins) {
        Route route = routeRepository.findById(routeId)
                .filter(r -> r.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found with id: " + routeId));
        Stop stop = stopRepository.findById(stopId)
                .orElseThrow(() -> new ResourceNotFoundException("Stop not found with id: " + stopId));

        RouteStop routeStop = RouteStop.builder()
                .route(route)
                .stop(stop)
                .sequenceNumber(sequenceNumber)
                .distanceFromStart(distanceFromStart)
                .durationFromStartMins(durationFromStartMins)
                .build();
        routeStopRepository.save(routeStop);
    }
}
