package com.railway.user.service;

import com.railway.common.exception.BusinessException;
import com.railway.common.exception.DuplicateResourceException;
import com.railway.common.exception.ResourceNotFoundException;
import com.railway.user.dto.*;
import com.railway.user.entity.User;
import com.railway.user.repository.UserRepository;
import com.railway.user.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("User", request.email());
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .phone(request.phone())
                .build();

        user = userRepository.save(user);
        return generateTokens(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException("INVALID_CREDENTIALS", "Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException("INVALID_CREDENTIALS", "Invalid email or password");
        }

        return generateTokens(user);
    }

    public AuthResponse refresh(RefreshTokenRequest request) {
        if (!jwtProvider.validateToken(request.refreshToken())) {
            throw new BusinessException("INVALID_TOKEN", "Invalid or expired refresh token");
        }

        String email = jwtProvider.getEmailFromToken(request.refreshToken());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        return generateTokens(user);
    }

    private AuthResponse generateTokens(User user) {
        String accessToken = jwtProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtProvider.generateRefreshToken(user.getId(), user.getEmail(), user.getRole().name());
        return new AuthResponse(accessToken, refreshToken, jwtProvider.getAccessTokenExpirationMs());
    }
}
