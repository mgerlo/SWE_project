package com.splitmanager.cli;

import com.splitmanager.businesslogic.controller.AuthController;
import com.splitmanager.businesslogic.controller.UserSession;
import com.splitmanager.businesslogic.service.UserService;

/**
 * Menu for authentication operations (login and registration).
 */
public class AuthMenu {

    private final InputHandler input;
    private final AuthController authController;
    private final UserSession session;

    /**
     * Creates a new AuthMenu.
     *
     * @param input the input handler
     */
    public AuthMenu(InputHandler input) {
        this.input = input;
        this.session = UserSession.getInstance();

        // Initialize services and controllers
        UserService userService = new UserService();
        this.authController = new AuthController(userService, session, null);
    }

    /**
     * Shows the authentication menu.
     * Returns true if login is successful, false if user wants to exit.
     *
     * @return true if authenticated, false if exit
     */
    public boolean show() {
        while (true) {
            input.printHeader("AUTHENTICATION");
            System.out.println("1. Login");
            System.out.println("2. Register");
            System.out.println("0. Exit");
            input.printSeparator();

            int choice = input.readInt("Select an option: ", 0, 2);

            switch (choice) {
                case 1:
                    if (handleLogin()) {
                        return true; // Login successful
                    }
                    break;
                case 2:
                    handleRegister();
                    break;
                case 0:
                    return false; // User wants to exit
                default:
                    input.printError("Invalid option.");
            }
        }
    }

    /**
     * Handles the login process.
     *
     * @return true if login is successful, false otherwise
     */
    private boolean handleLogin() {
        input.printHeader("LOGIN");

        String email = input.readNonEmptyString("Email: ");
        String password = input.readNonEmptyString("Password: ");

        try {
            authController.handleLogin(email, password);

            // Check if login was successful
            if (session.isLoggedIn()) {
                input.printSuccess("Login successful!");
                input.printInfo("Welcome, " + session.getCurrentUser().getFullName() + "!");
                input.waitForEnter();
                return true;
            } else {
                input.printError("Login failed. Please check your credentials.");
                input.waitForEnter();
                return false;
            }
        } catch (Exception e) {
            input.printError("An error occurred during login: " + e.getMessage());
            input.waitForEnter();
            return false;
        }
    }

    /**
     * Handles the registration process.
     */
    private void handleRegister() {
        input.printHeader("REGISTER");

        String name = input.readNonEmptyString("Full Name: ");
        String email = input.readNonEmptyString("Email: ");
        String password = input.readNonEmptyString("Password (min 8 characters): ");

        // Basic password validation
        if (password.length() < 8) {
            input.printError("Password must be at least 8 characters long.");
            input.waitForEnter();
            return;
        }

        String confirmPassword = input.readNonEmptyString("Confirm Password: ");

        if (!password.equals(confirmPassword)) {
            input.printError("Passwords do not match.");
            input.waitForEnter();
            return;
        }

        try {
            authController.handleSignUp(name, email, password);
            input.printSuccess("Registration successful! You can now login.");
            input.waitForEnter();
        } catch (Exception e) {
            input.printError("Registration failed: " + e.getMessage());
            input.waitForEnter();
        }
    }
}
