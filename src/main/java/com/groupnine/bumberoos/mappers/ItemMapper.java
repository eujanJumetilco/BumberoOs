package com.groupnine.bumberoos.mappers;

import com.groupnine.bumberoos.domain.entities.ItemEntity;
import com.groupnine.bumberoos.dto.ItemRequest;
import com.groupnine.bumberoos.dto.ItemResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ItemMapper {

    public ItemEntity toEntity(ItemRequest request) {
        return new ItemEntity(
                request.condition(), request.name(),
                request.description(), request.quantity()
        );
    }

    public ItemResponse toResponse(ItemEntity item) {
        return new ItemResponse(
                item.getId(), item.getName(), item.getDescription(),
                item.getQuantity(), item.getItemCondition(), item.getLastMaintained()
        );
    }

    public List<ItemResponse> toResponses(List<ItemEntity> items) {
        return items.stream().map(this::toResponse).toList();
    }
}
