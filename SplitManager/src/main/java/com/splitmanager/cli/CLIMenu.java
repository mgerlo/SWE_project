package com.splitmanager.cli;

import com.splitmanager.businesslogic.controller.UserSession;

/**
 * Main menu that coordinates all other menus in the CLI application.
 */
public class CLIMenu {

    private final InputHandler input;
    private final UserSession session;

    private final AuthMenu authMenu;
    private final GroupMenu groupMenu;
    private final ExpenseMenu expenseMenu;
    private final BalanceMenu balanceMenu;

    /**
     * Creates a new CLIMenu.
     */
    public CLIMenu() {
        this.input = new InputHandler();
        this.session = UserSession.getInstance();

        // Initialize all sub-menus
        this.authMenu = new AuthMenu(input);
        this.groupMenu = new GroupMenu(input);
        this.expenseMenu = new ExpenseMenu(input);
        this.balanceMenu = new BalanceMenu(input);
    }

    /**
     * Starts the main menu loop.
     */
    public void start() {
        // First, user must authenticate
        boolean authenticated = authMenu.show();

        if (!authenticated) {
            // User chose to exit without logging in
            input.close();
            return;
        }

        // Main menu loop (after authentication)
        while (true) {
            showMainMenu();

            int choice = input.readInt("Select an option: ", 0, 5);

            switch (choice) {
                case 1:
                    groupMenu.show();
                    break;
                case 2:
                    expenseMenu.show();
                    break;
                case 3:
                    balanceMenu.show();
                    break;
                case 4:
                    showUserProfile();
                    break;
                case 5:
                    handleLogout();
                    break;
                case 0:
                    if (confirmExit()) {
                        input.close();
                        return;
                    }
                    break;
                default:
                    input.printError("Invalid option.");
            }
        }
    }

    /**
     * Displays the main menu.
     */
    private void showMainMenu() {
        input.printHeader("MAIN MENU");

        // Show current user info
        if (session.isLoggedIn()) {
            System.out.println("User: " + session.getCurrentUser().getFullName());
            if (session.hasGroupSelected()) {
                System.out.println("Current Group: " + session.getCurrentGroup().getName());
            } else {
                System.out.println("No group selected");
            }
            input.printSeparator();
        }

        System.out.println("1. Group Management");
        System.out.println("2. Expense Management");
        System.out.println("3. Balance & Settlements");
        System.out.println("4. My Profile");
        System.out.println("5. Logout");
        System.out.println("0. Exit");
        input.printSeparator();
    }

    /**
     * Shows user profile information.
     */
    private void showUserProfile() {
        input.printHeader("MY PROFILE");

        if (!session.isLoggedIn()) {
            input.printError("Not logged in.");
            input.waitForEnter();
            return;
        }

        System.out.println("Name: " + session.getCurrentUser().getFullName());
        System.out.println("Email: " + session.getCurrentUser().getEmail());
        input.printSeparator();

        input.printInfo("Profile management features coming soon.");
        input.waitForEnter();
    }

    /**
     * Handles user logout.
     */
    private void handleLogout() {
        input.printHeader("LOGOUT");

        if (input.readConfirmation("Are you sure you want to logout?")) {
            session.logout();
            input.printSuccess("You have been logged out successfully.");
            input.waitForEnter();

            // After logout, show auth menu again
            boolean authenticated = authMenu.show();

            if (!authenticated) {
                // User chose to exit after logout
                input.close();
                System.exit(0);
            }
        }
    }

    /**
     * Confirms if user wants to exit the application.
     *
     * @return true if user confirms exit, false otherwise
     */
    private boolean confirmExit() {
        return input.readConfirmation("Are you sure you want to exit?");
    }
}
