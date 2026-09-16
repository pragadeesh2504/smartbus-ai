package com.smartbus.application.port.in;

import com.smartbus.infrastructure.dto.BusDto;
import java.util.List;
import java.util.UUID;

public interface BusUseCase {
    BusDto getBusById(UUID id);
    List<BusDto> getAllBuses();
    BusDto createBus(BusDto busDto);
    BusDto updateBus(UUID id, BusDto busDto);
    void deleteBus(UUID id);
    void updateBusLocation(UUID id, Double latitude, Double longitude);
    void updateBusStatus(UUID id, String status);
}
