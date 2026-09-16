package com.smartbus.application.service;

import java.util.UUID;

public interface EtaService {
    int calculateEtaMinutes(UUID tripId, UUID stopId);
}
