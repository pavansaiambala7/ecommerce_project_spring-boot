package com.jtspringproject.JtSpringProject.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.jtspringproject.JtSpringProject.dao.userDao;
import com.jtspringproject.JtSpringProject.models.User;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private userDao userDao;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private userService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPassword("plaintext123");
        testUser.setRole("ROLE_NORMAL");
        testUser.setAddress("123 Test St");
    }

    @Test
    void getUsers_shouldReturnAllUsers() {
        when(userDao.getAllUser()).thenReturn(Arrays.asList(testUser));

        List<User> result = userService.getUsers();

        assertEquals(1, result.size());
        assertEquals("testuser", result.get(0).getUsername());
    }

    @Test
    void addUser_shouldEncodePasswordAndSave() {
        when(passwordEncoder.encode("plaintext123")).thenReturn("$2a$encoded");
        when(userDao.saveUser(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.addUser(testUser);

        assertNotNull(result);
        assertEquals("$2a$encoded", result.getPassword());
        verify(passwordEncoder).encode("plaintext123");
    }

    @Test
    void checkUserExists_shouldReturnTrueWhenExists() {
        when(userDao.userExists("testuser")).thenReturn(true);

        assertTrue(userService.checkUserExists("testuser"));
    }

    @Test
    void checkUserExists_shouldReturnFalseWhenNotExists() {
        when(userDao.userExists("unknown")).thenReturn(false);

        assertFalse(userService.checkUserExists("unknown"));
    }

    @Test
    void getUserByUsername_shouldMigratePlainTextPassword() {
        when(userDao.getUserByUsername("testuser")).thenReturn(testUser);
        when(passwordEncoder.encode("plaintext123")).thenReturn("$2a$encoded");
        when(userDao.saveUser(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.getUserByUsername("testuser");

        assertNotNull(result);
        assertEquals("$2a$encoded", result.getPassword());
    }

    @Test
    void getUserByUsername_shouldReturnNullWhenNotFound() {
        when(userDao.getUserByUsername("unknown")).thenReturn(null);

        User result = userService.getUserByUsername("unknown");

        assertNull(result);
    }

    @Test
    void updateUserProfile_shouldUpdateAllFields() {
        when(userDao.getUserById(1)).thenReturn(testUser);
        when(passwordEncoder.encode("newpass")).thenReturn("$2a$newencoded");
        when(userDao.saveUser(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.updateUserProfile(1, "newuser", "new@email.com", "newpass", "456 New St");

        assertNotNull(result);
        assertEquals("newuser", result.getUsername());
        assertEquals("new@email.com", result.getEmail());
        assertEquals("456 New St", result.getAddress());
    }

    @Test
    void updateUserProfile_shouldReturnNullWhenUserNotFound() {
        when(userDao.getUserById(999)).thenReturn(null);

        User result = userService.updateUserProfile(999, "user", "email", "pass", "addr");

        assertNull(result);
    }
}
