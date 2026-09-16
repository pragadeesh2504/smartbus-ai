package com.smartbus.infrastructure.mapper;

import com.smartbus.domain.model.Bus;
import com.smartbus.infrastructure.dto.BusDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface BusMapper {
    @Mapping(target = "gpsDeviceId", source = "gpsDevice.id")
    @Mapping(target = "gpsDeviceStatus", source = "gpsDevice.status")
    BusDto toDto(Bus bus);

    @Mapping(target = "gpsDevice", ignore = true)
    Bus toEntity(BusDto busDto);
}
