package com.example.medsim;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
class DemoDataConfig {
    @Bean
    CommandLineRunner seedDemoUsers(UserRepository users, PasswordEncoder encoder) {
        return args -> {
            seed(users, encoder, "patient", "患者用户", Role.PATIENT);
            seed(users, encoder, "clinician", "医务人员", Role.CLINICIAN);
            seed(users, encoder, "followup", "随访人员", Role.FOLLOWUP_STAFF);
            seed(users, encoder, "admin", "系统管理员", Role.ADMIN);
        };
    }

    private void seed(UserRepository users, PasswordEncoder encoder, String username, String name, Role role) {
        if (users.findByUsername(username).isEmpty()) users.save(UserAccount.create(username, name, encoder.encode("Demo123!"), role));
    }
}

