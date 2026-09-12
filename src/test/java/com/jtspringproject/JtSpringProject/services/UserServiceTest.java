package com.jtspringproject.JtSpringProject.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.jtspringproject.JtSpringProject.dao.userDao;
import com.jtspringproject.JtSpringProject.exception.BusinessRuleException;
import com.jtspringproject.JtSpringProject.exception.ResourceNotFoundException;
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
        when(userDao.getAllUser()).thenReturn(List.of(testUser));

        assertEquals(1, userService.getUsers().size());
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

    /**
     * Reads must not write.
     *
     * <p>This lookup used to re-hash and persist plain-text passwords on every
     * call. Because it runs during authentication, before credentials are
     * checked, any request naming a known username triggered a bcrypt hash and a
     * database write. Seeded passwords are hashed by migration V4 instead.
     */
    @Test
    void getUserByUsername_shouldNotWriteToTheDatabase() {
        when(userDao.getUserByUsername("testuser")).thenReturn(testUser);

        User result = userService.getUserByUsername("testuser");

        assertNotNull(result);
        assertEquals("plaintext123", result.getPassword());
        verify(userDao, never()).saveUser(any(User.class));
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void getUserByUsername_shouldReturnNullWhenNotFound() {
        when(userDao.getUserByUsername("unknown")).thenReturn(null);

        assertNull(userService.getUserByUsername("unknown"));
    }

    @Test
    void requireUserByUsername_shouldThrowWhenNotFound() {
        when(userDao.getUserByUsername("unknown")).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> userService.requireUserByUsername("unknown"));
    }

    @Test
    void updateUserProfile_shouldUpdateAllFields() {
        when(userDao.getUserById(1)).thenReturn(testUser);
        when(userDao.userExists("newuser")).thenReturn(false);
        when(passwordEncoder.encode("newpass")).thenReturn("$2a$newencoded");
        when(userDao.saveUser(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.updateUserProfile(1, "newuser", "new@email.com", "newpass", "456 New St");

        assertNotNull(result);
        assertEquals("newuser", result.getUsername());
        assertEquals("new@email.com", result.getEmail());
        assertEquals("456 New St", result.getAddress());
        assertEquals("$2a$newencoded", result.getPassword());
    }

    @Test
    void updateUserProfile_shouldKeepPasswordWhenBlank() {
        when(userDao.getUserById(1)).thenReturn(testUser);
        when(userDao.saveUser(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.updateUserProfile(1, "testuser", "new@email.com", "   ", "456 New St");

        assertEquals("plaintext123", result.getPassword());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void updateUserProfile_shouldRejectTakenUsername() {
        when(userDao.getUserById(1)).thenReturn(testUser);
        when(userDao.userExists("taken")).thenReturn(true);

        assertThrows(BusinessRuleException.class, () ->
                userService.updateUserProfile(1, "taken", "a@b.com", null, "addr"));
    }

    @Test
    void updateUserProfile_shouldThrowWhenUserNotFound() {
        when(userDao.getUserById(999)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () ->
                userService.updateUserProfile(999, "user", "email", "pass", "addr"));
    }

    @Test
    void register_shouldRejectDuplicateUsername() {
        when(userDao.userExists("testuser")).thenReturn(true);

        assertThrows(BusinessRuleException.class, () ->
                userService.register("testuser", "a@b.com", "password123", "addr"));

        verify(userDao, never()).saveUser(any(User.class));
    }

    @Test
    void register_shouldAssignNormalRole() {
        when(userDao.userExists("fresh")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$encoded");
        when(userDao.saveUser(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.register("fresh", "a@b.com", "password123", "addr");

        assertEquals(User.ROLE_NORMAL, result.getRole());
        assertEquals("$2a$encoded", result.getPassword());
    }
}
