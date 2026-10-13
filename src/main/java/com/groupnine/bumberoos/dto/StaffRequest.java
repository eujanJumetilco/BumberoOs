package com.groupnine.bumberoos.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/** Payload for creating a staff member. */
public record StaffRequest(
        @NotBlank String name,
        @NotBlank String role,
        String contactNumber,
        String address,
        boolean employed,
        List<String> trainings,
        List<String> certifications
) {}
