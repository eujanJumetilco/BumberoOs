package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.ItemEntity;
import com.groupnine.bumberoos.domain.enums.AlertSeverity;
import com.groupnine.bumberoos.domain.enums.AlertType;
import com.groupnine.bumberoos.domain.enums.ItemCategory;
import com.groupnine.bumberoos.domain.enums.ItemCondition;
import com.groupnine.bumberoos.dto.AlertResponse;
import com.groupnine.bumberoos.dto.CategoryRuleDto;
import com.groupnine.bumberoos.dto.InventoryRulesDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class InventoryAlertServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);

    @Autowired InventoryAlertService alerts;
    @Autowired InventoryRulesService rules;
    @Autowired InventoryService inventory;

    private ItemEntity item(String name, ItemCategory category, ItemCondition condition, int quantity) throws Exception {
        return inventory.addItem(new ItemEntity(condition, name, null, quantity, category, null));
    }

    private ItemEntity item(String name) throws Exception {
        return item(name, ItemCategory.OTHER, ItemCondition.GOOD, 1);
    }

    private void maintainedOn(ItemEntity item, LocalDate date) {
        item.setLastMaintained(date);
    }

    /** Disables maintenance reminders so a test only sees the alert it is about. */
    private void noMaintenanceReminders() {
        rules.updateRules(new InventoryRulesDto(0, 30, List.of()));
    }

    private Optional<AlertResponse> alertFor(ItemEntity item, AlertType type) {
        return alerts.getAlerts(TODAY).stream()
                .filter(a -> item.getId().equals(a.itemId()) && a.type() == type).findFirst();
    }

    // ===== expiry ===== //

    @Test
    void expiredItem_isHigh_withDaysAgo() throws Exception {
        noMaintenanceReminders();
        ItemEntity ext = item("Fire Extinguisher A");
        ext.setExpiryDate(TODAY.minusDays(3));

        AlertResponse a = alertFor(ext, AlertType.EXPIRED).orElseThrow();
        assertEquals(AlertSeverity.HIGH, a.severity());
        assertTrue(a.message().contains("expired 3 days ago"), a.message());
    }

    @Test
    void expiryWindow_flagsSoonButNotLater_andBoundaryDaysCount() throws Exception {
        noMaintenanceReminders();
        ItemEntity today = item("Ext Today");
        today.setExpiryDate(TODAY);
        ItemEntity edge = item("Ext Edge");
        edge.setExpiryDate(TODAY.plusDays(30));
        ItemEntity later = item("Ext Later");
        later.setExpiryDate(TODAY.plusDays(31));

        AlertResponse soonToday = alertFor(today, AlertType.EXPIRING_SOON).orElseThrow();
        assertEquals(AlertSeverity.MEDIUM, soonToday.severity());
        assertTrue(soonToday.message().contains("expires today"));
        assertTrue(alertFor(edge, AlertType.EXPIRING_SOON).isPresent(), "exactly 30 days is inside a 30-day window");
        assertTrue(alertFor(later, AlertType.EXPIRING_SOON).isEmpty());
        assertTrue(alertFor(today, AlertType.EXPIRED).isEmpty(), "expiring today is not yet expired");
    }

    @Test
    void expiryWarningWindow_followsTheRule() throws Exception {
        ItemEntity ext = item("Ext Window");
        ext.setExpiryDate(TODAY.plusDays(45));
        rules.updateRules(new InventoryRulesDto(0, 30, List.of()));
        assertTrue(alertFor(ext, AlertType.EXPIRING_SOON).isEmpty());

        rules.updateRules(new InventoryRulesDto(0, 60, List.of()));
        assertTrue(alertFor(ext, AlertType.EXPIRING_SOON).isPresent());
    }

    @Test
    void retiredItemsAreIgnored() throws Exception {
        ItemEntity old = item("Retired Ext", ItemCategory.OTHER, ItemCondition.RETIRED, 1);
        old.setExpiryDate(TODAY.minusDays(100));

        assertTrue(alerts.getAlerts(TODAY).stream().noneMatch(a -> old.getId().equals(a.itemId())));
    }

    // ===== maintenance ===== //

    @Test
    void maintenance_usesTheDefaultInterval_andCountsOnlyWhenOlderThanIt() throws Exception {
        rules.updateRules(new InventoryRulesDto(90, 30, List.of()));
        ItemEntity overdue = item("Overdue");
        maintainedOn(overdue, TODAY.minusDays(91));
        ItemEntity exact = item("Exact");
        maintainedOn(exact, TODAY.minusDays(90));
        ItemEntity fresh = item("Fresh");
        maintainedOn(fresh, TODAY.minusDays(10));

        assertTrue(alertFor(overdue, AlertType.MAINTENANCE_DUE).orElseThrow().message().contains("overdue"));
        assertTrue(alertFor(exact, AlertType.MAINTENANCE_DUE).isEmpty(), "exactly the interval is not yet overdue");
        assertTrue(alertFor(fresh, AlertType.MAINTENANCE_DUE).isEmpty());
    }

    @Test
    void maintenance_neverMaintainedIsFlagged() throws Exception {
        rules.updateRules(new InventoryRulesDto(90, 30, List.of()));
        ItemEntity never = item("Never Maintained");

        assertTrue(alertFor(never, AlertType.MAINTENANCE_DUE).orElseThrow().message().contains("no maintenance record"));
    }

    @Test
    void maintenance_categoryIntervalOverridesDefault_andZeroTurnsItOff() throws Exception {
        rules.updateRules(new InventoryRulesDto(365, 30, List.of(
                new CategoryRuleDto(ItemCategory.ENGINE, 0, 30),
                new CategoryRuleDto(ItemCategory.HOSE, 0, 0))));
        ItemEntity engine = item("Engine X", ItemCategory.ENGINE, ItemCondition.GOOD, 1);
        maintainedOn(engine, TODAY.minusDays(45));
        ItemEntity hose = item("Hose X", ItemCategory.HOSE, ItemCondition.GOOD, 1);
        maintainedOn(hose, TODAY.minusDays(1000));
        ItemEntity other = item("Other X");
        maintainedOn(other, TODAY.minusDays(45));

        assertTrue(alertFor(engine, AlertType.MAINTENANCE_DUE).isPresent(), "engine uses its own 30-day interval");
        assertTrue(alertFor(hose, AlertType.MAINTENANCE_DUE).isEmpty(), "0 means no reminder for this category");
        assertTrue(alertFor(other, AlertType.MAINTENANCE_DUE).isEmpty(), "other uses the 365-day default");
    }

    // ===== low stock ===== //

    @Test
    void lowStock_countsOnlyUsableUnits() throws Exception {
        rules.updateRules(new InventoryRulesDto(0, 30, List.of(new CategoryRuleDto(ItemCategory.SCBA, 10_000, null))));
        item("SCBA Good", ItemCategory.SCBA, ItemCondition.GOOD, 3);
        item("SCBA Damaged", ItemCategory.SCBA, ItemCondition.DAMAGED, 50);

        AlertResponse low = alerts.getAlerts(TODAY).stream()
                .filter(a -> a.type() == AlertType.LOW_STOCK && a.category() == ItemCategory.SCBA).findFirst().orElseThrow();
        assertNull(low.itemId());
        assertTrue(low.message().contains("minimum 10000"), low.message());

        rules.updateRules(new InventoryRulesDto(0, 30, List.of(new CategoryRuleDto(ItemCategory.SCBA, 1, null))));
        assertTrue(alerts.getAlerts(TODAY).stream()
                .noneMatch(a -> a.type() == AlertType.LOW_STOCK && a.category() == ItemCategory.SCBA));
    }

    @Test
    void lowStock_zeroThresholdMeansOff_andNoUsableUnitsIsHigh() throws Exception {
        rules.updateRules(new InventoryRulesDto(0, 30, List.of(new CategoryRuleDto(ItemCategory.FIRST_AID, 0, null))));
        assertTrue(alerts.getAlerts(TODAY).stream().noneMatch(a -> a.type() == AlertType.LOW_STOCK));

        rules.updateRules(new InventoryRulesDto(0, 30, List.of(new CategoryRuleDto(ItemCategory.FIRST_AID, 10_000, null))));
        ItemEntity kit = item("Kit", ItemCategory.FIRST_AID, ItemCondition.GOOD, 2);
        kit.setQuantityDispatched(2); // everything is out on an incident
        AlertResponse none = alerts.getAlerts(TODAY).stream()
                .filter(a -> a.type() == AlertType.LOW_STOCK && a.category() == ItemCategory.FIRST_AID).findFirst().orElseThrow();
        assertTrue(none.message().contains("0 usable"), none.message());
    }

    // ===== vehicles ===== //

    @Test
    void damagedVehicles_areHigh_butDamagedSmallEquipmentIsNot() throws Exception {
        noMaintenanceReminders();
        ItemEntity truck = item("Engine Broken", ItemCategory.ENGINE, ItemCondition.DAMAGED, 1);
        ItemEntity ambulance = item("Ambulance Broken", ItemCategory.AMBULANCE, ItemCondition.DAMAGED, 1);
        ItemEntity hose = item("Hose Torn", ItemCategory.HOSE, ItemCondition.DAMAGED, 1);

        assertEquals(AlertSeverity.HIGH, alertFor(truck, AlertType.VEHICLE_DAMAGED).orElseThrow().severity());
        assertTrue(alertFor(ambulance, AlertType.VEHICLE_DAMAGED).isPresent());
        assertTrue(alertFor(hose, AlertType.VEHICLE_DAMAGED).isEmpty());
    }

    @Test
    void alerts_listHighBeforeMedium() throws Exception {
        noMaintenanceReminders();
        ItemEntity soon = item("A Soon");
        soon.setExpiryDate(TODAY.plusDays(5));
        ItemEntity expired = item("Z Expired");
        expired.setExpiryDate(TODAY.minusDays(1));

        List<AlertResponse> all = alerts.getAlerts(TODAY);
        assertTrue(all.indexOf(alertFor(expired, AlertType.EXPIRED).orElseThrow())
                < all.indexOf(alertFor(soon, AlertType.EXPIRING_SOON).orElseThrow()));
    }
}
