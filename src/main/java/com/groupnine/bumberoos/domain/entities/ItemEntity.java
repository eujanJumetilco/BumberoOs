package com.groupnine.bumberoos.domain.entities;

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

    @Column(nullable = false)
    private String name;
    private String description;
    private int quantity;
    private LocalDate lastMaintained;

    public ItemEntity(
            ItemCondition itemCondition, String name,
            String description, int quantity
    ) {
        this.itemCondition = itemCondition;
        this.name = name;
        this.description = description;
        this.quantity = quantity;
        normalize();
    }

    @PrePersist
    @PreUpdate
    public void normalize() {
        name = StringUtil.toTitleCase(name);
        description = StringUtil.capitalizeFirstLetter(description);
    }
}
