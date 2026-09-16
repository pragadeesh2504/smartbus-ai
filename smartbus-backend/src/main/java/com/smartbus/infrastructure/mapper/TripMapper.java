package com.smartbus.infrastructure.mapper;

import com.smartbus.domain.model.Trip;
import com.smartbus.infrastructure.dto.TripDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TripMapper {

    @Mapping(target = "scheduleId", source = "schedule.id")
    @Mapping(target = "busId", source = "bus.id")
    @Mapping(target = "busNumber", source = "bus.busNumber")
    @Mapping(target = "driverId", source = "driver.id")
    @Mapping(target = "driverName", expression = "java(trip.getDriver().getUser().getFirstName() + \" \" + trip.getDriver().getUser().getLastName())")
    @Mapping(target = "routeId", source = "route.id")
    @Mapping(target = "routeName", source = "route.routeName")
    TripDto toDto(Trip trip);
}
