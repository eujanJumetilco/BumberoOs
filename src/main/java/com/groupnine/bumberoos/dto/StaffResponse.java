package com.groupnine.bumberoos.dto;

import com.groupnine.bumberoos.domain.enums.StaffAvailability;

import java.time.LocalDate;
import java.util.List;

public record StaffResponse(
        Long id,
        String name,
        String role,
        String contactNumber,
        String address,
        boolean employed,
        StaffAvailability availability,
        List<String> trainings,
        List<String> certifications,
        LocalDate dateJoined
) {}
