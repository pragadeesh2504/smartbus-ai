package com.smartbus.infrastructure.adapter.jpa;

import com.smartbus.domain.model.Route;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RouteRepository extends JpaRepository<Route, UUID> {
    Optional<Route> findByRouteNameAndDeletedAtIsNull(String routeName);
    List<Route> findByDeletedAtIsNull();
    List<Route> findByStatusAndDeletedAtIsNull(String status);

    List<Route> findByCollegeIdAndDeletedAtIsNull(UUID collegeId);
    List<Route> findByCollegeIdAndStatusAndDeletedAtIsNull(UUID collegeId, String status);
    Optional<Route> findByCollegeIdAndRouteNameAndDeletedAtIsNull(UUID collegeId, String routeName);
    Optional<Route> findByIdAndCollegeIdAndDeletedAtIsNull(UUID id, UUID collegeId);
    long countByCollegeIdAndDeletedAtIsNull(UUID collegeId);
    boolean existsByCollegeIdAndRouteNameAndDeletedAtIsNull(UUID collegeId, String routeName);
}
