package com.smartbus.infrastructure.controller;

import com.smartbus.domain.exception.BadRequestException;
import com.smartbus.domain.exception.ResourceNotFoundException;
import com.smartbus.domain.model.GpsDevice;
import com.smartbus.infrastructure.adapter.jpa.GpsDeviceRepository;
import com.smartbus.infrastructure.dto.ApiResponse;
import com.smartbus.infrastructure.dto.GpsDeviceDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/gps-devices")
@RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
public class AdminGpsDeviceController {

    private final GpsDeviceRepository gpsDeviceRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<GpsDeviceDto>>> getAllDevices() {
        List<GpsDevice> devices = gpsDeviceRepository.findAll();
        List<GpsDeviceDto> dtos = devices.stream().map(this::mapToDto).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("GPS devices loaded", dtos));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<GpsDeviceDto>> createDevice(@RequestBody GpsDeviceDto dto) {
        if (gpsDeviceRepository.findByDeviceId(dto.getDeviceId()).isPresent()) {
            throw new BadRequestException("Device ID already exists");
        }

        GpsDevice device = GpsDevice.builder()
                .deviceId(dto.getDeviceId())
                .imei(dto.getImei())
                .simIdentifier(dto.getSimIdentifier())
                .provider(dto.getProvider())
                .status("NOT_CONFIGURED")
                .batteryLevel(100)
                .firmwareVersion(dto.getFirmwareVersion() != null ? dto.getFirmwareVersion() : "1.0.0")
                .build();

        GpsDevice saved = gpsDeviceRepository.save(device);
        return ResponseEntity.ok(ApiResponse.success("GPS device registered successfully", mapToDto(saved)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<GpsDeviceDto>> updateDevice(@PathVariable UUID id, @RequestBody GpsDeviceDto dto) {
        GpsDevice device = gpsDeviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("GPS device not found"));

        device.setImei(dto.getImei());
        device.setSimIdentifier(dto.getSimIdentifier());
        device.setProvider(dto.getProvider());
        if (dto.getStatus() != null) {
            device.setStatus(dto.getStatus().toUpperCase());
        }
        device.setFirmwareVersion(dto.getFirmwareVersion());

        GpsDevice saved = gpsDeviceRepository.save(device);
        return ResponseEntity.ok(ApiResponse.success("GPS device updated successfully", mapToDto(saved)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDevice(@PathVariable UUID id) {
        GpsDevice device = gpsDeviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("GPS device not found"));

        gpsDeviceRepository.delete(device);
        return ResponseEntity.ok(ApiResponse.success("GPS device deleted successfully"));
    }

    private GpsDeviceDto mapToDto(GpsDevice device) {
        return GpsDeviceDto.builder()
                .id(device.getId())
                .deviceId(device.getDeviceId())
                .imei(device.getImei())
                .simIdentifier(device.getSimIdentifier())
                .provider(device.getProvider())
                .status(device.getStatus())
                .lastSeen(device.getLastSeen())
                .batteryLevel(device.getBatteryLevel())
                .firmwareVersion(device.getFirmwareVersion())
                .build();
    }
}
