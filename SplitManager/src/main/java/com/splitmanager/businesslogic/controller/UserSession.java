package com.splitmanager.businesslogic.controller;

import com.splitmanager.domain.registry.User;
import com.splitmanager.domain.registry.Group;

/**
 * Singleton class that manages the current user session.
 * Keeps track of the currently logged-in user and the currently selected group.
 */
public class UserSession {

    // Singleton instance (static)
    private static UserSession instance;

    // Current logged-in user
    private User currentUser;

    // Current selected group
    private Group currentGroup;

    // --- Private Constructor (Singleton Pattern) ---

    /**
     * Private constructor to prevent direct instantiation.
     * Use getInstance() to get the singleton instance.
     */
    private UserSession() {
        this.currentUser = null;
        this.currentGroup = null;
    }

    // --- Singleton Method ---

    /**
     * Returns the singleton instance of UserSession.
     * Creates it if it doesn't exist yet (lazy initialization).
     *
     * @return the singleton UserSession instance
     */
    public static UserSession getInstance() {
        if (instance == null) {
            instance = new UserSession();
        }
        return instance;
    }

    // --- Business Methods (exactly as per UML) ---

    /**
     * Logs in a user by setting the current user.
     *
     * @param user the user to log in
     */
    public void login(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        this.currentUser = user;
        // Reset current group on login
        this.currentGroup = null;
    }

    /**
     * Logs out the current user.
     * Clears both current user and current group.
     */
    public void logout() {
        this.currentUser = null;
        this.currentGroup = null;
    }

    /**
     * Gets the currently logged-in user.
     *
     * @return the current user, or null if no user is logged in
     */
    public User getCurrentUser() {
        return currentUser;
    }

    /**
     * Sets the current user.
     *
     * @param user the user to set as current
     */
    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    /**
     * Gets the currently selected group.
     *
     * @return the current group, or null if no group is selected
     */
    public Group getCurrentGroup() {
        return currentGroup;
    }

    /**
     * Sets the current group.
     *
     * @param group the group to set as current
     */
    public void setCurrentGroup(Group group) {
        this.currentGroup = group;
    }

    // --- Utility Methods ---

    /**
     * Checks if a user is currently logged in.
     *
     * @return true if a user is logged in, false otherwise
     */
    public boolean isLoggedIn() {
        return currentUser != null;
    }

    /**
     * Checks if a group is currently selected.
     *
     * @return true if a group is selected, false otherwise
     */
    public boolean hasGroupSelected() {
        return currentGroup != null;
    }

    @Override
    public String toString() {
        return "UserSession{" +
                "currentUser=" + (currentUser != null ? currentUser.getEmail() : "none") +
                ", currentGroup=" + (currentGroup != null ? currentGroup.getName() : "none") +
                '}';
    }
}
