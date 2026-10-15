package com.groupnine.bumberoos.domain.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Station-wide inventory settings. A single row, always id {@link #ID}. */
@Entity
@Table(name = "inventory_settings")
@Getter
@Setter
@NoArgsConstructor
public class InventorySettingsEntity {

    public static final long ID = 1L;
    public static final int DEFAULT_MAINTENANCE_INTERVAL_DAYS = 180;
    public static final int DEFAULT_EXPIRY_WARNING_DAYS = 30;

    @Id
    private Long id = ID;

    /** Days between maintenance when a category has no interval of its own. 0 turns reminders off. */
    @Column(nullable = false)
    private int defaultMaintenanceIntervalDays = DEFAULT_MAINTENANCE_INTERVAL_DAYS;

    /** How many days before the expiry date an item starts to be flagged. */
    @Column(nullable = false)
    private int expiryWarningDays = DEFAULT_EXPIRY_WARNING_DAYS;
}
