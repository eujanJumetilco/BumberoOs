package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.StaffEntity;
import com.groupnine.bumberoos.domain.enums.StaffAvailability;
import com.groupnine.bumberoos.domain.exceptions.BadRequestException;
import com.groupnine.bumberoos.domain.exceptions.ConflictException;
import com.groupnine.bumberoos.repositories.IncidentStaffAssignmentRepository;
import com.groupnine.bumberoos.repositories.StaffRepository;
import com.groupnine.bumberoos.repositories.UserAccountRepository;
import com.groupnine.bumberoos.util.StringUtil;
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
        staff.normalize();
        requireValidFreeBadge(staff.getBadgeNumber(), null);
        return staffRepository.save(staff);
    }

    // =====> RETIRE <===== //

    /**
     * Soft-removes a staff member: employed = false and availability = UNAVAILABLE. The record, its
     * assignment history and any linked account are kept. Retiring someone who is already retired is a no-op.
     *
     * @return the updated staff member, or empty if not found.
     * @throws ConflictException if the member is currently DISPATCHED or assigned to an open incident.
     */
    public Optional<StaffEntity> retireStaff(Long id) {
        return staffRepository.findByIdForUpdate(id).map(staff -> {
            if (staff.getStaffAvailability() == StaffAvailability.DISPATCHED
                    || assignmentRepository.existsByStaffIdAndReleasedAtIsNull(id)) {
                throw new ConflictException(staff.getName() + " is assigned to an incident; "
                        + "release them before retiring");
            }
            staff.setEmployed(false);
            staff.setStaffAvailability(StaffAvailability.UNAVAILABLE);
            return staffRepository.save(staff);
        });
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
        return editStaff(id, name, role, contactNumber, address, employed, trainings, certifications,
                availability, null);
    }

    /**
     * As above. {@code badgeNumber} must be 7 digits and not used by anyone else; a blank value clears it.
     *
     * @throws com.groupnine.bumberoos.domain.exceptions.BadRequestException if the badge number is malformed
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
            StaffAvailability availability,
            String badgeNumber
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

            String newBadge = badgeNumber == null ? null : StringUtil.clean(badgeNumber);
            if (newBadge != null && !newBadge.equals(staff.getBadgeNumber())) {
                if (!newBadge.isEmpty()) requireValidFreeBadge(newBadge, id);
            }

            if (newBadge != null) staff.setBadgeNumber(newBadge.isEmpty() ? null : newBadge);
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

    /** Null is allowed (no ID yet). Otherwise exactly 7 digits, unused by any other staff member. */
    private void requireValidFreeBadge(String badgeNumber, Long ownId) {
        if (badgeNumber == null) return;
        if (!badgeNumber.matches("\\d{7}")) {
            throw new BadRequestException("staff ID must be exactly 7 digits");
        }
        staffRepository.findByBadgeNumber(badgeNumber).ifPresent(other -> {
            if (!other.getId().equals(ownId)) {
                throw new ConflictException("staff ID " + badgeNumber + " is already used by " + other.getName());
            }
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