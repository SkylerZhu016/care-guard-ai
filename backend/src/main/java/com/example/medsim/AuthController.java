package com.example.medsim;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
class AuthController {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;

    AuthController(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwt) {
        this.users = users; this.passwordEncoder = passwordEncoder; this.jwt = jwt;
    }

    @PostMapping("/auth/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request) {
        var user = users.findByUsername(request.username()).filter(value -> value.enabled && passwordEncoder.matches(request.password(), value.passwordHash))
            .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "用户名或密码错误"));
        return new AuthResponse(jwt.issue(user), "Bearer", jwt.expiresInSeconds(), view(user));
    }

    @GetMapping("/me") UserView me(Authentication auth) {
        var principal = (AuthPrincipal) auth.getPrincipal();
        return new UserView(principal.id(), principal.username(), principal.displayName(), principal.role());
    }

    private UserView view(UserAccount user) { return new UserView(user.id, user.username, user.displayName, user.role); }
}

