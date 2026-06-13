package com.staging.sg.service;

import com.staging.sg.dto.CreateUserRequest;
import com.staging.sg.dto.UserDto;
import com.staging.sg.entity.Role;
import com.staging.sg.entity.Test;
import com.staging.sg.entity.User;
import com.staging.sg.repository.TestRepository;
import com.staging.sg.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository  userRepository;
    private final TestRepository  testRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       TestRepository testRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository  = userRepository;
        this.testRepository  = testRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserDto> findAll() {
        return userRepository.findAll().stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    public UserDto findById(Long id) {
        return userRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new RuntimeException("User not found : " + id));
    }

    @Transactional
    public UserDto create(CreateUserRequest req, String createdBy) {
        if (userRepository.existsByLogin(req.getLogin()))
            throw new RuntimeException("Login already exists : " + req.getLogin());
        User user = new User();
        user.setLogin(req.getLogin());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setEmail(req.getEmail());
        user.setRole(req.getRole() != null ? req.getRole() : Role.EXPLOITATION);
        user.setActive(true);
        user.setCreatedBy(createdBy);
        User saved = userRepository.save(user);
        log.info("[USER] Created — login={} role={}", saved.getLogin(), saved.getRole());
        return toDto(saved);
    }

    @Transactional
    public UserDto update(Long id, CreateUserRequest req) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found : " + id));
        if (req.getEmail()    != null) user.setEmail(req.getEmail());
        if (req.getRole()     != null) user.setRole(req.getRole());
        if (req.getPassword() != null && !req.getPassword().isBlank())
            user.setPassword(passwordEncoder.encode(req.getPassword()));
        return toDto(userRepository.save(user));
    }

    @Transactional
    public void toggleActive(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found : " + id));
        user.setActive(!user.isActive());
        userRepository.save(user);
    }

    @Transactional
    public void assignTest(Long userId, Long testId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found : " + userId));
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new RuntimeException("Test not found : " + testId));
        user.getAssignedTests().add(test);
        userRepository.save(user);
        log.info("[USER] Test {} assigned to user {}", testId, userId);
    }

    @Transactional
    public void unassignTest(Long userId, Long testId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found : " + userId));
        user.getAssignedTests().removeIf(t -> t.getId().equals(testId));
        userRepository.save(user);
        log.info("[USER] Test {} unassigned from user {}", testId, userId);
    }

    private UserDto toDto(User u) {
        UserDto dto = new UserDto();
        dto.setId(u.getId());
        dto.setLogin(u.getLogin());
        dto.setEmail(u.getEmail());
        dto.setRole(u.getRole());
        dto.setActive(u.isActive());
        return dto;
    }
}
