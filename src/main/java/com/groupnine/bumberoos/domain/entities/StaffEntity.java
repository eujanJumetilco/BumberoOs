package com.groupnine.bumberoos.domain.entities;

import com.groupnine.bumberoos.domain.enums.StaffAvailability;
import com.groupnine.bumberoos.util.StringUtil;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "staff")
@Getter
@Setter
@NoArgsConstructor
public class StaffEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StaffAvailability staffAvailability = StaffAvailability.UNAVAILABLE;

    private String name;
    private String role;
    private String contactNumber;
    private String address;
    private boolean employed;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "staff_trainings", joinColumns = @JoinColumn(name = "staff_id"))
    @Column(name = "training")
    private List<String> trainings = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "staff_certifications", joinColumns = @JoinColumn(name = "staff_id"))
    @Column(name = "certification")
    private List<String> certifications = new ArrayList<>();

    @Column(updatable = false)
    private LocalDate dateJoined;

    public StaffEntity(
            String name, String role, String contactNumber,
            String address, boolean employed, List<String> trainings,
            List<String> certifications
    ) {
        this.name = name;
        this.role = role;
        this.contactNumber = contactNumber;
        this.address = address;
        this.employed = employed;
        this.trainings = trainings == null ? new ArrayList<>() : new ArrayList<>(trainings);
        this.certifications = certifications == null ? new ArrayList<>() : new ArrayList<>(certifications);
        normalize();
    }

    @PrePersist
    void onCreate() {
        if (dateJoined == null) dateJoined = LocalDate.now();
        normalize();
    }

    @PreUpdate
    public void normalize() {
        name = StringUtil.toTitleCase(name);
        role = StringUtil.toTitleCase(role);
        contactNumber = StringUtil.normalizePhoneNumber(contactNumber);
        address = StringUtil.capitalizeFirstLetter(address);
        trainings = StringUtil.cleanList(trainings);
        certifications = StringUtil.cleanList(certifications);
    }
}
