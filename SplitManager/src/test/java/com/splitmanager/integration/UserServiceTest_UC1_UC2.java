package com.splitmanager.integration;

import com.splitmanager.businesslogic.service.UserService;
import com.splitmanager.dao.ConnectionManager;
import com.splitmanager.dao.UserDAO;
import com.splitmanager.domain.registry.User;
import com.splitmanager.exception.DomainException;
import com.splitmanager.exception.EntityNotFoundException;

import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration Test (Grey-Box) for UserService.
 *
 * Tests UC1 (Sign Up) and UC2 (Login) with REAL database.
 *
 * NO MOCKS - Uses real DAO and H2 database.
 * Database is cleaned before each test to ensure isolation.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserServiceTest_UC1_UC2 {

    private ConnectionManager connMgr;
    private UserService userService;
    private UserDAO userDAO;

    @BeforeEach
    void setUp() throws SQLException {
        // Initialize real database connection
        connMgr = ConnectionManager.getInstance();

        // Clean database before each test
        cleanDatabase();

        // Initialize REAL DAO (no mocks!)
        userDAO = new UserDAO();

        // Initialize UserService with real DAO
        userService = new UserService(userDAO);
    }

    // ==========================================
    // UC1 - SIGN UP TESTS
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("UC1: Sign Up with valid data creates user in database")
    void UC1_signUp_withValidData_createsUser() {
        // Act
        User user = userService.signUp("alice@test.com", "password123", "Alice Smith");

        // Assert
        assertNotNull(user);
        assertNotNull(user.getUserId(), "User ID should be generated");
        assertEquals("alice@test.com", user.getEmail());
        assertEquals("Alice Smith", user.getFullName());

        // Verify user is actually in database
        User fromDB = userDAO.findByEmail("alice@test.com").orElse(null);
        assertNotNull(fromDB, "User should be persisted in database");
        assertEquals(user.getUserId(), fromDB.getUserId());
    }

    @Test
    @Order(2)
    @DisplayName("UC1: Sign Up with duplicate email throws DomainException")
    void UC1_signUp_withDuplicateEmail_throwsException() {
        // Arrange - first registration
        userService.signUp("duplicate@test.com", "password123", "First User");

        // Act & Assert - second registration with same email
        DomainException exception = assertThrows(
                DomainException.class,
                () -> userService.signUp("duplicate@test.com", "password456", "Second User")
        );

        assertTrue(exception.getMessage().contains("already registered"),
                "Error message should mention email already registered");
    }

    @Test
    @Order(3)
    @DisplayName("UC1: Sign Up with invalid email format throws DomainException")
    void UC1_signUp_withInvalidEmail_throwsException() {
        // Test cases from UC1 Alternative Course 5b

        // No @ symbol
        assertThrows(DomainException.class,
                () -> userService.signUp("invalidemail", "password123", "Test User"));

        // No domain
        assertThrows(DomainException.class,
                () -> userService.signUp("test@", "password123", "Test User"));

        // No dot in domain
        assertThrows(DomainException.class,
                () -> userService.signUp("test@invalid", "password123", "Test User"));
    }

    @Test
    @Order(4)
    @DisplayName("UC1: Sign Up with short password throws DomainException")
    void UC1_signUp_withShortPassword_throwsException() {
        // Password less than 8 characters (UC1 requirement)
        DomainException exception = assertThrows(
                DomainException.class,
                () -> userService.signUp("test@test.com", "pass", "Test User")
        );

        assertTrue(exception.getMessage().contains("at least 8 characters"),
                "Error message should mention password length requirement");
    }

    @Test
    @Order(5)
    @DisplayName("UC1: Sign Up with empty name throws DomainException")
    void UC1_signUp_withEmptyName_throwsException() {
        assertThrows(DomainException.class,
                () -> userService.signUp("test@test.com", "password123", ""));

        assertThrows(DomainException.class,
                () -> userService.signUp("test@test.com", "password123", "   "));
    }

    @Test
    @Order(6)
    @DisplayName("UC1: Password is hashed before storing in database")
    void UC1_signUp_passwordIsHashed() {
        // Arrange
        String plainPassword = "mySecretPassword123";

        // Act
        User user = userService.signUp("secure@test.com", plainPassword, "Secure User");

        // Assert
        User fromDB = userDAO.findById(user.getUserId()).orElseThrow();

        // Password in DB should NOT be the plain password
        assertNotEquals(plainPassword, fromDB.getPasswordHash(),
                "Password should be hashed, not stored in plain text");

        // Password hash should not be empty
        assertNotNull(fromDB.getPasswordHash());
        assertFalse(fromDB.getPasswordHash().isEmpty());

        // Password hash should be different from plain password
        assertNotEquals(plainPassword, fromDB.getPasswordHash());
    }

    // ==========================================
    // UC2 - LOGIN TESTS
    // ==========================================

    @Test
    @Order(7)
    @DisplayName("UC2: Login with valid credentials returns user")
    void UC2_login_withValidCredentials_returnsUser() {
        // Arrange - sign up first
        String email = "login@test.com";
        String password = "correctPassword123";
        userService.signUp(email, password, "Login User");

        // Act
        User loggedInUser = userService.login(email, password);

        // Assert
        assertNotNull(loggedInUser);
        assertEquals(email, loggedInUser.getEmail());
        assertEquals("Login User", loggedInUser.getFullName());
    }

    @Test
    @Order(8)
    @DisplayName("UC2: Login with non-existent email throws EntityNotFoundException")
    void UC2_login_withInvalidEmail_throwsException() {
        // Act & Assert (UC2 Alternative Course 4a)
        assertThrows(
                EntityNotFoundException.class,
                () -> userService.login("nonexistent@test.com", "password123")
        );
    }

    @Test
    @Order(9)
    @DisplayName("UC2: Login with wrong password throws DomainException")
    void UC2_login_withWrongPassword_throwsException() {
        // Arrange
        String email = "wrongpass@test.com";
        userService.signUp(email, "correctPassword123", "Test User");

        // Act & Assert (UC2 Alternative Course 4a)
        DomainException exception = assertThrows(
                DomainException.class,
                () -> userService.login(email, "wrongPassword456")
        );

        assertTrue(exception.getMessage().contains("Incorrect password"),
                "Error message should mention incorrect password");
    }

    @Test
    @Order(10)
    @DisplayName("UC2: Login verifies hashed password correctly")
    void UC2_login_verifiesHashedPassword() {
        // Arrange
        String email = "hash@test.com";
        String password = "myPassword123";
        userService.signUp(email, password, "Hash User");

        // Act - login with same password
        User user = userService.login(email, password);

        // Assert - login should succeed
        assertNotNull(user);
        assertEquals(email, user.getEmail());

        // Verify wrong password fails
        assertThrows(DomainException.class,
                () -> userService.login(email, "wrongPassword"));
    }

    @Test
    @Order(11)
    @DisplayName("UC2: Login with null credentials throws DomainException")
    void UC2_login_withNullCredentials_throwsException() {
        assertThrows(DomainException.class,
                () -> userService.login(null, "password123"));

        assertThrows(DomainException.class,
                () -> userService.login("test@test.com", null));
    }

    // ==========================================
    // INTEGRATION VERIFICATION
    // ==========================================

    @Test
    @Order(12)
    @DisplayName("Integration: Multiple users can be registered and logged in")
    void integration_multipleUsers() {
        // Register multiple users
        userService.signUp("alice@test.com", "password123", "Alice");
        userService.signUp("bob@test.com", "password456", "Bob");
        userService.signUp("charlie@test.com", "password789", "Charlie");

        // All should be able to login
        User alice = userService.login("alice@test.com", "password123");
        User bob = userService.login("bob@test.com", "password456");
        User charlie = userService.login("charlie@test.com", "password789");

        // Verify all distinct users
        assertNotEquals(alice.getUserId(), bob.getUserId());
        assertNotEquals(bob.getUserId(), charlie.getUserId());
        assertNotEquals(alice.getUserId(), charlie.getUserId());
    }

    // ==========================================
    // DATABASE CLEANUP
    // ==========================================

    /**
     * Cleans the database before each test.
     * Ensures test isolation by removing all data.
     */
    private void cleanDatabase() throws SQLException {
        Connection conn = connMgr.getConnection();
        try (Statement stmt = conn.createStatement()) {
            // H2 syntax: SET REFERENTIAL_INTEGRITY = FALSE/TRUE
            stmt.execute("SET REFERENTIAL_INTEGRITY = FALSE");
            stmt.execute("TRUNCATE TABLE settlements");
            stmt.execute("TRUNCATE TABLE balances");
            stmt.execute("TRUNCATE TABLE expense_participants");
            stmt.execute("TRUNCATE TABLE expenses");
            stmt.execute("TRUNCATE TABLE memberships");
            stmt.execute("TRUNCATE TABLE groups");
            stmt.execute("TRUNCATE TABLE users");
            stmt.execute("SET REFERENTIAL_INTEGRITY = TRUE");
            conn.commit();
        }
    }
}
