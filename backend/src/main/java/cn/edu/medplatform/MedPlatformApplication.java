package cn.edu.medplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MedPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(MedPlatformApplication.class, args);
    }
}
