package com.splitmanager.businesslogic.controller;

import com.splitmanager.businesslogic.service.UserService;
import com.splitmanager.domain.registry.User;
import com.splitmanager.exception.DomainException;
import com.splitmanager.exception.UnauthorizedException;

/**
 * Controller that handles authentication operations.
 * Manages user login, signup, and logout functionality.
 */
public class AuthController {

    // Dependencies
    private final UserService userService;
    private final UserSession session;
    private final NavigationManager navigator;

    // --- Constructor ---

    /**
     * Creates a new AuthController with the required dependencies.
     *
     * @param userService the user service for authentication operations
     * @param session the user session manager
     * @param navigator the navigation manager
     */
    public AuthController(UserService userService,
                          UserSession session,
                          NavigationManager navigator) {

        if (userService == null) {
            throw new IllegalArgumentException("UserService cannot be null");
        }
        if (session == null) {
            throw new IllegalArgumentException("UserSession cannot be null");
        }
        if (navigator == null) {
            throw new IllegalArgumentException("NavigationManager cannot be null");
        }

        this.userService = userService;
        this.session = session;
        this.navigator = navigator;
    }

    // --- Business Methods (exactly as per UML) ---

    /**
     * Handles user login.
     * Validates credentials, logs in the user, and navigates to home on success.
     *
     * @param email the user's email
     * @param pwd the user's password
     */
    public void handleLogin(String email, String pwd) {
        try {
            // Validate input
            if (email == null || email.trim().isEmpty()) {
                navigator.showError("Email cannot be empty");
                return;
            }

            if (pwd == null || pwd.trim().isEmpty()) {
                navigator.showError("Password cannot be empty");
                return;
            }

            // Attempt login through UserService
            User user = userService.login(email, pwd);

            if (user == null) {
                navigator.showError("Invalid email or password");
                return;
            }

            // Login successful - update session
            session.login(user);

            // Navigate to home screen
            navigator.navigateToHome();

        } catch (DomainException e) {
            navigator.showError("Login failed: " + e.getMessage());
        } catch (Exception e) {
            navigator.showError("An unexpected error occurred during login: " + e.getMessage());
        }
    }

    /**
     * Handles user registration (sign up).
     * Creates a new user account and navigates to login on success.
     *
     * @param name the user's full name
     * @param email the user's email
     * @param pwd the user's password
     */
    public void handleSignUp(String name, String email, String pwd) {
        try {
            // Validate input
            if (name == null || name.trim().isEmpty()) {
                navigator.showError("Name cannot be empty");
                return;
            }

            if (email == null || email.trim().isEmpty()) {
                navigator.showError("Email cannot be empty");
                return;
            }

            if (pwd == null || pwd.trim().isEmpty()) {
                navigator.showError("Password cannot be empty");
                return;
            }

            // Attempt to register new user through UserService
            // UserService.signUp expects: (email, password, fullName)
            User newUser = userService.signUp(email, pwd, name);

            if (newUser == null) {
                navigator.showError("Registration failed. Please try again.");
                return;
            }

            // Registration successful
            navigator.showSuccess("Account created successfully! Please log in.");

            // Navigate to login screen
            navigator.navigateToLogin();

        } catch (DomainException e) {
            navigator.showError("Registration error: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            navigator.showError("Invalid input: " + e.getMessage());
        } catch (Exception e) {
            navigator.showError("An unexpected error occurred during registration: " + e.getMessage());
        }
    }

    /**
     * Handles user logout.
     * Clears the session and navigates to login screen.
     */
    public void handleLogout() {
        try {
            // Clear the session
            session.logout();

            // Navigate to login screen
            navigator.navigateToLogin();

        } catch (Exception e) {
            navigator.showError("An error occurred during logout: " + e.getMessage());
        }
    }

    // --- Getter Methods ---

    /**
     * Gets the user service.
     *
     * @return the UserService instance
     */
    public UserService getUserService() {
        return userService;
    }

    /**
     * Gets the user session.
     *
     * @return the UserSession instance
     */
    public UserSession getSession() {
        return session;
    }

    /**
     * Gets the navigation manager.
     *
     * @return the NavigationManager instance
     */
    public NavigationManager getNavigator() {
        return navigator;
    }

    @Override
    public String toString() {
        return "AuthController{" +
                "currentUser=" + (session.getCurrentUser() != null ?
                session.getCurrentUser().getEmail() : "none") +
                '}';
    }
}
