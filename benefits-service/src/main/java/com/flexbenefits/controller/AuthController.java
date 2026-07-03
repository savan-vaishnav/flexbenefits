package com.flexbenefits.controller;

import com.flexbenefits.config.TenantContext;
import com.flexbenefits.dto.AuthResponse;
import com.flexbenefits.dto.LoginRequest;
import com.flexbenefits.dto.RegisterRequest;
import com.flexbenefits.entity.Tenant;
import com.flexbenefits.entity.User;
import com.flexbenefits.entity.enums.Role;
import com.flexbenefits.exception.ResourceNotFoundException;
import com.flexbenefits.repository.TenantRepository;
import com.flexbenefits.repository.UserRepository;
import com.flexbenefits.service.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration attempt for email: {}", request.email());

        // Validate tenant exists
        Tenant tenant = tenantRepository.findById(request.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", request.tenantId()));

        // Check if email already taken for this tenant
        if (userRepository.existsByTenantIdAndEmail(request.tenantId(), request.email())) {
            throw new IllegalStateException("Email already registered for this tenant: " + request.email());
        }

        // Create user
        User user = User.builder()
                .tenant(tenant)
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .role(Role.EMPLOYEE)
                .active(true)
                .build();

        User saved = userRepository.save(user);

        // Generate JWT
        String token = jwtService.generateToken(
                saved.getId(), saved.getEmail(), tenant.getId(), saved.getRole().name()
        );

        log.info("User registered: {} for tenant: {}", saved.getEmail(), tenant.getCode());

        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponse(
                token, saved.getId(), tenant.getId(), saved.getEmail(), saved.getRole().name()
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login attempt for email: {}", request.email());

        // Authenticate (throws BadCredentialsException if invalid)
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        // Load user
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.email()));

        // Generate JWT
        String token = jwtService.generateToken(
                user.getId(), user.getEmail(), user.getTenant().getId(), user.getRole().name()
        );

        log.info("User logged in: {}", user.getEmail());

        return ResponseEntity.ok(new AuthResponse(
                token, user.getId(), user.getTenant().getId(), user.getEmail(), user.getRole().name()
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Generate a new JWT token
        String token = jwtService.generateToken(
                user.getId(), user.getEmail(), user.getTenant().getId(), user.getRole().name()
        );

        log.info("Token refreshed for user: {}", user.getEmail());

        return ResponseEntity.ok(new AuthResponse(
                token, user.getId(), user.getTenant().getId(), user.getEmail(), user.getRole().name()
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> me() {
        // This endpoint requires authentication (JWT) — SecurityConfig enforces it
        // TenantContext is set by JwtAuthenticationFilter
        // But /me is under /auth/**, which is permitAll. Let's keep it simple:
        // The user must send a valid JWT to get their info back.
        // We'll rely on SecurityContext for this.
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(new AuthResponse(
                null, user.getId(), user.getTenant().getId(), user.getEmail(), user.getRole().name()
        ));
    }
}

