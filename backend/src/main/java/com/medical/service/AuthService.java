package com.medical.service;

import com.medical.dto.LoginRequest;
import com.medical.dto.LoginResponse;
import com.medical.entity.User;
import com.medical.repository.UserRepository;
import com.medical.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public LoginResponse login(LoginRequest req) {
        var user = userRepository.findByUsername(req.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new RuntimeException("Password error");
        }

        if (!user.isEnabled()) {
            throw new RuntimeException("Account disabled");
        }

        String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRole().name());
        var resp = new LoginResponse();
        resp.setToken(token);
        resp.setUsername(user.getUsername());
        resp.setDisplayName(user.getDisplayName());
        resp.setRole(user.getRole().name());
        resp.setMessage("Login success");
        return resp;
    }

    public void initDefaultUsers() {
        if (userRepository.count() > 0) return;

        createUser("admin", "admin123", "System Admin", User.Role.ROLE_ADMIN);
        createUser("doctor1", "doc123", "Doctor Zhang", User.Role.ROLE_DOCTOR);
        createUser("doctor2", "doc123", "Doctor Li", User.Role.ROLE_DOCTOR);
        createUser("followup1", "fol123", "Wang Followup", User.Role.ROLE_FOLLOWUP);
        createUser("patient1", "pat123", "Patient A", User.Role.ROLE_PATIENT);
        createUser("patient2", "pat123", "Patient B", User.Role.ROLE_PATIENT);
    }

    private void createUser(String username, String password, String displayName, User.Role role) {
        var user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setDisplayName(displayName);
        user.setRole(role);
        user.setEnabled(true);
        userRepository.save(user);
    }
}