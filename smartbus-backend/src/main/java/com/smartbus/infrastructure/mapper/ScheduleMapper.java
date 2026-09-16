package com.smartbus.infrastructure.mapper;

import com.smartbus.domain.model.Schedule;
import com.smartbus.infrastructure.dto.ScheduleDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ScheduleMapper {

    @Mapping(target = "routeId", source = "route.id")
    @Mapping(target = "routeName", source = "route.routeName")
    @Mapping(target = "busId", source = "bus.id")
    @Mapping(target = "busNumber", source = "bus.busNumber")
    @Mapping(target = "driverId", source = "driver.id")
    @Mapping(target = "driverName", expression = "java(schedule.getDriver().getUser().getFirstName() + \" \" + schedule.getDriver().getUser().getLastName())")
    @Mapping(target = "departureTime", source = "departureTime")
    @Mapping(target = "arrivalTime", source = "arrivalTime")
    ScheduleDto toDto(Schedule schedule);
}
