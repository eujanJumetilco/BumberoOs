package com.groupnine.bumberoos.dto;

import com.groupnine.bumberoos.domain.enums.StaffAvailability;

import jakarta.validation.constraints.Pattern;

import java.util.List;

/** Partial update: any null field is left unchanged. */
public record StaffUpdateRequest(
        String name,
        String role,
        String contactNumber,
        String address,
        Boolean employed,
        List<String> trainings,
        List<String> certifications,
        StaffAvailability availability,
        @Pattern(regexp = "|\\d{7}", message = "must be exactly 7 digits (blank clears it)") String badgeNumber
) {}
