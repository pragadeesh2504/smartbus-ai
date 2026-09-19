package com.smartbus.infrastructure.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateCollegeCodeRequest {

    @NotBlank(message = "College code cannot be blank")
    @Size(min = 2, max = 20, message = "College code must be between 2 and 20 characters")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "College code can only contain alphanumeric characters, underscores, and dashes")
    private String collegeCode;
}
