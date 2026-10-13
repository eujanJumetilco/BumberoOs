package com.groupnine.bumberoos.mappers;

import com.groupnine.bumberoos.domain.entities.UserAccountEntity;
import com.groupnine.bumberoos.dto.UserAccountResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserAccountMapper {

    public UserAccountResponse toResponse(UserAccountEntity account) {
        return new UserAccountResponse(
                account.getId(), account.getEmail(), account.getDisplayName(),
                account.getRole(), account.isEnabled(),
                account.getStaff() == null ? null : account.getStaff().getId(),
                account.getCreatedAt()
        );
    }

    public List<UserAccountResponse> toResponses(List<UserAccountEntity> accounts) {
        return accounts.stream().map(this::toResponse).toList();
    }
}
