package com.smartbus.application.service;

import com.smartbus.domain.exception.BadRequestException;

import java.time.LocalDateTime;

public final class GpsValidationUtil {

    private GpsValidationUtil() {
        // Utility class
    }

    /**
     * Validates raw GPS coordinates and telemetry.
     * Throws BadRequestException if any parameter is invalid.
     */
    public static void validateTelemetry(Double latitude, Double longitude, Double speed, Double heading) {
        validateLatitude(latitude);
        validateLongitude(longitude);
        validateSpeed(speed);
        validateHeading(heading);
    }

    public static void validateLatitude(Double latitude) {
        if (latitude == null) {
            throw new BadRequestException("Latitude is required and cannot be null");
        }
        if (latitude.isNaN() || latitude.isInfinite()) {
            throw new BadRequestException("Latitude cannot be NaN or Infinite");
        }
        if (latitude < -90.0 || latitude > 90.0) {
            throw new BadRequestException("Latitude must be between -90.0 and 90.0, received: " + latitude);
        }
    }

    public static void validateLongitude(Double longitude) {
        if (longitude == null) {
            throw new BadRequestException("Longitude is required and cannot be null");
        }
        if (longitude.isNaN() || longitude.isInfinite()) {
            throw new BadRequestException("Longitude cannot be NaN or Infinite");
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new BadRequestException("Longitude must be between -180.0 and 180.0, received: " + longitude);
        }
    }

    public static void validateSpeed(Double speed) {
        if (speed == null) {
            return; // speed is optional in some contexts, defaults to 0
        }
        if (speed.isNaN() || speed.isInfinite()) {
            throw new BadRequestException("Speed cannot be NaN or Infinite");
        }
        if (speed < 0.0 || speed > 160.0) {
            throw new BadRequestException("Speed must be between 0.0 and 160.0 km/h, received: " + speed);
        }
    }

    public static void validateHeading(Double heading) {
        if (heading == null) {
            return; // optional heading
        }
        if (heading.isNaN() || heading.isInfinite()) {
            throw new BadRequestException("Heading cannot be NaN or Infinite");
        }
        if (heading < 0.0 || heading >= 360.0) {
            throw new BadRequestException("Heading must be between 0.0 and 360.0 degrees [0 <= heading < 360], received: " + heading);
        }
    }

    public static void validateAccuracy(Double accuracy) {
        if (accuracy == null) {
            return;
        }
        if (accuracy.isNaN() || accuracy.isInfinite()) {
            throw new BadRequestException("Accuracy cannot be NaN or Infinite");
        }
        if (accuracy < 0.0) {
            throw new BadRequestException("Accuracy must be positive, received: " + accuracy);
        }
    }

    public static void validateTimestamp(LocalDateTime timestamp) {
        if (timestamp == null) {
            return;
        }
        if (timestamp.isAfter(LocalDateTime.now().plusMinutes(5))) {
            throw new BadRequestException("Timestamp cannot be in the future");
        }
    }
}
