package com.smartbus.application.service;

import com.smartbus.domain.model.Role;
import com.smartbus.domain.model.User;
import com.smartbus.infrastructure.adapter.jpa.UserRepository;
import com.smartbus.infrastructure.dto.RegisterRequest;
import com.smartbus.domain.exception.BadRequestException;
import com.smartbus.infrastructure.adapter.jpa.StudentRepository;
import com.smartbus.infrastructure.adapter.jpa.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private com.smartbus.infrastructure.adapter.jpa.CollegeRepository collegeRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setEmail("test@smartbus.edu");
        registerRequest.setPassword("password123");
        registerRequest.setFirstName("John");
        registerRequest.setLastName("Doe");
        registerRequest.setPhoneNumber("1234567890");
        registerRequest.setRole("STUDENT");
        registerRequest.setStudentId("ST-101");
        registerRequest.setDepartment("CSE");
        registerRequest.setBatch("2024");
        registerRequest.setCollegeCode("CIT");
    }

    @Test
    void registerUser_Success() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_password");
        
        com.smartbus.domain.model.College college = com.smartbus.domain.model.College.builder()
                .id(java.util.UUID.randomUUID())
                .name("Test College")
                .collegeCode("CIT")
                .status("ACTIVE")
                .build();
        when(collegeRepository.findByCollegeCodeIgnoreCaseAndStatus("CIT", "ACTIVE")).thenReturn(Optional.of(college));

        User mockUser = User.builder()
                .email(registerRequest.getEmail())
                .passwordHash("hashed_password")
                .role(Role.STUDENT)
                .college(college)
                .build();
        when(userRepository.save(any(User.class))).thenReturn(mockUser);

        assertDoesNotThrow(() -> authService.register(registerRequest));
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void registerUser_EmailAlreadyExists_ThrowsException() {
        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        Exception exception = assertThrows(BadRequestException.class, () -> {
            authService.register(registerRequest);
        });

        assertEquals("Email address already in use!", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }
}
