package com.example.payflow.service;

import com.example.payflow.dto.LoginRequest;
import com.example.payflow.dto.LoginResponse;
import com.example.payflow.entity.User;
import com.example.payflow.exception.InvalidCredentialsException;
import com.example.payflow.repository.UserRepository;
import com.example.payflow.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    // Compared against when the UPI ID is unknown, so both failure paths do the same BCrypt work and take
    // about the same time; otherwise response timing would reveal which UPI IDs are registered
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode("timing-equaliser");
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUpiId(UserService.normalizeUpiId(request.upiId()));
        String hash = user == null || user.getPasswordHash() == null ? dummyHash : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user == null || user.getPasswordHash() == null || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        return new LoginResponse(jwtService.generateToken(user), "Bearer", jwtService.getExpiration().toSeconds(),
                user.getUserId(), user.getUpiId(), user.getRole());
    }
}
