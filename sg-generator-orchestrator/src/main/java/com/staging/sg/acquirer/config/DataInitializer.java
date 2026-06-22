package com.staging.sg.acquirer.config;

import com.staging.sg.common.entity.User;
import com.staging.sg.common.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.login:admin}")
    private String adminLogin;

    @Value("${app.admin.password:Admin123!}")
    private String adminPassword;

    @Value("${app.admin.email:admin@staging.com}")
    private String adminEmail;

    public DataInitializer(UserRepository userRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository  = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void init() {
        if (!userRepository.existsByLogin(adminLogin)) {
            User admin = new User();
            admin.setLogin(adminLogin);
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setEmail(adminEmail);
            admin.setRole("ADMIN");
            admin.setActive(true);
            admin.setCreatedBy("system");
            userRepository.save(admin);
            log.info("[INIT] Admin user created — login={}", adminLogin);
        } else {
            log.info("[INIT] Admin user already exists — login={}", adminLogin);
        }
    }
}
