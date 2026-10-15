package com.groupnine.bumberoos.services;

import com.groupnine.bumberoos.domain.entities.InventoryRuleEntity;
import com.groupnine.bumberoos.domain.entities.InventorySettingsEntity;
import com.groupnine.bumberoos.domain.enums.ItemCategory;
import com.groupnine.bumberoos.domain.exceptions.BadRequestException;
import com.groupnine.bumberoos.dto.CategoryRuleDto;
import com.groupnine.bumberoos.dto.InventoryRulesDto;
import com.groupnine.bumberoos.repositories.InventoryRuleRepository;
import com.groupnine.bumberoos.repositories.InventorySettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Reads and replaces the station-wide and per-category inventory rules. */
@Service
@Transactional
public class InventoryRulesService {

    private final InventorySettingsRepository settingsRepository;
    private final InventoryRuleRepository ruleRepository;

    public InventoryRulesService(InventorySettingsRepository settingsRepository, InventoryRuleRepository ruleRepository) {
        this.settingsRepository = settingsRepository;
        this.ruleRepository = ruleRepository;
    }

    /** Always lists every category, filling in defaults for those never configured. */
    @Transactional(readOnly = true)
    public InventoryRulesDto getRules() {
        InventorySettingsEntity settings = settingsRepository.findById(InventorySettingsEntity.ID)
                .orElseGet(InventorySettingsEntity::new);
        Map<ItemCategory, InventoryRuleEntity> byCategory = ruleRepository.findAll().stream()
                .collect(Collectors.toMap(InventoryRuleEntity::getCategory, Function.identity()));

        List<CategoryRuleDto> categories = new ArrayList<>();
        for (ItemCategory category : ItemCategory.values()) {
            InventoryRuleEntity rule = byCategory.get(category);
            categories.add(rule == null
                    ? new CategoryRuleDto(category, 0, null)
                    : new CategoryRuleDto(category, rule.getLowStockThreshold(), rule.getMaintenanceIntervalDays()));
        }
        return new InventoryRulesDto(settings.getDefaultMaintenanceIntervalDays(), settings.getExpiryWarningDays(), categories);
    }

    /**
     * Replaces the rules. Categories left out of the request keep their current rule.
     *
     * @throws BadRequestException if a category appears twice
     */
    public InventoryRulesDto updateRules(InventoryRulesDto request) {
        InventorySettingsEntity settings = settingsRepository.findById(InventorySettingsEntity.ID)
                .orElseGet(InventorySettingsEntity::new);
        settings.setDefaultMaintenanceIntervalDays(request.defaultMaintenanceIntervalDays());
        settings.setExpiryWarningDays(request.expiryWarningDays());
        settingsRepository.save(settings);

        EnumSet<ItemCategory> seen = EnumSet.noneOf(ItemCategory.class);
        if (request.categories() != null) {
            for (CategoryRuleDto dto : request.categories()) {
                if (!seen.add(dto.category())) {
                    throw new BadRequestException("category " + dto.category() + " appears more than once");
                }
                InventoryRuleEntity rule = ruleRepository.findByCategory(dto.category())
                        .orElseGet(() -> new InventoryRuleEntity(dto.category(), 0, null));
                rule.setLowStockThreshold(dto.lowStockThreshold());
                rule.setMaintenanceIntervalDays(dto.maintenanceIntervalDays());
                ruleRepository.save(rule);
            }
        }
        return getRules();
    }
}
