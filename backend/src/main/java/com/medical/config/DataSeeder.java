package com.medical.config;

import com.medical.entity.*;
import com.medical.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seed(PasswordEncoder encoder,
                           UserRepository userRepo,
                           SimulatedPatientRepository patientRepo,
                           MedicalGuidelineRepository guidelineRepo) {
        return args -> {
            if (userRepo.count() > 0) return;

            String[][] users = {
                {"admin", "admin123", "System Admin", "ROLE_ADMIN"},
                {"doctor1", "doc123", "Doctor Zhang", "ROLE_DOCTOR"},
                {"doctor2", "doc123", "Doctor Li", "ROLE_DOCTOR"},
                {"followup1", "fol123", "Wang Followup", "ROLE_FOLLOWUP"},
                {"patient1", "pat123", "Patient A", "ROLE_PATIENT"},
                {"patient2", "pat123", "Patient B", "ROLE_PATIENT"},
            };
            for (String[] u : users) {
                var user = new User();
                user.setUsername(u[0]);
                user.setPassword(encoder.encode(u[1]));
                user.setDisplayName(u[2]);
                user.setRole(User.Role.valueOf(u[3]));
                user.setEnabled(true);
                userRepo.save(user);
            }

            String[][] patients = {
                {"Zhang San", "45", "Male", "Hypertension 3 years", "Penicillin", "A"},
                {"Li Si", "32", "Female", "None", "None", "B"},
                {"Wang Wu", "68", "Male", "Diabetes 10y, CAD post-stent 2y", "Sulfa", "O"},
                {"Zhao Liu", "28", "Female", "Migraine", "None", "AB"},
                {"Chen Qi", "55", "Male", "Hyperlipidemia, fatty liver", "None", "A"},
            };
            for (String[] p : patients) {
                var patient = new SimulatedPatient();
                patient.setName(p[0]);
                patient.setAge(Integer.parseInt(p[1]));
                patient.setGender(p[2]);
                patient.setMedicalHistory(p[3]);
                patient.setAllergies(p[4]);
                patient.setBloodType(p[5]);
                patientRepo.save(patient);
            }

            String[][] guidelines = {
                {"Hypertension Guideline", "Cardiology", "Diagnostic criteria: SBP >= 140mmHg...", "National Guideline", "2024"},
                {"Diabetes Guideline", "Endocrinology", "Diagnostic criteria: FPG >= 7.0mmol/L...", "National Guideline", "2024"},
                {"URTI Guideline", "Respiratory", "Most are viral, antibiotics not needed...", "National Guideline", "2024"},
                {"CAD Guideline", "Cardiology", "Stable angina: nitrates, beta-blockers...", "National Guideline", "2024"},
                {"Stroke Guideline", "Neurology", "FAST assessment: facial droop...", "National Guideline", "2024"},
                {"Fever Guideline", "Infectious", "Acute fever (<7d): common infections...", "National Guideline", "2024"},
            };
            for (String[] g : guidelines) {
                var guide = new MedicalGuideline();
                guide.setTitle(g[0]);
                guide.setCategory(g[1]);
                guide.setContent(g[2]);
                guide.setSource(g[3]);
                guide.setVersion(g[4]);
                guidelineRepo.save(guide);
            }

            System.out.println("Seed data initialized (users=" + userRepo.count() + ")");
        };
    }
}