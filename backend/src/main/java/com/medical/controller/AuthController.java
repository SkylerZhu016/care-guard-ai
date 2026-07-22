package com.medical.controller;

import com.medical.dto.ApiResponse;
import com.medical.dto.LoginRequest;
import com.medical.dto.LoginResponse;
import com.medical.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            return ApiResponse.success(authService.login(request));
        } catch (RuntimeException e) {
            return ApiResponse.error(401, e.getMessage());
        }
    }

    @PostMapping("/init")
    public ApiResponse<String> init() {
        authService.initDefaultUsers();
        return ApiResponse.success("Default users initialized");
    }
}