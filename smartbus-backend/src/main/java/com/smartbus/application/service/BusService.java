package com.smartbus.application.service;

import com.smartbus.application.port.in.BusUseCase;
import com.smartbus.domain.exception.ResourceNotFoundException;
import com.smartbus.domain.model.Bus;
import com.smartbus.infrastructure.adapter.jpa.BusRepository;
import com.smartbus.infrastructure.dto.BusDto;
import com.smartbus.infrastructure.mapper.BusMapper;
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
public class BusService implements BusUseCase {

    private final BusRepository busRepository;
    private final BusMapper busMapper;

    @Override
    @Transactional(readOnly = true)
    public BusDto getBusById(UUID id) {
        Bus bus = busRepository.findById(id)
                .filter(b -> b.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Bus not found with id: " + id));
        return busMapper.toDto(bus);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BusDto> getAllBuses() {
        return busRepository.findByDeletedAtIsNull().stream()
                .map(busMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public BusDto createBus(BusDto busDto) {
        Bus bus = busMapper.toEntity(busDto);
        bus.setStatus("ACTIVE");
        Bus savedBus = busRepository.save(bus);
        return busMapper.toDto(savedBus);
    }

    @Override
    public BusDto updateBus(UUID id, BusDto busDto) {
        Bus bus = busRepository.findById(id)
                .filter(b -> b.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Bus not found with id: " + id));
        
        bus.setBusNumber(busDto.getBusNumber());
        bus.setModel(busDto.getModel());
        bus.setCapacity(busDto.getCapacity());
        if (busDto.getStatus() != null) {
            bus.setStatus(busDto.getStatus());
        }
        
        Bus updatedBus = busRepository.save(bus);
        return busMapper.toDto(updatedBus);
    }

    @Override
    public void deleteBus(UUID id) {
        Bus bus = busRepository.findById(id)
                .filter(b -> b.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Bus not found with id: " + id));
        bus.setDeletedAt(LocalDateTime.now());
        busRepository.save(bus);
    }

    @Override
    public void updateBusLocation(UUID id, Double latitude, Double longitude) {
        Bus bus = busRepository.findById(id)
                .filter(b -> b.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Bus not found with id: " + id));
        bus.setCurrentLatitude(latitude);
        bus.setCurrentLongitude(longitude);
        bus.setLastUpdated(LocalDateTime.now());
        busRepository.save(bus);
    }

    @Override
    public void updateBusStatus(UUID id, String status) {
        Bus bus = busRepository.findById(id)
                .filter(b -> b.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Bus not found with id: " + id));
        bus.setStatus(status);
        busRepository.save(bus);
    }
}
