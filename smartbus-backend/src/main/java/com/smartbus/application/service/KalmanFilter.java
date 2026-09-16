package com.smartbus.application.service;

public class KalmanFilter {
    private final double q; // Process noise covariance
    private final double r; // Measurement noise covariance
    private double p;       // Estimation error covariance
    private double x;       // Estimated value
    private boolean initialized = false;

    public KalmanFilter(double q, double r) {
        this.q = q;
        this.r = r;
        this.p = 1.0;
    }

    public double filter(double measurement) {
        if (!initialized) {
            this.x = measurement;
            this.initialized = true;
            return measurement;
        }

        // Prediction update
        p = p + q;

        // Measurement update
        double k = p / (p + r); // Kalman gain
        x = x + k * (measurement - x);
        p = (1.0 - k) * p;

        return x;
    }
}
