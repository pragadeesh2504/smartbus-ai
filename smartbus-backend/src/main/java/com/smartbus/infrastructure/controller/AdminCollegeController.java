package com.smartbus.infrastructure.controller;

import com.smartbus.application.service.CollegeService;
import com.smartbus.domain.exception.BadRequestException;
import com.smartbus.infrastructure.dto.ApiResponse;
import com.smartbus.infrastructure.dto.CollegeDto;
import com.smartbus.infrastructure.dto.UpdateCollegeCodeRequest;
import com.smartbus.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/admin/college", "/api/admin/college"})
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminCollegeController {

    private final CollegeService collegeService;

    @GetMapping
    public ResponseEntity<ApiResponse<CollegeDto>> getCollegeInfo(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || principal.getCollegeId() == null) {
            throw new BadRequestException("Admin is not associated with a college tenant");
        }
        CollegeDto dto = collegeService.getCollegeForAdmin(principal.getCollegeId());
        return ResponseEntity.ok(ApiResponse.success("College information retrieved successfully", dto));
    }

    @PutMapping("/code")
    public ResponseEntity<ApiResponse<CollegeDto>> updateCollegeCode(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateCollegeCodeRequest request) {
        if (principal == null || principal.getCollegeId() == null) {
            throw new BadRequestException("Admin is not associated with a college tenant");
        }
        CollegeDto dto = collegeService.updateCollegeCodeForAdmin(principal.getCollegeId(), request.getCollegeCode());
        return ResponseEntity.ok(ApiResponse.success("College code updated successfully", dto));
    }
}
