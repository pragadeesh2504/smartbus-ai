package com.smartbus.infrastructure.mapper;

import com.smartbus.domain.model.Route;
import com.smartbus.infrastructure.dto.RouteDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RouteMapper {
    @Mapping(target = "stops", ignore = true)
    RouteDto toDto(Route route);
    
    Route toEntity(RouteDto routeDto);
}
