package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.StaffEntity;
import com.groupnine.bumberoos.domain.enums.StaffAvailability;
import com.groupnine.bumberoos.repositories.StaffRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class StaffService {

    private final StaffRepository staffRepository;

    public StaffService(StaffRepository staffRepository) {
        this.staffRepository = staffRepository;
    }

    // =====> ADD <===== //

    public StaffEntity addStaff(StaffEntity staff) {
        staff.setId(null);
        return staffRepository.save(staff);
    }

    // =====> REMOVE <===== //

    /** @return true if found and removed, false otherwise. */
    public boolean removeStaff(Long id) {
        if (!staffRepository.existsById(id)) return false;
        staffRepository.deleteById(id);
        return true;
    }

    // =====> EDIT <===== //

    /** Null arguments leave the existing value unchanged. */
    public Optional<StaffEntity> editStaff(
            Long id,
            String name,
            String role,
            String contactNumber,
            String address,
            Boolean employed,
            List<String> trainings,
            List<String> certifications,
            StaffAvailability availability
    ) {
        return staffRepository.findById(id).map(staff -> {
            if (name != null) staff.setName(name);
            if (role != null) staff.setRole(role);
            if (contactNumber != null) staff.setContactNumber(contactNumber);
            if (address != null) staff.setAddress(address);
            if (employed != null) staff.setEmployed(employed);
            if (trainings != null) staff.setTrainings(trainings);
            if (certifications != null) staff.setCertifications(certifications);
            if (availability != null) staff.setStaffAvailability(availability);
            staff.normalize();
            return staffRepository.save(staff);
        });
    }

    // =====> QUERIES <===== //

    @Transactional(readOnly = true)
    public Optional<StaffEntity> findById(Long id) {
        return staffRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<StaffEntity> findByEmail(String email) {
        return staffRepository.findByEmailIgnoreCase(email);
    }

    @Transactional(readOnly = true)
    public List<StaffEntity> getStaffList() {
        return staffRepository.findAll();
    }
}
