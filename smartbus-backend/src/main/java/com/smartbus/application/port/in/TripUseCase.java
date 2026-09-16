package com.smartbus.application.port.in;

import com.smartbus.infrastructure.dto.TripDto;
import java.util.List;
import java.util.UUID;

public interface TripUseCase {
    TripDto getTripById(UUID id);
    List<TripDto> getActiveTrips();
    TripDto startTrip(UUID scheduleId);
    TripDto pauseTrip(UUID tripId);
    TripDto resumeTrip(UUID tripId);
    TripDto endTrip(UUID tripId);
    TripDto getActiveTripByDriver(UUID driverId);
}
