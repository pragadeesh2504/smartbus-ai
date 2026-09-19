package com.smartbus.config;

import com.smartbus.domain.model.Role;
import com.smartbus.domain.model.User;
import com.smartbus.infrastructure.adapter.jpa.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SuperAdminBootstrapService implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.core.env.Environment environment;

    @Value("${SUPERADMIN_EMAIL:superadmin@smartbus.com}")
    private String configuredSuperAdminEmail;

    @Value("${SUPERADMIN_PASSWORD:#{null}}")
    private String configuredSuperAdminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = configuredSuperAdminEmail != null && !configuredSuperAdminEmail.isBlank()
                ? configuredSuperAdminEmail.trim().toLowerCase()
                : "superadmin@smartbus.com";

        boolean superAdminExists = userRepository.existsByRole(Role.SUPER_ADMIN);

        if (!superAdminExists) {
            String initialPassword = configuredSuperAdminPassword;
            boolean isLocalOrTest = java.util.Arrays.stream(environment.getActiveProfiles())
                    .anyMatch(p -> p.equalsIgnoreCase("local") || p.equalsIgnoreCase("test") || p.equalsIgnoreCase("dev"));

            if (initialPassword == null || initialPassword.isBlank()) {
                if (!isLocalOrTest) {
                    throw new IllegalStateException(
                            "CRITICAL STARTUP FAILURE: SUPERADMIN_PASSWORD environment variable is mandatory in production. " +
                            "Cannot bootstrap platform owner without an explicit production secret. Ephemeral password generation is disabled in production."
                    );
                }
                byte[] randomBytes = new byte[16];
                new SecureRandom().nextBytes(randomBytes);
                initialPassword = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
                log.warn("SUPERADMIN_PASSWORD not set in local/dev environment. Generated ephemeral bootstrap password for SuperAdmin: {}", initialPassword);
            }

            User superAdmin = User.builder()
                    .email(email)
                    .passwordHash(passwordEncoder.encode(initialPassword))
                    .firstName("Super")
                    .lastName("Admin")
                    .phoneNumber("+910000000000")
                    .role(Role.SUPER_ADMIN)
                    .college(null)
                    .isActive(true)
                    .build();

            userRepository.save(superAdmin);
            log.info("Successfully bootstrapped single platform SuperAdmin account: {}", email);
        } else {
            log.info("SuperAdmin account already exists. Preserving existing account and credentials.");
        }
    }
}
