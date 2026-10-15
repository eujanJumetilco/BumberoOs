package com.groupnine.bumberoos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/** Payload for creating a staff member. */
public record StaffRequest(
        @NotBlank String name,
        @NotBlank String role,
        String contactNumber,
        String address,
        boolean employed,
        List<String> trainings,
        List<String> certifications,
        @Pattern(regexp = "\\d{7}", message = "must be exactly 7 digits") String badgeNumber
) {}
