package com.lexintel.contracts;

import de.codecentric.boot.admin.server.config.EnableAdminServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableAdminServer // Enables Spring Boot Admin Dashboard UI
public class LegalGraphRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(LegalGraphRagApplication.class, args);
    }
}