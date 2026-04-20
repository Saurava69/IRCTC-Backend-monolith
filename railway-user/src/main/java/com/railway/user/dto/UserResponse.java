package com.railway.user.dto;

import com.railway.user.entity.Role;

import java.time.Instant;

public record UserResponse(
        Long id,
        String email,
        String phone,
        String fullName,
        Role role,
        Instant createdAt
) {
}
