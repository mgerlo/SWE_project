package com.splitmanager.businesslogic.controller;

import com.splitmanager.businesslogic.service.GroupService;
import com.splitmanager.domain.registry.Group;
import com.splitmanager.exception.DomainException;
import com.splitmanager.exception.EntityNotFoundException;
import com.splitmanager.exception.UnauthorizedException;

import com.splitmanager.businesslogic.controller.Navigator;

/**
 * Controller that handles group-related operations.
 * Manages group creation, joining, settings, and member management.
 */
public class GroupController {

    // Dependencies
    private final GroupService groupService;
    private final UserSession session;
    private final Navigator navigator; //uses Navigator interface

    // --- Constructor ---

    /**
     * Creates a new GroupController with the required dependencies.
     *
     * @param groupService the group service for group operations
     * @param session the user session manager
     * @param navigator the navigation manager
     */
    public GroupController(GroupService groupService,
                           UserSession session,
                           Navigator navigator) { //inject Navigator dependency

        if (groupService == null) {
            throw new IllegalArgumentException("GroupService cannot be null");
        }
        if (session == null) {
            throw new IllegalArgumentException("UserSession cannot be null");
        }
        if (navigator == null) {
            throw new IllegalArgumentException("NavigationManager cannot be null");
        }

        this.groupService = groupService;
        this.session = session;
        this.navigator = navigator;
    }

    // --- Business Methods (exactly as per UML) ---

    /**
     * Creates a new group.
     * Sets the current user as admin and the group as current group.
     *
     * @param name the name of the group
     * @param currency the currency used by the group
     */
    public void createGroup(String name, String currency) {
        try {
            // Validate input
            if (name == null || name.trim().isEmpty()) {
                navigator.showError("Group name cannot be empty");
                return;
            }

            if (currency == null || currency.trim().isEmpty()) {
                navigator.showError("Currency cannot be empty");
                return;
            }

            // Check if user is logged in
            if (!session.isLoggedIn()) {
                navigator.showError("You must be logged in to create a group");
                navigator.navigateToLogin();
                return;
            }

            // Create group through GroupService
            // GroupService.createGroup expects: (userId, name, currency)
            Group newGroup = groupService.createGroup(
                    session.getCurrentUser().getUserId(),
                    name,
                    currency
            );

            if (newGroup == null) {
                navigator.showError("Failed to create group");
                return;
            }

            // Set as current group
            session.setCurrentGroup(newGroup);

            // Navigate to group details
            navigator.navigateToGroupDetails(newGroup);

        } catch (DomainException e) {
            navigator.showError("Error creating group: " + e.getMessage());
        } catch (Exception e) {
            navigator.showError("An unexpected error occurred: " + e.getMessage());
        }
    }

    /**
     * Joins a group using an invite code.
     * Adds the current user as a member of the group.
     *
     * @param inviteCode the invite code for the group
     */
    public void joinGroup(String inviteCode) {
        try {
            // Validate input
            if (inviteCode == null || inviteCode.trim().isEmpty()) {
                navigator.showError("Invite code cannot be empty");
                return;
            }

            // Check if user is logged in
            if (!session.isLoggedIn()) {
                navigator.showError("You must be logged in to join a group");
                navigator.navigateToLogin();
                return;
            }

            // Join group through GroupService
            // GroupService.joinByCode expects: (userId, inviteCode)
            groupService.joinByCode(
                    session.getCurrentUser().getUserId(),
                    inviteCode
            );

            // Success message
            navigator.showSuccess("Successfully joined the group! Waiting for admin approval.");

            // Navigate back to home (group will be visible after approval)
            navigator.navigateToHome();

        } catch (EntityNotFoundException e) {
            navigator.showError("Invalid invite code: " + e.getMessage());
        } catch (DomainException e) {
            navigator.showError("Error joining group: " + e.getMessage());
        } catch (Exception e) {
            navigator.showError("An unexpected error occurred: " + e.getMessage());
        }
    }

    /**
     * Opens a group and sets it as the current group.
     * Navigates to the group details screen.
     * Note: This method assumes the Group object is already available.
     *
     * @param groupId the ID of the group to open
     */
    public void openGroup(Long groupId) {
        try {
            // Validate input
            if (groupId == null) {
                navigator.showError("Group ID cannot be null");
                return;
            }

            // Check if user is logged in
            if (!session.isLoggedIn()) {
                navigator.showError("You must be logged in to open a group");
                navigator.navigateToLogin();
                return;
            }

            // Note: GroupService doesn't have a getGroupById method in the current implementation
            // This method needs to be called with a Group object already loaded
            // For now, we show an error message
            navigator.showError("This feature requires the Group object to be passed directly");

        } catch (Exception e) {
            navigator.showError("An unexpected error occurred: " + e.getMessage());
        }
    }

    /**
     * Opens a group and sets it as the current group.
     * Navigates to the group details screen.
     *
     * @param group the Group object to open
     */
    public void openGroup(Group group) {
        try {
            // Validate input
            if (group == null) {
                navigator.showError("Group cannot be null");
                return;
            }

            // Check if user is logged in
            if (!session.isLoggedIn()) {
                navigator.showError("You must be logged in to open a group");
                navigator.navigateToLogin();
                return;
            }

            // Set as current group
            session.setCurrentGroup(group);

            // Navigate to group details
            navigator.navigateToGroupDetails(group);

        } catch (Exception e) {
            navigator.showError("An unexpected error occurred: " + e.getMessage());
        }
    }

    /**
     * Updates the settings of the current group.
     * Only admins can update group settings.
     *
     * @param name the new name for the group
     * @param currency the new currency for the group
     */
    public void updateSettings(String name, String currency) {
        try {
            // Validate input
            if (name == null || name.trim().isEmpty()) {
                navigator.showError("Group name cannot be empty");
                return;
            }

            if (currency == null || currency.trim().isEmpty()) {
                navigator.showError("Currency cannot be empty");
                return;
            }

            // Check if user is logged in
            if (!session.isLoggedIn()) {
                navigator.showError("You must be logged in to update settings");
                navigator.navigateToLogin();
                return;
            }

            // Check if group is selected
            if (!session.hasGroupSelected()) {
                navigator.showError("No group selected");
                return;
            }

            // Get current user's membership in this group
            // Note: This requires getting the membership ID from somewhere
            // For now, we'll need to add a helper method or pass it differently

            // This is a limitation: we need the membership ID but only have User and Group
            // The GUI layer should track this when displaying group members
            navigator.showError("This feature requires membership tracking in the session");

        } catch (UnauthorizedException e) {
            navigator.showError("Permission denied: " + e.getMessage());
        } catch (DomainException e) {
            navigator.showError("Error updating settings: " + e.getMessage());
        } catch (Exception e) {
            navigator.showError("An unexpected error occurred: " + e.getMessage());
        }
    }

    /**
     * Updates the settings of the current group.
     * Only admins can update group settings.
     * This version requires the admin's membership ID.
     *
     * @param name the new name for the group
     * @param description the new description for the group
     * @param currency the new currency for the group
     * @param adminMembershipId the ID of the admin's membership
     */
    public void updateSettings(String name, String description, String currency, Long adminMembershipId) {
        try {
            // Validate input
            if (adminMembershipId == null) {
                navigator.showError("Admin membership ID is required");
                return;
            }

            // Check if user is logged in
            if (!session.isLoggedIn()) {
                navigator.showError("You must be logged in to update settings");
                navigator.navigateToLogin();
                return;
            }

            // Check if group is selected
            if (!session.hasGroupSelected()) {
                navigator.showError("No group selected");
                return;
            }

            // Update group settings through GroupService
            // GroupService.updateSettings expects: (groupId, adminMembershipId, newName, newDescription, newCurrency)
            groupService.updateSettings(
                    session.getCurrentGroup().getGroupId(),
                    adminMembershipId,
                    name,
                    description,
                    currency
            );

            // Success message
            navigator.showSuccess("Group settings updated successfully");

        } catch (UnauthorizedException e) {
            navigator.showError("Permission denied: " + e.getMessage());
        } catch (DomainException e) {
            navigator.showError("Error updating settings: " + e.getMessage());
        } catch (Exception e) {
            navigator.showError("An unexpected error occurred: " + e.getMessage());
        }
    }

    /**
     * Generates a new invite code for the current group.
     * Only admins can generate invite codes.
     * This version requires the admin's membership ID.
     *
     * @param adminMembershipId the ID of the admin's membership
     */
    public void generateNewInviteCode(Long adminMembershipId) {
        try {
            // Validate input
            if (adminMembershipId == null) {
                navigator.showError("Admin membership ID is required");
                return;
            }

            // Check if user is logged in
            if (!session.isLoggedIn()) {
                navigator.showError("You must be logged in to generate invite code");
                navigator.navigateToLogin();
                return;
            }

            // Check if group is selected
            if (!session.hasGroupSelected()) {
                navigator.showError("No group selected");
                return;
            }

            // Generate new invite code through GroupService
            // GroupService.inviteMember expects: (groupId, adminMembershipId)
            String newInviteCode = groupService.inviteMember(
                    session.getCurrentGroup().getGroupId(),
                    adminMembershipId
            );

            if (newInviteCode == null || newInviteCode.isEmpty()) {
                navigator.showError("Failed to generate invite code");
                return;
            }

            // Success message with the new code
            navigator.showSuccess("New invite code generated: " + newInviteCode);

        } catch (UnauthorizedException e) {
            navigator.showError("Permission denied: " + e.getMessage());
        } catch (DomainException e) {
            navigator.showError("Error generating invite code: " + e.getMessage());
        } catch (Exception e) {
            navigator.showError("An unexpected error occurred: " + e.getMessage());
        }
    }

    /**
     * Removes an existing member from the current group.
     * Only admins can remove members.
     *
     * @param membershipIdToRemove the ID of the membership to remove
     * @param adminMembershipId the ID of the admin's membership
     */
    public void removeExistingMember(Long membershipIdToRemove, Long adminMembershipId) {
        try {
            // Validate input
            if (membershipIdToRemove == null) {
                navigator.showError("Member ID cannot be null");
                return;
            }

            if (adminMembershipId == null) {
                navigator.showError("Admin membership ID is required");
                return;
            }

            // Check if user is logged in
            if (!session.isLoggedIn()) {
                navigator.showError("You must be logged in to remove members");
                navigator.navigateToLogin();
                return;
            }

            // Check if group is selected
            if (!session.hasGroupSelected()) {
                navigator.showError("No group selected");
                return;
            }

            // Confirm action
            if (!navigator.showConfirmation("Are you sure you want to remove this member?")) {
                return;
            }

            // Remove member through GroupService
            // GroupService.removeMember expects: (groupId, membershipIdToRemove, adminMembershipId)
            groupService.removeMember(
                    session.getCurrentGroup().getGroupId(),
                    membershipIdToRemove,
                    adminMembershipId
            );

            // Success message
            navigator.showSuccess("Member removed successfully");

        } catch (UnauthorizedException e) {
            navigator.showError("Permission denied: " + e.getMessage());
        } catch (EntityNotFoundException e) {
            navigator.showError("Member not found: " + e.getMessage());
        } catch (DomainException e) {
            navigator.showError("Error removing member: " + e.getMessage());
        } catch (Exception e) {
            navigator.showError("An unexpected error occurred: " + e.getMessage());
        }
    }

    /**
     * Approves a pending member in the current group.
     * Only admins can approve members.
     *
     * @param membershipId the ID of the membership to approve
     * @param adminMembershipId the ID of the admin's membership
     */
    public void approveMember(Long membershipId, Long adminMembershipId) {
        try {
            // Validate input
            if (membershipId == null) {
                navigator.showError("Member ID cannot be null");
                return;
            }

            if (adminMembershipId == null) {
                navigator.showError("Admin membership ID is required");
                return;
            }

            // Check if user is logged in
            if (!session.isLoggedIn()) {
                navigator.showError("You must be logged in to approve members");
                navigator.navigateToLogin();
                return;
            }

            // Check if group is selected
            if (!session.hasGroupSelected()) {
                navigator.showError("No group selected");
                return;
            }

            // Approve member through GroupService
            // GroupService.approveMember expects: (membershipId, adminMembershipId)
            groupService.approveMember(
                    membershipId,
                    adminMembershipId
            );

            // Success message
            navigator.showSuccess("Member approved successfully");

        } catch (UnauthorizedException e) {
            navigator.showError("Permission denied: " + e.getMessage());
        } catch (EntityNotFoundException e) {
            navigator.showError("Member not found: " + e.getMessage());
        } catch (DomainException e) {
            navigator.showError("Error approving member: " + e.getMessage());
        } catch (Exception e) {
            navigator.showError("An unexpected error occurred: " + e.getMessage());
        }
    }

    // --- Getter Methods ---

    /**
     * Gets the group service.
     *
     * @return the GroupService instance
     */
    public GroupService getGroupService() {
        return groupService;
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
    public Navigator getNavigator() {
        return navigator;
    }

    @Override
    public String toString() {
        return "GroupController{" +
                "currentGroup=" + (session.getCurrentGroup() != null ?
                session.getCurrentGroup().getName() : "none") +
                '}';
    }
}
