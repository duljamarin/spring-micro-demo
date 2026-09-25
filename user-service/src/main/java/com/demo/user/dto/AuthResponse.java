package com.demo.user.dto;

public record AuthResponse(String token, long expiresInSeconds, Long userId, String username) {
}
