package com.vulnaudit.service;

import com.vulnaudit.dto.auth.LoginRequestDTO;
import com.vulnaudit.dto.auth.LoginResponseDTO;
import com.vulnaudit.entity.UserEntity;
import com.vulnaudit.exception.UnauthorizedException;
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @Transactional
    public LoginResponseDTO login(LoginRequestDTO request) {
        UserEntity user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UnauthorizedException("用户名或密码错误"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("用户名或密码错误");
        }

        if (user.getStatus() != UserEntity.UserStatus.active) {
            throw new UnauthorizedException("账户已被禁用");
        }

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        String accessToken = tokenProvider.generateAccessToken(user.getId(), user.getUsername());
        String refreshToken = tokenProvider.generateRefreshToken(user.getId());

        return LoginResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(86400L)
                .user(LoginResponseDTO.UserInfoDTO.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .role("USER")
                        .build())
                .build();
    }
}
