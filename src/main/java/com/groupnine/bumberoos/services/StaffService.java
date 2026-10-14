package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.StaffEntity;
import com.groupnine.bumberoos.domain.enums.StaffAvailability;
import com.groupnine.bumberoos.domain.exceptions.ConflictException;
import com.groupnine.bumberoos.repositories.IncidentStaffAssignmentRepository;
import com.groupnine.bumberoos.repositories.StaffRepository;
import com.groupnine.bumberoos.repositories.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class StaffService {

    private final StaffRepository staffRepository;
    private final IncidentStaffAssignmentRepository assignmentRepository;
    private final UserAccountRepository userAccountRepository;

    public StaffService(
            StaffRepository staffRepository,
            IncidentStaffAssignmentRepository assignmentRepository,
            UserAccountRepository userAccountRepository
    ) {
        this.staffRepository = staffRepository;
        this.assignmentRepository = assignmentRepository;
        this.userAccountRepository = userAccountRepository;
    }

    // =====> ADD <===== //

    public StaffEntity addStaff(StaffEntity staff) {
        staff.setId(null);
        return staffRepository.save(staff);
    }

    // =====> REMOVE <===== //

    /**
     * @return true if found and removed, false if not found.
     * @throws ConflictException if the staff member has assignment history or a linked account.
     */
    public boolean removeStaff(Long id) {
        Optional<StaffEntity> found = staffRepository.findById(id);
        if (found.isEmpty()) return false;

        String name = found.get().getName();
        if (assignmentRepository.existsByStaffId(id)) {
            throw new ConflictException(name + " has incident assignment history and cannot be deleted; "
                    + "set employed to false instead");
        }
        if (userAccountRepository.existsByStaffId(id)) {
            throw new ConflictException(name + " is linked to a login account and cannot be deleted; "
                    + "unlink the account first or set employed to false instead");
        }
        staffRepository.deleteById(id);
        return true;
    }

    // =====> EDIT <===== //

    /**
     * Null arguments leave the existing value unchanged.
     *
     * @throws ConflictException if the change would conflict with dispatch state: DISPATCHED cannot be
     *                           set manually, availability cannot change while assigned, and a
     *                           dispatched member cannot be marked not employed.
     */
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
            boolean availabilityChanging = availability != null && availability != staff.getStaffAvailability();

            if (availabilityChanging) {
                if (availability == StaffAvailability.DISPATCHED) {
                    throw new ConflictException("availability DISPATCHED can only be set by assigning "
                            + staff.getName() + " to an incident");
                }
                if (assignmentRepository.existsByStaffIdAndReleasedAtIsNull(id)) {
                    throw new ConflictException(staff.getName() + " is assigned to an incident; "
                            + "release them before changing availability");
                }
            }
            if (Boolean.FALSE.equals(employed) && staff.getStaffAvailability() == StaffAvailability.DISPATCHED) {
                throw new ConflictException(staff.getName() + " is DISPATCHED and cannot be marked not employed");
            }

            if (name != null) staff.setName(name);
            if (role != null) staff.setRole(role);
            if (contactNumber != null) staff.setContactNumber(contactNumber);
            if (address != null) staff.setAddress(address);
            if (employed != null) staff.setEmployed(employed);
            if (trainings != null) staff.setTrainings(new ArrayList<>(trainings));
            if (certifications != null) staff.setCertifications(new ArrayList<>(certifications));
            if (availabilityChanging) staff.setStaffAvailability(availability);
            staff.normalize();
            return staffRepository.save(staff);
        });
    }

    // =====> QUERIES <===== //

    @Transactional(readOnly = true)
    public Optional<StaffEntity> findById(Long id) {
        return staffRepository.findById(id);
    }

    /** Loads with a pessimistic write lock; used by dispatching. */
    public Optional<StaffEntity> findByIdForUpdate(Long id) {
        return staffRepository.findByIdForUpdate(id);
    }

    @Transactional(readOnly = true)
    public List<StaffEntity> getStaffList() {
        return staffRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<StaffEntity> getStaffList(StaffAvailability availability) {
        return availability == null ? staffRepository.findAll() : staffRepository.findByStaffAvailability(availability);
    }
}
