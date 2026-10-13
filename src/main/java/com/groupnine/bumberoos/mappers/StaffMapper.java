package com.groupnine.bumberoos.mappers;

import com.groupnine.bumberoos.domain.entities.StaffEntity;
import com.groupnine.bumberoos.dto.StaffRequest;
import com.groupnine.bumberoos.dto.StaffResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StaffMapper {

    public StaffEntity toEntity(StaffRequest request) {
        return new StaffEntity(
                request.name(), request.role(), request.contactNumber(),
                request.address(), request.employed(),
                request.trainings(), request.certifications()
        );
    }

    public StaffResponse toResponse(StaffEntity staff) {
        return new StaffResponse(
                staff.getId(), staff.getName(), staff.getRole(),
                staff.getContactNumber(), staff.getAddress(), staff.isEmployed(),
                staff.getStaffAvailability(),
                List.copyOf(staff.getTrainings()), List.copyOf(staff.getCertifications()),
                staff.getDateJoined()
        );
    }

    public List<StaffResponse> toResponses(List<StaffEntity> staff) {
        return staff.stream().map(this::toResponse).toList();
    }
}
