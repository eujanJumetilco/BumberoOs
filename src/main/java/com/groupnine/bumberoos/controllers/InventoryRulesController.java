package com.groupnine.bumberoos.controllers;

import com.groupnine.bumberoos.dto.InventoryRulesDto;
import com.groupnine.bumberoos.services.InventoryRulesService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings/inventory-rules")
public class InventoryRulesController {

    private final InventoryRulesService rulesService;

    public InventoryRulesController(InventoryRulesService rulesService) {
        this.rulesService = rulesService;
    }

    /** Any signed-in user can read the rules. */
    @GetMapping
    public InventoryRulesDto get() {
        return rulesService.getRules();
    }

    /** Admin only (see SecurityConfig). Replaces the rules. */
    @PutMapping
    public InventoryRulesDto update(@Valid @RequestBody InventoryRulesDto request) {
        return rulesService.updateRules(request);
    }
}
