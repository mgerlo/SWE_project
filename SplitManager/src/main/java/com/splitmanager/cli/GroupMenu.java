package com.splitmanager.cli;

import com.splitmanager.businesslogic.controller.GroupController;
import com.splitmanager.businesslogic.controller.NavigationManager;
import com.splitmanager.businesslogic.controller.UserSession;
import com.splitmanager.businesslogic.service.GroupService;
import com.splitmanager.domain.registry.Group;
import com.splitmanager.domain.registry.Membership;

import java.util.List;

/**
 * Menu for group management operations.
 */
public class GroupMenu {

    private final InputHandler input;
    private final GroupController groupController;
    private final UserSession session;
    private final GroupService groupService;

    /**
     * Creates a new GroupMenu.
     *
     * @param input the input handler
     */
    public GroupMenu(InputHandler input) {
        this.input = input;
        this.session = UserSession.getInstance();
        this.groupService = new GroupService();
        NavigationManager navigator = NavigationManager.getInstance();
        this.groupController = new GroupController(groupService, session, navigator);
    }

    /**
     * Shows the group menu.
     */
    public void show() {
        while (true) {
            input.printHeader("GROUP MANAGEMENT");

            // Show current group if selected
            if (session.hasGroupSelected()) {
                System.out.println("Current Group: " + session.getCurrentGroup().getName());
                input.printSeparator();
            }

            System.out.println("1. Create New Group");
            System.out.println("2. Join Group by Invite Code");
            System.out.println("3. View My Groups");
            System.out.println("4. Select a Group");

            if (session.hasGroupSelected()) {
                System.out.println("5. Group Settings");
                System.out.println("6. Manage Members");
            }

            System.out.println("0. Back to Main Menu");
            input.printSeparator();

            int maxOption = session.hasGroupSelected() ? 6 : 4;
            int choice = input.readInt("Select an option: ", 0, maxOption);

            switch (choice) {
                case 1:
                    handleCreateGroup();
                    break;
                case 2:
                    handleJoinGroup();
                    break;
                case 3:
                    handleViewMyGroups();
                    break;
                case 4:
                    handleSelectGroup();
                    break;
                case 5:
                    if (session.hasGroupSelected()) {
                        handleGroupSettings();
                    }
                    break;
                case 6:
                    if (session.hasGroupSelected()) {
                        handleManageMembers();
                    }
                    break;
                case 0:
                    return; // Back to main menu
                default:
                    input.printError("Invalid option.");
            }
        }
    }

    /**
     * Handles creating a new group.
     */
    private void handleCreateGroup() {
        input.printHeader("CREATE NEW GROUP");

        String name = input.readNonEmptyString("Group Name: ");
        String currency = input.readNonEmptyString("Currency (e.g., EUR, USD): ").toUpperCase();

        // Validate currency format (3 letters)
        if (!currency.matches("[A-Z]{3}")) {
            input.printError("Invalid currency format. Use 3 letters (e.g., EUR, USD, GBP).");
            input.waitForEnter();
            return;
        }

        try {
            groupController.createGroup(name, currency);
            input.printSuccess("Group created successfully!");
            input.printInfo("You are now the admin of this group.");
            input.waitForEnter();
        } catch (Exception e) {
            input.printError("Failed to create group: " + e.getMessage());
            input.waitForEnter();
        }
    }

    /**
     * Handles joining a group by invite code.
     */
    private void handleJoinGroup() {
        input.printHeader("JOIN GROUP");

        String inviteCode = input.readNonEmptyString("Enter Invite Code: ").toUpperCase();

        try {
            groupController.joinGroup(inviteCode);
            input.printSuccess("Join request sent! Wait for admin approval.");
            input.waitForEnter();
        } catch (Exception e) {
            input.printError("Failed to join group: " + e.getMessage());
            input.waitForEnter();
        }
    }

    /**
     * Handles viewing all groups the user belongs to.
     */
    private void handleViewMyGroups() {
        input.printHeader("MY GROUPS");

        try {
            // Get all memberships for current user
            Long userId = session.getCurrentUser().getUserId();
            List<Membership> memberships = groupService.getUserMemberships(userId);

            if (memberships.isEmpty()) {
                input.printInfo("You are not a member of any group yet.");
                input.waitForEnter();
                return;
            }

            System.out.println("\nYour Groups:");
            input.printSeparator();

            for (int i = 0; i < memberships.size(); i++) {
                Membership membership = memberships.get(i);
                Group group = membership.getGroup();
                System.out.println((i + 1) + ". " + group.getName() +
                        " (" + group.getCurrency() + ")" +
                        " - Role: " + membership.getRole());
            }

            input.waitForEnter();
        } catch (Exception e) {
            input.printError("Failed to load groups: " + e.getMessage());
            input.waitForEnter();
        }
    }

    /**
     * Handles selecting a group to work with.
     */
    private void handleSelectGroup() {
        input.printHeader("SELECT GROUP");

        try {
            Long userId = session.getCurrentUser().getUserId();
            List<Membership> memberships = groupService.getGroupMembers(userId);

            if (memberships.isEmpty()) {
                input.printInfo("You are not a member of any group yet.");
                input.waitForEnter();
                return;
            }

            System.out.println("\nAvailable Groups:");
            input.printSeparator();

            for (int i = 0; i < memberships.size(); i++) {
                Membership membership = memberships.get(i);
                Group group = membership.getGroup();
                System.out.println((i + 1) + ". " + group.getName() +
                        " (" + group.getCurrency() + ")");
            }

            int choice = input.readInt("\nSelect a group (0 to cancel): ", 0, memberships.size());

            if (choice == 0) {
                return;
            }

            Group selectedGroup = memberships.get(choice - 1).getGroup();
            groupController.openGroup(selectedGroup);

            input.printSuccess("Group selected: " + selectedGroup.getName());
            input.waitForEnter();

        } catch (Exception e) {
            input.printError("Failed to select group: " + e.getMessage());
            input.waitForEnter();
        }
    }

    /**
     * Handles group settings (admin only).
     */
    private void handleGroupSettings() {
        input.printHeader("GROUP SETTINGS");

        Group currentGroup = session.getCurrentGroup();

        System.out.println("Current Settings:");
        System.out.println("Name: " + currentGroup.getName());
        System.out.println("Currency: " + currentGroup.getCurrency());
        System.out.println("Invite Code: " + currentGroup.getInviteCode());
        input.printSeparator();

        System.out.println("1. Update Group Name and Currency");
        System.out.println("2. Generate New Invite Code");
        System.out.println("0. Back");

        int choice = input.readInt("Select an option: ", 0, 2);

        switch (choice) {
            case 1:
                handleUpdateSettings();
                break;
            case 2:
                handleGenerateInviteCode();
                break;
            case 0:
                return;
        }
    }

    /**
     * Handles updating group settings.
     */
    private void handleUpdateSettings() {
        String newName = input.readNonEmptyString("New Group Name: ");
        String newCurrency = input.readNonEmptyString("New Currency: ").toUpperCase();

        if (!newCurrency.matches("[A-Z]{3}")) {
            input.printError("Invalid currency format.");
            input.waitForEnter();
            return;
        }

        try {
            // Note: This requires adminMembershipId which we don't have in session
            input.printError("This feature requires membership ID tracking.");
            input.printInfo("Please implement membership ID in UserSession.");
            input.waitForEnter();
        } catch (Exception e) {
            input.printError("Failed to update settings: " + e.getMessage());
            input.waitForEnter();
        }
    }

    /**
     * Handles generating a new invite code.
     */
    private void handleGenerateInviteCode() {
        try {
            // Note: This requires adminMembershipId which we don't have in session
            input.printError("This feature requires membership ID tracking.");
            input.printInfo("Please implement membership ID in UserSession.");
            input.waitForEnter();
        } catch (Exception e) {
            input.printError("Failed to generate invite code: " + e.getMessage());
            input.waitForEnter();
        }
    }

    /**
     * Handles managing group members (admin only).
     */
    private void handleManageMembers() {
        input.printHeader("MANAGE MEMBERS");

        try {
            Long groupId = session.getCurrentGroup().getGroupId();
            List<Membership> members = groupService.getGroupMembers(groupId);

            System.out.println("\nGroup Members:");
            input.printSeparator();

            for (int i = 0; i < members.size(); i++) {
                Membership member = members.get(i);
                System.out.println((i + 1) + ". " +
                        member.getUser().getFullName() +
                        " - Role: " + member.getRole() +
                        " - Status: " + member.getStatus());
            }

            input.printSeparator();
            System.out.println("1. Approve Pending Member");
            System.out.println("2. Remove Member");
            System.out.println("0. Back");

            int choice = input.readInt("Select an option: ", 0, 2);

            switch (choice) {
                case 1:
                    handleApproveMember(members);
                    break;
                case 2:
                    handleRemoveMember(members);
                    break;
                case 0:
                    return;
            }

        } catch (Exception e) {
            input.printError("Failed to load members: " + e.getMessage());
            input.waitForEnter();
        }
    }

    /**
     * Handles approving a pending member.
     */
    private void handleApproveMember(List<Membership> members) {
        input.printInfo("This feature requires membership ID tracking.");
        input.waitForEnter();
    }

    /**
     * Handles removing a member.
     */
    private void handleRemoveMember(List<Membership> members) {
        input.printInfo("This feature requires membership ID tracking.");
        input.waitForEnter();
    }
}
