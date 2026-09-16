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
}
