package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.enums.ItemCategory;
import com.groupnine.bumberoos.domain.exceptions.BadRequestException;
import com.groupnine.bumberoos.dto.CategoryRuleDto;
import com.groupnine.bumberoos.dto.InventoryRulesDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class InventoryRulesServiceTest {

    @Autowired InventoryRulesService rules;

    private CategoryRuleDto rule(InventoryRulesDto dto, ItemCategory category) {
        return dto.categories().stream().filter(c -> c.category() == category).findFirst().orElseThrow();
    }

    @Test
    void defaults_listEveryCategory_withNoLowStockAndInheritedInterval() {
        InventoryRulesDto dto = rules.getRules();

        assertEquals(180, dto.defaultMaintenanceIntervalDays());
        assertEquals(30, dto.expiryWarningDays());
        assertEquals(ItemCategory.values().length, dto.categories().size());
        dto.categories().forEach(c -> {
            assertEquals(0, c.lowStockThreshold());
            assertNull(c.maintenanceIntervalDays());
        });
    }

    @Test
    void update_savesSettingsAndCategories_andKeepsCategoriesLeftOut() {
        rules.updateRules(new InventoryRulesDto(120, 45, List.of(
                new CategoryRuleDto(ItemCategory.SCBA, 4, 60),
                new CategoryRuleDto(ItemCategory.HOSE, 10, null))));

        InventoryRulesDto saved = rules.updateRules(new InventoryRulesDto(90, 14, List.of(
                new CategoryRuleDto(ItemCategory.HOSE, 12, 0))));

        assertEquals(90, saved.defaultMaintenanceIntervalDays());
        assertEquals(14, saved.expiryWarningDays());
        assertEquals(12, rule(saved, ItemCategory.HOSE).lowStockThreshold());
        assertEquals(0, rule(saved, ItemCategory.HOSE).maintenanceIntervalDays());
        assertEquals(4, rule(saved, ItemCategory.SCBA).lowStockThreshold(), "left out, so unchanged");
        assertEquals(60, rule(saved, ItemCategory.SCBA).maintenanceIntervalDays());
        assertEquals(saved, rules.getRules());
    }

    @Test
    void update_nullIntervalGoesBackToTheDefault() {
        rules.updateRules(new InventoryRulesDto(90, 30, List.of(new CategoryRuleDto(ItemCategory.ENGINE, 0, 30))));
        InventoryRulesDto saved = rules.updateRules(new InventoryRulesDto(90, 30, List.of(
                new CategoryRuleDto(ItemCategory.ENGINE, 0, null))));

        assertNull(rule(saved, ItemCategory.ENGINE).maintenanceIntervalDays());
    }

    @Test
    void update_rejectsDuplicateCategories() {
        assertThrows(BadRequestException.class, () -> rules.updateRules(new InventoryRulesDto(90, 30, List.of(
                new CategoryRuleDto(ItemCategory.HOSE, 1, null), new CategoryRuleDto(ItemCategory.HOSE, 2, null)))));
    }
}
