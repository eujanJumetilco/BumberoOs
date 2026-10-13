package com.groupnine.bumberoos.domain.entities;

import com.groupnine.bumberoos.domain.enums.ItemCondition;
import com.groupnine.bumberoos.util.StringUtil;

import java.io.Serializable;
import java.time.LocalDate;

public class ItemEntity implements Serializable {

    private int id;
    private ItemCondition itemCondition;

    private String name;
    private String description;
    private int quantity;
    private LocalDate lastMaintained;

    public ItemEntity(
            int id, int itemCondition, String name,
            String description, int quantity
    ) {
        setId(id);
        setItemCondition(itemCondition);
        setName(name);
        setDescription(description);
        setQuantity(quantity);

    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public ItemCondition getItemCondition() {
        return itemCondition;
    }

    public String getItemConditionAsString(){
        return switch(this.itemCondition) {
            case GOOD -> "Good";
            case DAMAGED -> "Damaged";
            case RETIRED -> "Retired";
            default -> null;
        };
    }

    public void setItemCondition(int input) {
        this.itemCondition = ItemCondition.setCondition(input);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = StringUtil.toTitleCase(name);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = StringUtil.capitalizeFirstLetter(description);
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public LocalDate getLastMaintained() {
        return lastMaintained;
    }

    public void setLastMaintained(LocalDate lastMaintained) {
        this.lastMaintained = lastMaintained;
    }
}