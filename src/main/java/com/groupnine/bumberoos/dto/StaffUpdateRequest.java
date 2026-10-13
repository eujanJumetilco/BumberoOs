package com.groupnine.bumberoos.dto;

import com.groupnine.bumberoos.domain.enums.StaffAvailability;

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
        StaffAvailability availability
) {}
