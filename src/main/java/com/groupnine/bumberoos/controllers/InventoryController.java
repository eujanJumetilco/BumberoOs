package com.groupnine.bumberoos.controllers;

import com.groupnine.bumberoos.domain.entities.ItemEntity;
import com.groupnine.bumberoos.domain.enums.ItemCategory;
import com.groupnine.bumberoos.domain.enums.ItemCondition;
import com.groupnine.bumberoos.domain.exceptions.InvalidInventoryExceptions;
import com.groupnine.bumberoos.dto.AlertResponse;
import com.groupnine.bumberoos.dto.ItemRequest;
import com.groupnine.bumberoos.dto.ItemResponse;
import com.groupnine.bumberoos.dto.ItemUpdateRequest;
import com.groupnine.bumberoos.mappers.ItemMapper;
import com.groupnine.bumberoos.services.InventoryAlertService;
import com.groupnine.bumberoos.services.InventoryService;
import com.groupnine.bumberoos.util.CsvSupport;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/items")
public class InventoryController {

    // ------------------ DEPENDENCY SETUP ------------------ //

    private static final ZoneId STATION_ZONE = ZoneId.of("Asia/Manila");

    /** Export column order. ID, Dispatched and Available are informational: import ignores them. */
    private static final List<String> CSV_HEADERS = List.of(
            "ID", "Name", "Description", "Category", "Serial Number", "Condition", "Quantity",
            "Dispatched", "Available", "Last Maintained", "Expiry Date");

    private final InventoryService inventoryService;
    private final InventoryAlertService inventoryAlertService;
    private final ItemMapper itemMapper;

    public InventoryController(
            InventoryService inventoryService, InventoryAlertService inventoryAlertService, ItemMapper itemMapper
    ){
        this.inventoryService = inventoryService;
        this.inventoryAlertService = inventoryAlertService;
        this.itemMapper = itemMapper;
    }

    // ------------------ CONTROLLERS ------------------ //

    @GetMapping
    public List<ItemResponse> getAll(
            @RequestParam(required = false) ItemCategory category,
            @RequestParam(defaultValue = "false") boolean availableOnly
    ) {
        return itemMapper.toResponses(inventoryService.getItems(category, availableOnly));
    }

    /** Expired, expiring, overdue-for-maintenance, low-stock and damaged-vehicle alerts. Dates use Philippine time. */
    @GetMapping("/alerts")
    public List<AlertResponse> alerts() {
        return inventoryAlertService.getAlerts(LocalDate.now(STATION_ZONE));
    }

    @GetMapping("/{id}")
    public ItemResponse getById(@PathVariable Long id) {
        return inventoryService.findById(id).map(itemMapper::toResponse)
                .orElseThrow(() -> notFound(id));
    }

    @GetMapping("/search")
    public ItemResponse getByName(@RequestParam String name) {
        return inventoryService.findByName(name).map(itemMapper::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "item '" + name + "' not found"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ItemResponse add(@Valid @RequestBody ItemRequest request) throws InvalidInventoryExceptions {
        return itemMapper.toResponse(inventoryService.addItem(itemMapper.toEntity(request)));
    }

    @PatchMapping("/{id}")
    public ItemResponse edit(@PathVariable Long id, @Valid @RequestBody ItemUpdateRequest r) {
        return inventoryService.editItem(id, r.name(), r.description(), r.quantity(),
                        r.condition(), r.lastMaintained(), r.category(), r.serialNumber(), r.expiryDate())
                .map(itemMapper::toResponse)
                .orElseThrow(() -> notFound(id));
    }

    @PostMapping("/{id}/increase")
    public ItemResponse increaseQuantity(@PathVariable Long id, @RequestParam int amount) {
        return inventoryService.increaseQuantity(id, amount).map(itemMapper::toResponse)
                .orElseThrow(() -> notFound(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long id) {
        if (!inventoryService.removeItem(id)) throw notFound(id);
    }

    // ------------------ CSV / PSV EXPORT & IMPORT ------------------ //

    /** GET /api/items/export?format=csv|psv[&category=...]. Dates are ISO (YYYY-MM-DD). */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportFile(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) ItemCategory category
    ) {
        List<List<Object>> rows = new ArrayList<>();
        for (ItemEntity i : inventoryService.getItems(category, false)) {
            rows.add(CsvSupport.row(
                    i.getId(), CsvSupport.safe(i.getName()), CsvSupport.safe(i.getDescription()),
                    CsvSupport.name(i.getCategory()), CsvSupport.safe(i.getSerialNumber()),
                    CsvSupport.name(i.getItemCondition()), i.getQuantity(),
                    i.getQuantityDispatched(), i.getQuantityAvailable(),
                    i.getLastMaintained(), i.getExpiryDate()));
        }
        return CsvSupport.export("inventory", CsvSupport.Format.of(format), CSV_HEADERS, rows);
    }

    /**
     * POST /api/items/import (multipart field "file", CSV or PSV). Every row becomes a NEW item with nothing
     * dispatched. Required: Name, Quantity. Condition defaults to GOOD and Category to OTHER.
     * All-or-nothing: one bad row rejects the whole file.
     */
    @PostMapping("/import")
    @Transactional(rollbackFor = Exception.class)
    public CsvSupport.ImportResult importFile(@RequestParam("file") MultipartFile file) {
        CsvSupport.Table table = CsvSupport.parse(file);
        table.require(CSV_HEADERS, "name", "quantity");
        return CsvSupport.importRows(table, this::toEntity, inventoryService::addItem);
    }

    private ItemEntity toEntity(CsvSupport.Row row) {
        String name = row.get("name", "equipment", "item");
        if (name == null) throw new IllegalArgumentException("Name is required.");

        Integer quantity = CsvSupport.parseInt(row.get("quantity"), "Quantity");
        if (quantity == null || quantity < 0) {
            throw new IllegalArgumentException("Quantity is required and must be a whole number, 0 or more.");
        }

        ItemCondition condition = CsvSupport.parseEnum(ItemCondition.class, row.get("condition"), "Condition");
        ItemCategory category = CsvSupport.parseEnum(ItemCategory.class, row.get("category"), "Category");

        ItemEntity item = new ItemEntity(
                condition == null ? ItemCondition.GOOD : condition,
                name, row.get("description"), quantity,
                category == null ? ItemCategory.OTHER : category,
                row.get("serial number", "serial"));
        item.setLastMaintained(CsvSupport.parseDate(row.get("last maintained", "maintained"), "Last Maintained"));
        item.setExpiryDate(CsvSupport.parseDate(row.get("expiry date", "expiry"), "Expiry Date"));
        return item;
    }

    private ResponseStatusException notFound(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "item " + id + " not found");
    }
}
