package com.smartbus.application.service;

import com.smartbus.domain.model.PassengerCount;
import com.smartbus.domain.model.Trip;
import com.smartbus.infrastructure.adapter.jpa.PassengerCountRepository;
import com.smartbus.infrastructure.adapter.jpa.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DemandForecastingService {

    private final PassengerCountRepository passengerCountRepository;
    private final TripRepository tripRepository;

    /**
     * Forecasts passenger demand (count) for a specific route and hour of day.
     */
    public int forecastDemand(UUID routeId, int hourOfDay) {
        // Query historical trips for this route
        List<Trip> historicalTrips = tripRepository.findByStatus("COMPLETED");
        
        double totalCount = 0;
        int matchedRecords = 0;

        for (Trip trip : historicalTrips) {
            if (trip.getRoute().getId().equals(routeId)) {
                // Find passenger counts for this trip recorded around the target hour
                List<PassengerCount> counts = passengerCountRepository.findByTripId(trip.getId());
                for (PassengerCount pc : counts) {
                    if (pc.getRecordedAt().getHour() == hourOfDay) {
                        totalCount += pc.getCount();
                        matchedRecords++;
                    }
                }
            }
        }

        // Base fallback demand: 25 passengers
        double baseDemand = matchedRecords > 0 ? (totalCount / matchedRecords) : 25.0;

        // Day of Week adjustments (e.g., Mondays and Fridays usually see 20% higher demand)
        DayOfWeek today = LocalDate.now().getDayOfWeek();
        if (today == DayOfWeek.MONDAY || today == DayOfWeek.FRIDAY) {
            baseDemand *= 1.20;
        }

        // Time of Day peak adjustments (Morning commute 8-10 AM, Evening commute 4-6 PM)
        if (hourOfDay >= 8 && hourOfDay <= 10) {
            baseDemand *= 1.40; // 40% passenger increase
        } else if (hourOfDay >= 16 && hourOfDay <= 18) {
            baseDemand *= 1.30;
        }

        return (int) Math.round(baseDemand);
    }
}
