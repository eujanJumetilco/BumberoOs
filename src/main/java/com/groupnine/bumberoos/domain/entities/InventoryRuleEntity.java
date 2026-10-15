package com.groupnine.bumberoos.domain.entities;

import com.groupnine.bumberoos.domain.enums.ItemCategory;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Per-category inventory rules. A category with no row uses the defaults (no low-stock alert, default interval). */
@Entity
@Table(name = "inventory_rules")
@Getter
@Setter
@NoArgsConstructor
public class InventoryRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private ItemCategory category;

    /** Alert when the usable units across the category fall below this. 0 turns the alert off. */
    @Column(nullable = false)
    private int lowStockThreshold;

    /** Days between maintenance for this category. Null uses the default; 0 turns the reminder off. */
    private Integer maintenanceIntervalDays;

    public InventoryRuleEntity(ItemCategory category, int lowStockThreshold, Integer maintenanceIntervalDays) {
        this.category = category;
        this.lowStockThreshold = lowStockThreshold;
        this.maintenanceIntervalDays = maintenanceIntervalDays;
    }
}
