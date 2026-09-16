package com.smartbus.infrastructure.adapter.jpa;

import com.smartbus.domain.model.Route;
import com.smartbus.domain.model.RouteStop;
import com.smartbus.domain.model.RouteStopId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface RouteStopRepository extends JpaRepository<RouteStop, RouteStopId> {
    List<RouteStop> findByRouteIdOrderBySequenceNumberAsc(UUID routeId);
    List<RouteStop> findByRouteOrderBySequenceNumberAsc(Route route);
}
