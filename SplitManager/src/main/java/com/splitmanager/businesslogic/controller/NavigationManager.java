package com.splitmanager.businesslogic.controller;

import com.splitmanager.domain.registry.Group;

import com.splitmanager.businesslogic.controller.Navigator;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

/**
 * Singleton class that manages navigation between different screens in the application.
 * Handles the main frame and navigation logic for the GUI.
 */
public class NavigationManager implements Navigator {

    // Singleton instance (static)
    private static NavigationManager instance;

    // Main application frame
    private JFrame mainFrame;

    // --- Private Constructor (Singleton Pattern) ---

    /**
     * PRIVATE constructor to prevent direct instantiation.
     * Use getInstance() to get the singleton instance.
     */
    private NavigationManager() {
        this.mainFrame = null;
    }

    // --- Singleton Method ---

    /**
     * Returns the singleton instance of NavigationManager.
     * Creates it if it doesn't exist yet (lazy initialization).
     *
     * @return the singleton NavigationManager instance
     */
    public static NavigationManager getInstance() {
        if (instance == null) {
            instance = new NavigationManager();
        }
        return instance;
    }

    // --- Business Methods (exactly as per UML) ---

    /**
     * Navigates to the login screen.
     */
    @Override
    public void navigateToLogin() {
        // Implementation will depend on your GUI framework
        // Typically: close current frame and open LoginFrame
        if (mainFrame != null) {
            mainFrame.dispose();
        }
        // Example: new LoginFrame().setVisible(true);
        System.out.println("Navigating to Login screen...");
    }

    /**
     * Navigates to the registration screen.
     */
    @Override
    public void navigateToRegister() {
        // Implementation will depend on your GUI framework
        // Typically: close current frame and open RegisterFrame
        if (mainFrame != null) {
            mainFrame.dispose();
        }
        // Example: new RegisterFrame().setVisible(true);
        System.out.println("Navigating to Register screen...");
    }

    /**
     * Navigates to the home screen.
     */
    @Override
    public void navigateToHome() {
        // Implementation will depend on your GUI framework
        // Typically: close current frame and open HomeFrame
        if (mainFrame != null) {
            mainFrame.dispose();
        }
        // Example: new HomeFrame().setVisible(true);
        System.out.println("Navigating to Home screen...");
    }

    /**
     * Navigates to the group details screen for a specific group.
     *
     * @param group the group whose details should be displayed
     */
    @Override
    public void navigateToGroupDetails(Group group) {
        if (group == null) {
            throw new IllegalArgumentException("Group cannot be null");
        }

        // Implementation will depend on your GUI framework
        // Typically: close current frame and open GroupDetailsFrame with the group
        if (mainFrame != null) {
            mainFrame.dispose();
        }
        // Example: new GroupDetailsFrame(group).setVisible(true);
        System.out.println("Navigating to Group Details for: " + group.getName());
    }

    /**
     * Shows an error message dialog to the user.
     *
     * @param message the error message to display
     */
    @Override
    public void showError(String message) {
        if (message == null || message.trim().isEmpty()) {
            message = "An unknown error occurred";
        }

        // Show error dialog
        JOptionPane.showMessageDialog(
                mainFrame,
                message,
                "Error",
                JOptionPane.ERROR_MESSAGE
        );
    }

    // --- Getter and Setter ---

    /**
     * Gets the main application frame.
     *
     * @return the main JFrame
     */
    public JFrame getMainFrame() {
        return mainFrame;
    }

    /**
     * Sets the main application frame.
     *
     * @param mainFrame the JFrame to set as main frame
     */
    public void setMainFrame(JFrame mainFrame) {
        this.mainFrame = mainFrame;
    }

    // --- Utility Methods ---

    /**
     * Shows a success message dialog to the user.
     *
     * @param message the success message to display
     */
    @Override
    public void showSuccess(String message) {
        if (message == null || message.trim().isEmpty()) {
            message = "Operation completed successfully";
        }

        JOptionPane.showMessageDialog(
                mainFrame,
                message,
                "Success",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    /**
     * Shows a confirmation dialog to the user.
     *
     * @param message the confirmation message to display
     * @return true if the user confirmed, false otherwise
     */
    @Override
    public boolean showConfirmation(String message) {
        if (message == null || message.trim().isEmpty()) {
            message = "Are you sure?";
        }

        int result = JOptionPane.showConfirmDialog(
                mainFrame,
                message,
                "Confirmation",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        return result == JOptionPane.YES_OPTION;
    }

    @Override
    public String toString() {
        return "NavigationManager{" +
                "mainFrame=" + (mainFrame != null ? mainFrame.getTitle() : "none") +
                '}';
    }
}


