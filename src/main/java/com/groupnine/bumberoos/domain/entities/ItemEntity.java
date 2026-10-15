package com.groupnine.bumberoos.domain.entities;

import com.groupnine.bumberoos.domain.enums.ItemAvailability;
import com.groupnine.bumberoos.domain.enums.ItemCategory;
import com.groupnine.bumberoos.domain.enums.ItemCondition;
import com.groupnine.bumberoos.util.StringUtil;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "items")
@Getter
@Setter
@NoArgsConstructor
public class ItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemCondition itemCondition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemCategory category = ItemCategory.OTHER;

    @Column(nullable = false)
    private String name;
    private String description;

    /** Plate number for engines, serial for SCBA sets, etc. */
    private String serialNumber;

    /** Total units owned. */
    private int quantity;

    /** Units currently out on incidents. Invariant: 0 <= quantityDispatched <= quantity. */
    @Column(nullable = false)
    private int quantityDispatched;

    private LocalDate lastMaintained;

    /** For items that expire, such as extinguishers or medical stock. Null for items that do not. */
    private LocalDate expiryDate;

    public ItemEntity(
            ItemCondition itemCondition, String name,
            String description, int quantity
    ) {
        this(itemCondition, name, description, quantity, ItemCategory.OTHER, null);
    }

    public ItemEntity(
            ItemCondition itemCondition, String name,
            String description, int quantity,
            ItemCategory category, String serialNumber
    ) {
        this.itemCondition = itemCondition;
        this.name = name;
        this.description = description;
        this.quantity = quantity;
        this.category = category == null ? ItemCategory.OTHER : category;
        this.serialNumber = serialNumber;
        normalize();
    }

    @Transient
    public int getQuantityAvailable() {
        return quantity - quantityDispatched;
    }

    /** Derived, never stored. */
    @Transient
    public ItemAvailability getAvailability() {
        if (itemCondition != ItemCondition.GOOD || quantity == 0) return ItemAvailability.UNAVAILABLE;
        if (quantityDispatched == 0) return ItemAvailability.AVAILABLE;
        if (quantityDispatched >= quantity) return ItemAvailability.DISPATCHED;
        return ItemAvailability.PARTIALLY_DISPATCHED;
    }

    @PrePersist
    @PreUpdate
    public void normalize() {
        name = StringUtil.toTitleCase(name);
        description = StringUtil.capitalizeFirstLetter(description);
        serialNumber = StringUtil.clean(serialNumber);
    }
}
