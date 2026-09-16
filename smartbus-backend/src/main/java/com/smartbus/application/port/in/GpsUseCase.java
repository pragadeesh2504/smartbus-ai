package com.smartbus.application.port.in;

import com.smartbus.infrastructure.dto.BusDto;
import java.util.UUID;

public interface GpsUseCase {
    void processLocationUpdate(UUID tripId, Double latitude, Double longitude, Double speed, Double heading);
}
