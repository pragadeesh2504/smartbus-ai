package com.smartbus.infrastructure.mapper;

import com.smartbus.domain.model.Stop;
import com.smartbus.infrastructure.dto.StopDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface StopMapper {
    StopDto toDto(Stop stop);
    Stop toEntity(StopDto stopDto);
}
