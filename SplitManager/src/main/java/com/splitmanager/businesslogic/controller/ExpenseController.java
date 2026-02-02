package com.splitmanager.businesslogic.controller;

import com.splitmanager.businesslogic.service.ExpenseService;
import com.splitmanager.domain.accounting.Category;
import com.splitmanager.domain.accounting.Expense;
import com.splitmanager.exception.DomainException;
import com.splitmanager.exception.EntityNotFoundException;
import com.splitmanager.exception.UnauthorizedException;

import com.splitmanager.businesslogic.controller.Navigator;

import java.math.BigDecimal;
import java.util.List;

/**
 * Controller that handles expense-related operations.
 * Manages expense creation, deletion, and viewing expense history.
 */
public class ExpenseController {

    // Dependencies
    private final ExpenseService expenseService;
    private final UserSession session;
    private final Navigator navigator;

    // --- Constructor ---

    /**
     * Creates a new ExpenseController with the required dependencies.
     *
     * @param expenseService the expense service for expense operations
     * @param session the user session manager
     * @param navigator the navigation manager
     */
    public ExpenseController(ExpenseService expenseService,
                             UserSession session,
                             Navigator navigator) {

        if (expenseService == null) {
            throw new IllegalArgumentException("ExpenseService cannot be null");
        }
        if (session == null) {
            throw new IllegalArgumentException("UserSession cannot be null");
        }
        if (navigator == null) {
            throw new IllegalArgumentException("NavigationManager cannot be null");
        }

        this.expenseService = expenseService;
        this.session = session;
        this.navigator = navigator;
    }

    // --- Business Methods (exactly as per UML) ---

    /**
     * Creates a new expense in the current group.
     * Automatically updates balances for all participants.
     *
     * @param amount the total amount of the expense
     * @param desc the description of the expense
     * @param cat the category of the expense
     * @param participants the list of membership IDs who participate in this expense
     */
    public void createExpense(BigDecimal amount,
                              String desc,
                              Category cat,
                              List<Long> participants) {
        try {
            // Validate input
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                navigator.showError("Amount must be positive");
                return;
            }

            if (desc == null || desc.trim().isEmpty()) {
                navigator.showError("Description cannot be empty");
                return;
            }

            if (cat == null) {
                navigator.showError("Category must be selected");
                return;
            }

            if (participants == null || participants.isEmpty()) {
                navigator.showError("At least one participant must be selected");
                return;
            }

            // Check if user is logged in
            if (!session.isLoggedIn()) {
                navigator.showError("You must be logged in to create an expense");
                navigator.navigateToLogin();
                return;
            }

            // Check if group is selected
            if (!session.hasGroupSelected()) {
                navigator.showError("No group selected");
                return;
            }

            // Note: This implementation requires the current user's membership ID
            // which should be tracked in the session or GUI layer
            // For now, we'll show a limitation message

            navigator.showError("This feature requires membership ID tracking in the session");

            // The actual call would be:
            // expenseService.addExpense(
            //     session.getCurrentGroup().getGroupId(),
            //     currentUserMembershipId,  // Need to get this from somewhere
            //     amount,
            //     desc,
            //     cat,
            //     participants
            // );

        } catch (EntityNotFoundException e) {
            navigator.showError("Not found: " + e.getMessage());
        } catch (DomainException e) {
            navigator.showError("Error creating expense: " + e.getMessage());
        } catch (Exception e) {
            navigator.showError("An unexpected error occurred: " + e.getMessage());
        }
    }

    /**
     * Creates a new expense with the payer's membership ID.
     * This is a more complete version that works with the current ExpenseService.
     *
     * @param payerMembershipId the ID of the member who paid
     * @param amount the total amount of the expense
     * @param desc the description of the expense
     * @param cat the category of the expense
     * @param participants the list of membership IDs who participate in this expense
     */
    public void createExpense(Long payerMembershipId,
                              BigDecimal amount,
                              String desc,
                              Category cat,
                              List<Long> participants) {
        try {
            // Validate input
            if (payerMembershipId == null) {
                navigator.showError("Payer membership ID is required");
                return;
            }

            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                navigator.showError("Amount must be positive");
                return;
            }

            if (desc == null || desc.trim().isEmpty()) {
                navigator.showError("Description cannot be empty");
                return;
            }

            if (cat == null) {
                navigator.showError("Category must be selected");
                return;
            }

            if (participants == null || participants.isEmpty()) {
                navigator.showError("At least one participant must be selected");
                return;
            }

            // Check if user is logged in
            if (!session.isLoggedIn()) {
                navigator.showError("You must be logged in to create an expense");
                navigator.navigateToLogin();
                return;
            }

            // Check if group is selected
            if (!session.hasGroupSelected()) {
                navigator.showError("No group selected");
                return;
            }

            // Create expense through ExpenseService
            // ExpenseService.addExpense expects: (groupId, payerMembershipId, amount, description, category, participantIds)
            Expense newExpense = expenseService.addExpense(
                    session.getCurrentGroup().getGroupId(),
                    payerMembershipId,
                    amount,
                    desc,
                    cat,
                    participants
            );

            if (newExpense == null) {
                navigator.showError("Failed to create expense");
                return;
            }

            // Success message
            navigator.showSuccess("Expense created successfully! Balances have been updated.");

        } catch (EntityNotFoundException e) {
            navigator.showError("Not found: " + e.getMessage());
        } catch (DomainException e) {
            navigator.showError("Error creating expense: " + e.getMessage());
        } catch (Exception e) {
            navigator.showError("An unexpected error occurred: " + e.getMessage());
        }
    }

    /**
     * Deletes an existing expense.
     * Only the creator or an admin can delete an expense.
     * Balances are automatically adjusted.
     *
     * @param expenseId the ID of the expense to delete
     * @param deleterMembershipId the ID of the member's membership who is deleting
     */
    public void deleteExpense(Long expenseId, Long deleterMembershipId) {
        try {
            // Validate input
            if (expenseId == null) {
                navigator.showError("Expense ID cannot be null");
                return;
            }

            if (deleterMembershipId == null) {
                navigator.showError("Deleter membership ID is required");
                return;
            }

            // Check if user is logged in
            if (!session.isLoggedIn()) {
                navigator.showError("You must be logged in to delete an expense");
                navigator.navigateToLogin();
                return;
            }

            // Confirm action
            if (!navigator.showConfirmation("Are you sure you want to delete this expense? Balances will be adjusted.")) {
                return;
            }

            // Delete expense through ExpenseService
            // ExpenseService.deleteExpense expects: (expenseId, deleterMembershipId)
            expenseService.deleteExpense(expenseId, deleterMembershipId);

            // Success message
            navigator.showSuccess("Expense deleted successfully! Balances have been adjusted.");

        } catch (EntityNotFoundException e) {
            navigator.showError("Expense not found: " + e.getMessage());
        } catch (UnauthorizedException e) {
            navigator.showError("Permission denied: " + e.getMessage());
        } catch (DomainException e) {
            navigator.showError("Error deleting expense: " + e.getMessage());
        } catch (Exception e) {
            navigator.showError("An unexpected error occurred: " + e.getMessage());
        }
    }

    /**
     * Loads and returns the expense history for the current group.
     * Returns all expenses except deleted ones.
     *
     * @return list of expenses in the current group
     */
    public List<Expense> loadExpenseHistory() {
        try {
            // Check if user is logged in
            if (!session.isLoggedIn()) {
                navigator.showError("You must be logged in to view expenses");
                navigator.navigateToLogin();
                return null;
            }

            // Check if group is selected
            if (!session.hasGroupSelected()) {
                navigator.showError("No group selected");
                return null;
            }

            // Load expense history through ExpenseService
            // ExpenseService.getHistory expects: (groupId)
            List<Expense> expenses = expenseService.getHistory(
                    session.getCurrentGroup().getGroupId()
            );

            return expenses;

        } catch (EntityNotFoundException e) {
            navigator.showError("Group not found: " + e.getMessage());
            return null;
        } catch (Exception e) {
            navigator.showError("An unexpected error occurred: " + e.getMessage());
            return null;
        }
    }

    /**
     * Edits an existing expense.
     * Only the creator or an admin can edit an expense.
     * Balances are automatically recalculated if amount changes.
     *
     * @param expenseId the ID of the expense to edit
     * @param editorMembershipId the ID of the member's membership who is editing
     * @param newAmount the new amount (optional, null to keep current)
     * @param newDesc the new description (optional, null to keep current)
     * @param newCat the new category (optional, null to keep current)
     */
    public void editExpense(Long expenseId,
                            Long editorMembershipId,
                            BigDecimal newAmount,
                            String newDesc,
                            Category newCat) {
        try {
            // Validate input
            if (expenseId == null) {
                navigator.showError("Expense ID cannot be null");
                return;
            }

            if (editorMembershipId == null) {
                navigator.showError("Editor membership ID is required");
                return;
            }

            // At least one field must be provided
            if (newAmount == null && newDesc == null && newCat == null) {
                navigator.showError("At least one field must be updated");
                return;
            }

            // Validate amount if provided
            if (newAmount != null && newAmount.compareTo(BigDecimal.ZERO) <= 0) {
                navigator.showError("Amount must be positive");
                return;
            }

            // Check if user is logged in
            if (!session.isLoggedIn()) {
                navigator.showError("You must be logged in to edit an expense");
                navigator.navigateToLogin();
                return;
            }

            // Edit expense through ExpenseService
            // ExpenseService.editExpense expects: (expenseId, editorMembershipId, newAmount, newDescription, newCategory)
            expenseService.editExpense(
                    expenseId,
                    editorMembershipId,
                    newAmount,
                    newDesc,
                    newCat
            );

            // Success message
            navigator.showSuccess("Expense updated successfully!");

        } catch (EntityNotFoundException e) {
            navigator.showError("Expense not found: " + e.getMessage());
        } catch (UnauthorizedException e) {
            navigator.showError("Permission denied: " + e.getMessage());
        } catch (DomainException e) {
            navigator.showError("Error editing expense: " + e.getMessage());
        } catch (Exception e) {
            navigator.showError("An unexpected error occurred: " + e.getMessage());
        }
    }

    // --- Getter Methods ---

    /**
     * Gets the expense service.
     *
     * @return the ExpenseService instance
     */
    public ExpenseService getExpenseService() {
        return expenseService;
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
        return "ExpenseController{" +
                "currentGroup=" + (session.getCurrentGroup() != null ?
                session.getCurrentGroup().getName() : "none") +
                '}';
    }
}
