package com.smartbus.application.service;

import com.smartbus.application.port.in.TripUseCase;
import com.smartbus.domain.exception.BadRequestException;
import com.smartbus.domain.exception.ResourceNotFoundException;
import com.smartbus.domain.model.Bus;
import com.smartbus.domain.model.Driver;
import com.smartbus.domain.model.Schedule;
import com.smartbus.domain.model.Trip;
import com.smartbus.infrastructure.adapter.jpa.BusRepository;
import com.smartbus.infrastructure.adapter.jpa.DriverRepository;
import com.smartbus.infrastructure.adapter.jpa.ScheduleRepository;
import com.smartbus.infrastructure.adapter.jpa.TripRepository;
import com.smartbus.infrastructure.dto.TripDto;
import com.smartbus.infrastructure.mapper.TripMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class TripService implements TripUseCase {

    private final ScheduleRepository scheduleRepository;
    private final TripRepository tripRepository;
    private final DriverRepository driverRepository;
    private final BusRepository busRepository;
    private final TripMapper tripMapper;
    private final GpsService gpsService;

    @Override
    @Transactional(readOnly = true)
    public TripDto getTripById(UUID id) {
        Trip trip = tripRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with id: " + id));
        return tripMapper.toDto(trip);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TripDto> getActiveTrips() {
        return tripRepository.findActiveTrips().stream()
                .map(tripMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public TripDto startTrip(UUID scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .filter(s -> s.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found with id: " + scheduleId));

        Driver driver = schedule.getDriver();
        Bus bus = schedule.getBus();

        // Check if driver is already on a trip
        tripRepository.findByDriverIdAndStatusIn(driver.getId(), Arrays.asList("EN_ROUTE", "PAUSED"))
                .ifPresent(t -> {
                    throw new BadRequestException("Driver is already on an active trip: " + t.getId());
                });

        // Check if bus is already on a trip
        tripRepository.findByBusIdAndStatusIn(bus.getId(), Arrays.asList("EN_ROUTE", "PAUSED"))
                .ifPresent(t -> {
                    throw new BadRequestException("Bus is already on an active trip: " + t.getId());
                });

        Trip trip = Trip.builder()
                .schedule(schedule)
                .bus(bus)
                .driver(driver)
                .route(schedule.getRoute())
                .status("EN_ROUTE")
                .startTime(LocalDateTime.now())
                .actualDeparture(LocalDateTime.now())
                .currentStopSequence(1)
                .build();

        driver.setStatus("ON_TRIP");
        driverRepository.save(driver);

        Trip savedTrip = tripRepository.save(trip);
        return tripMapper.toDto(savedTrip);
    }

    @Override
    public TripDto pauseTrip(UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with id: " + tripId));
        if (!"EN_ROUTE".equals(trip.getStatus())) {
            throw new BadRequestException("Trip cannot be paused as it is in status: " + trip.getStatus());
        }
        trip.setStatus("PAUSED");
        Trip savedTrip = tripRepository.save(trip);
        return tripMapper.toDto(savedTrip);
    }

    @Override
    public TripDto resumeTrip(UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with id: " + tripId));
        if (!"PAUSED".equals(trip.getStatus())) {
            throw new BadRequestException("Trip cannot be resumed as it is in status: " + trip.getStatus());
        }
        trip.setStatus("EN_ROUTE");
        Trip savedTrip = tripRepository.save(trip);
        return tripMapper.toDto(savedTrip);
    }

    @Override
    public TripDto endTrip(UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with id: " + tripId));
        
        if ("COMPLETED".equals(trip.getStatus()) || "CANCELLED".equals(trip.getStatus())) {
            throw new BadRequestException("Trip is already finished: " + trip.getStatus());
        }

        trip.setStatus("COMPLETED");
        trip.setEndTime(LocalDateTime.now());
        trip.setActualArrival(LocalDateTime.now());

        Driver driver = trip.getDriver();
        driver.setStatus("AVAILABLE");
        driverRepository.save(driver);

        gpsService.clearTripCache(tripId);

        Trip savedTrip = tripRepository.save(trip);
        return tripMapper.toDto(savedTrip);
    }

    @Override
    @Transactional(readOnly = true)
    public TripDto getActiveTripByDriver(UUID driverId) {
        Trip trip = tripRepository.findByDriverIdAndStatusIn(driverId, Arrays.asList("EN_ROUTE", "PAUSED"))
                .orElseThrow(() -> new ResourceNotFoundException("No active trip found for driver with id: " + driverId));
        return tripMapper.toDto(trip);
    }
}
