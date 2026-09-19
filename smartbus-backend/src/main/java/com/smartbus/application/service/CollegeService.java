package com.smartbus.application.service;

import com.smartbus.domain.model.*;
import com.smartbus.infrastructure.adapter.jpa.*;
import com.smartbus.infrastructure.dto.CollegeDto;
import com.smartbus.infrastructure.dto.PlatformMetricsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CollegeService {

    private final CollegeRepository collegeRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final DriverRepository driverRepository;
    private final BusRepository busRepository;
    private final RouteRepository routeRepository;
    private final TripRepository tripRepository;

    @Transactional(readOnly = true)
    public List<CollegeDto> getAllColleges() {
        return collegeRepository.findAll().stream()
                .map(this::mapToDtoWithStats)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CollegeDto getCollegeById(UUID id) {
        College college = collegeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("College not found with id: " + id));
        return mapToDtoWithStats(college);
    }

    @Transactional(readOnly = true)
    public CollegeDto getCollegeForAdmin(UUID collegeId) {
        if (collegeId == null) {
            throw new IllegalArgumentException("Admin is not associated with a college tenant");
        }
        College college = collegeRepository.findById(collegeId)
                .orElseThrow(() -> new IllegalArgumentException("College not found for tenant: " + collegeId));
        return mapToDtoWithStats(college);
    }

    @Transactional
    public CollegeDto updateCollegeCodeForAdmin(UUID collegeId, String newCollegeCode) {
        if (collegeId == null) {
            throw new IllegalArgumentException("Admin is not associated with a college tenant");
        }
        if (newCollegeCode == null || newCollegeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("College code cannot be blank");
        }

        String cleanCode = newCollegeCode.trim().toUpperCase();
        if (!cleanCode.matches("^[A-Za-z0-9_-]{2,20}$")) {
            throw new IllegalArgumentException("College code must be between 2 and 20 characters (alphanumeric, dashes, underscores only)");
        }

        if (collegeRepository.existsByCollegeCodeIgnoreCaseAndIdNot(cleanCode, collegeId)) {
            throw new IllegalArgumentException("College code already in use by another college: " + cleanCode);
        }

        College college = collegeRepository.findById(collegeId)
                .orElseThrow(() -> new IllegalArgumentException("College not found with id: " + collegeId));

        college.setCollegeCode(cleanCode);
        college = collegeRepository.save(college);
        log.info("Admin updated college code for college '{}' ({}) to: {}", college.getName(), college.getId(), cleanCode);

        return mapToDtoWithStats(college);
    }

    @Transactional(readOnly = true)
    public PlatformMetricsDto getPlatformMetrics() {
        long totalColleges = collegeRepository.count();
        long activeColleges = collegeRepository.findAll().stream()
                .filter(c -> "ACTIVE".equalsIgnoreCase(c.getStatus()))
                .count();
        long disabledColleges = totalColleges - activeColleges;
        long totalStudents = studentRepository.count();
        long totalDrivers = driverRepository.count();
        long totalBuses = busRepository.countByDeletedAtIsNull();

        return PlatformMetricsDto.builder()
                .totalColleges(totalColleges)
                .activeColleges(activeColleges)
                .disabledColleges(disabledColleges)
                .totalStudents(totalStudents)
                .totalDrivers(totalDrivers)
                .totalBuses(totalBuses)
                .build();
    }

    @Transactional
    private CollegeDto mapToDtoWithStats(College c) {
        UUID collegeId = c.getId();
        long studentCount = studentRepository.countByCollegeId(collegeId);
        long driverCount = driverRepository.countByCollegeId(collegeId);
        long busCount = busRepository.countByCollegeIdAndDeletedAtIsNull(collegeId);
        long routeCount = routeRepository.countByCollegeIdAndDeletedAtIsNull(collegeId);
        long activeTrips = tripRepository.countByCollegeIdAndStatusIn(collegeId, List.of("EN_ROUTE", "PAUSED"));

        return CollegeDto.builder()
                .id(c.getId())
                .name(c.getName())
                .collegeCode(c.getCollegeCode())
                .status(c.getStatus())
                .logoUrl(c.getLogoUrl())
                .contactEmail(c.getContactEmail())
                .studentCount(studentCount)
                .driverCount(driverCount)
                .busCount(busCount)
                .routeCount(routeCount)
                .activeTripCount(activeTrips)
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
