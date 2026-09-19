package com.smartbus.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;

@Entity
@Table(name = "students", uniqueConstraints = {
    @UniqueConstraint(name = "uq_students_college_student_id", columnNames = {"college_id", "student_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "college_id")
    private College college;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "student_id", nullable = false)
    private String studentId;

    @Column(nullable = false)
    private String department;

    @Column(nullable = false)
    private String batch;

    @Column(name = "register_number")
    private String registerNumber;

    @Column(name = "home_latitude")
    private Double homeLatitude;

    @Column(name = "home_longitude")
    private Double homeLongitude;

    @Column(name = "home_address", columnDefinition = "TEXT")
    private String homeAddress;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_stop_id")
    private Stop preferredStop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_route_id")
    private Route preferredRoute;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_bus_id")
    private Bus preferredBus;

    @Column(name = "notification_preferences")
    private String notificationPreferences;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "student_favorite_buses",
        joinColumns = @JoinColumn(name = "student_id"),
        inverseJoinColumns = @JoinColumn(name = "bus_id")
    )
    @Builder.Default
    private Set<Bus> favoriteBuses = new HashSet<>();
}
