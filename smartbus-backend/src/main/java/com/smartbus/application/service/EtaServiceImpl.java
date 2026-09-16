package com.smartbus.application.service;

import com.smartbus.infrastructure.dto.EtaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EtaServiceImpl implements EtaService {

    private final EtaCalculationService etaCalculationService;

    @Override
    public int calculateEtaMinutes(UUID tripId, UUID stopId) {
        try {
            EtaResponse res = etaCalculationService.calculateEta(tripId, stopId);
            return res.getMinutesRemaining() != null ? res.getMinutesRemaining() : -1;
        } catch (Exception e) {
            // Return -1 to represent ETA unavailable
            return -1;
        }
    }
}
