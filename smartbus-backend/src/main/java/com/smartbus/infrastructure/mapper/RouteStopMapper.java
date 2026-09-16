package com.smartbus.infrastructure.mapper;

import com.smartbus.domain.model.RouteStop;
import com.smartbus.infrastructure.dto.RouteStopDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {StopMapper.class})
public interface RouteStopMapper {
    @Mapping(target = "stop", source = "stop")
    RouteStopDto toDto(RouteStop routeStop);
}
