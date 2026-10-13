package com.groupnine.bumberoos.controllers;

import com.groupnine.bumberoos.domain.exceptions.InvalidInventoryExceptions;
import com.groupnine.bumberoos.dto.ItemRequest;
import com.groupnine.bumberoos.dto.ItemResponse;
import com.groupnine.bumberoos.dto.ItemUpdateRequest;
import com.groupnine.bumberoos.mappers.ItemMapper;
import com.groupnine.bumberoos.services.InventoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/items")
public class InventoryController {

    // ------------------ DEPENDENCY SETUP ------------------ //

    private final InventoryService inventoryService;
    private final ItemMapper itemMapper;

    public InventoryController(InventoryService inventoryService, ItemMapper itemMapper){
        this.inventoryService = inventoryService;
        this.itemMapper = itemMapper;
    }

    // ------------------ CONTROLLERS ------------------ //

    @GetMapping
    public List<ItemResponse> getAll() {
        return itemMapper.toResponses(inventoryService.getAllItems());
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
                        r.condition(), r.lastMaintained())
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

    private ResponseStatusException notFound(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "item " + id + " not found");
    }
}
