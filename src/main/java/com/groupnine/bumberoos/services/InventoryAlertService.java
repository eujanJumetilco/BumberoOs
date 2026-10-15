package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.ItemEntity;
import com.groupnine.bumberoos.domain.enums.AlertSeverity;
import com.groupnine.bumberoos.domain.enums.AlertType;
import com.groupnine.bumberoos.domain.enums.ItemCategory;
import com.groupnine.bumberoos.domain.enums.ItemCondition;
import com.groupnine.bumberoos.dto.AlertResponse;
import com.groupnine.bumberoos.dto.CategoryRuleDto;
import com.groupnine.bumberoos.dto.InventoryRulesDto;
import com.groupnine.bumberoos.repositories.ItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Works out what needs attention in the inventory from the items and the inventory rules:
 * expired or soon-to-expire items, overdue maintenance, low stock per category, and damaged vehicles.
 * Retired items are ignored. Nothing is stored; alerts are recomputed on every call.
 */
@Service
@Transactional(readOnly = true)
public class InventoryAlertService {

    private static final List<ItemCategory> VEHICLES = List.of(ItemCategory.ENGINE, ItemCategory.AMBULANCE);

    private final ItemRepository itemRepository;
    private final InventoryRulesService rulesService;

    public InventoryAlertService(ItemRepository itemRepository, InventoryRulesService rulesService) {
        this.itemRepository = itemRepository;
        this.rulesService = rulesService;
    }

    /** HIGH alerts first, then by item name. {@code today} is the station's local date. */
    public List<AlertResponse> getAlerts(LocalDate today) {
        InventoryRulesDto rules = rulesService.getRules();
        Map<ItemCategory, CategoryRuleDto> byCategory = new EnumMap<>(ItemCategory.class);
        rules.categories().forEach(rule -> byCategory.put(rule.category(), rule));

        List<ItemEntity> items = itemRepository.findAll();
        List<AlertResponse> alerts = new ArrayList<>();

        for (ItemEntity item : items) {
            if (item.getItemCondition() == ItemCondition.RETIRED) continue;
            expiryAlert(item, today, rules.expiryWarningDays()).ifPresent(alerts::add);
            maintenanceAlert(item, today, rules, byCategory.get(item.getCategory())).ifPresent(alerts::add);
            if (item.getItemCondition() == ItemCondition.DAMAGED && VEHICLES.contains(item.getCategory())) {
                alerts.add(new AlertResponse(AlertType.VEHICLE_DAMAGED, AlertSeverity.HIGH, item.getId(), item.getName(),
                        item.getCategory(), item.getName() + " is damaged and cannot be dispatched"));
            }
        }

        for (CategoryRuleDto rule : rules.categories()) {
            if (rule.lowStockThreshold() <= 0) continue;
            int usable = items.stream()
                    .filter(i -> i.getCategory() == rule.category() && i.getItemCondition() == ItemCondition.GOOD)
                    .mapToInt(ItemEntity::getQuantityAvailable).sum();
            if (usable < rule.lowStockThreshold()) {
                alerts.add(new AlertResponse(AlertType.LOW_STOCK, usable == 0 ? AlertSeverity.HIGH : AlertSeverity.MEDIUM,
                        null, null, rule.category(), "Low stock in " + pretty(rule.category()) + ": " + usable
                        + " usable, minimum " + rule.lowStockThreshold()));
            }
        }

        alerts.sort(Comparator.comparing(AlertResponse::severity)
                .thenComparing(a -> a.itemName() == null ? "" : a.itemName()));
        return alerts;
    }

    private java.util.Optional<AlertResponse> expiryAlert(ItemEntity item, LocalDate today, int warningDays) {
        LocalDate expiry = item.getExpiryDate();
        if (expiry == null) return java.util.Optional.empty();
        long daysLeft = ChronoUnit.DAYS.between(today, expiry);
        if (daysLeft < 0) {
            return java.util.Optional.of(new AlertResponse(AlertType.EXPIRED, AlertSeverity.HIGH, item.getId(), item.getName(),
                    item.getCategory(), item.getName() + " expired " + plural(-daysLeft, "day") + " ago (" + expiry + ")"));
        }
        if (daysLeft <= warningDays) {
            return java.util.Optional.of(new AlertResponse(AlertType.EXPIRING_SOON, AlertSeverity.MEDIUM, item.getId(),
                    item.getName(), item.getCategory(), item.getName() + (daysLeft == 0 ? " expires today"
                    : " expires in " + plural(daysLeft, "day")) + " (" + expiry + ")"));
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<AlertResponse> maintenanceAlert(
            ItemEntity item, LocalDate today, InventoryRulesDto rules, CategoryRuleDto categoryRule
    ) {
        Integer own = categoryRule == null ? null : categoryRule.maintenanceIntervalDays();
        int interval = own != null ? own : rules.defaultMaintenanceIntervalDays();
        if (interval <= 0) return java.util.Optional.empty();

        LocalDate last = item.getLastMaintained();
        if (last == null) {
            return java.util.Optional.of(new AlertResponse(AlertType.MAINTENANCE_DUE, AlertSeverity.MEDIUM, item.getId(),
                    item.getName(), item.getCategory(), item.getName() + " has no maintenance record"));
        }
        long since = ChronoUnit.DAYS.between(last, today);
        if (since > interval) {
            return java.util.Optional.of(new AlertResponse(AlertType.MAINTENANCE_DUE, AlertSeverity.MEDIUM, item.getId(),
                    item.getName(), item.getCategory(), item.getName() + " is overdue for maintenance (last " + last
                    + ", every " + plural(interval, "day") + ")"));
        }
        return java.util.Optional.empty();
    }

    private static String plural(long n, String unit) {
        return n + " " + unit + (n == 1 ? "" : "s");
    }

    private static String pretty(ItemCategory category) {
        return category.name().toLowerCase().replace('_', ' ');
    }
}
