package com.splitmanager.cli;

import com.splitmanager.businesslogic.controller.ExpenseController;
import com.splitmanager.businesslogic.controller.UserSession;
import com.splitmanager.businesslogic.service.ExpenseService;
import com.splitmanager.businesslogic.service.GroupService;
import com.splitmanager.domain.accounting.Category;
import com.splitmanager.domain.accounting.Expense;
import com.splitmanager.domain.registry.Membership;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Menu for expense management operations.
 */
public class ExpenseMenu {

    private final InputHandler input;
    private final ExpenseController expenseController;
    private final UserSession session;
    private final GroupService groupService;

    /**
     * Creates a new ExpenseMenu.
     *
     * @param input the input handler
     */
    public ExpenseMenu(InputHandler input) {
        this.input = input;
        this.session = UserSession.getInstance();
        ExpenseService expenseService = new ExpenseService();
        this.groupService = new GroupService();
        this.expenseController = new ExpenseController(expenseService, session, null);
    }

    /**
     * Shows the expense menu.
     */
    public void show() {
        // Check if a group is selected
        if (!session.hasGroupSelected()) {
            input.printError("Please select a group first.");
            input.waitForEnter();
            return;
        }

        while (true) {
            input.printHeader("EXPENSE MANAGEMENT");
            System.out.println("Group: " + session.getCurrentGroup().getName());
            input.printSeparator();

            System.out.println("1. Add New Expense");
            System.out.println("2. View Expense History");
            System.out.println("3. Edit Expense");
            System.out.println("4. Delete Expense");
            System.out.println("0. Back to Main Menu");
            input.printSeparator();

            int choice = input.readInt("Select an option: ", 0, 4);

            switch (choice) {
                case 1:
                    handleAddExpense();
                    break;
                case 2:
                    handleViewExpenseHistory();
                    break;
                case 3:
                    handleEditExpense();
                    break;
                case 4:
                    handleDeleteExpense();
                    break;
                case 0:
                    return; // Back to main menu
                default:
                    input.printError("Invalid option.");
            }
        }
    }

    /**
     * Handles adding a new expense.
     */
    private void handleAddExpense() {
        input.printHeader("ADD NEW EXPENSE");

        try {
            // Get expense details
            String description = input.readNonEmptyString("Description: ");
            BigDecimal amount = input.readAmount("Amount: ");

            // Select category
            Category category = selectCategory();
            if (category == null) {
                input.printInfo("Expense creation cancelled.");
                input.waitForEnter();
                return;
            }

            // Select participants
            List<Long> participantIds = selectParticipants();
            if (participantIds.isEmpty()) {
                input.printError("At least one participant must be selected.");
                input.waitForEnter();
                return;
            }

            // Note: This requires payer membership ID
            input.printInfo("This feature requires membership ID tracking.");
            input.printInfo("The expense would be created with:");
            System.out.println("- Description: " + description);
            System.out.println("- Amount: " + amount);
            System.out.println("- Category: " + category);
            System.out.println("- Participants: " + participantIds.size() + " members");
            input.waitForEnter();

        } catch (Exception e) {
            input.printError("Failed to add expense: " + e.getMessage());
            input.waitForEnter();
        }
    }

    /**
     * Handles viewing expense history.
     */
    private void handleViewExpenseHistory() {
        input.printHeader("EXPENSE HISTORY");

        try {
            List<Expense> expenses = expenseController.loadExpenseHistory();

            if (expenses == null || expenses.isEmpty()) {
                input.printInfo("No expenses found for this group.");
                input.waitForEnter();
                return;
            }

            System.out.println("\nExpenses:");
            input.printSeparator();

            for (int i = 0; i < expenses.size(); i++) {
                Expense expense = expenses.get(i);
                System.out.println((i + 1) + ". " + expense.getDescription());
                System.out.println("   Amount: " + expense.getAmount() +
                        " " + session.getCurrentGroup().getCurrency());
                System.out.println("   Category: " + expense.getCategory());
                System.out.println("   Paid by: " + expense.getPayer().getUser().getFullName());
                System.out.println("   Date: " + expense.getExpenseDate());
                System.out.println("   Participants: " + expense.getParticipants().size());
                input.printSeparator();
            }

            input.waitForEnter();

        } catch (Exception e) {
            input.printError("Failed to load expenses: " + e.getMessage());
            input.waitForEnter();
        }
    }

    /**
     * Handles editing an expense.
     */
    private void handleEditExpense() {
        input.printHeader("EDIT EXPENSE");

        try {
            List<Expense> expenses = expenseController.loadExpenseHistory();

            if (expenses == null || expenses.isEmpty()) {
                input.printInfo("No expenses found.");
                input.waitForEnter();
                return;
            }

            // Show expenses
            System.out.println("\nSelect an expense to edit:");
            input.printSeparator();
            for (int i = 0; i < expenses.size(); i++) {
                Expense expense = expenses.get(i);
                System.out.println((i + 1) + ". " + expense.getDescription() +
                        " - " + expense.getAmount());
            }

            int choice = input.readInt("\nSelect expense (0 to cancel): ", 0, expenses.size());

            if (choice == 0) {
                return;
            }

            Expense selectedExpense = expenses.get(choice - 1);

            // Edit options
            System.out.println("\nCurrent expense:");
            System.out.println("Description: " + selectedExpense.getDescription());
            System.out.println("Amount: " + selectedExpense.getAmount());
            System.out.println("Category: " + selectedExpense.getCategory());
            input.printSeparator();

            String newDesc = input.readString("New Description (press Enter to keep current): ");
            String amountStr = input.readString("New Amount (press Enter to keep current): ");

            BigDecimal newAmount = null;
            if (!amountStr.isEmpty()) {
                newAmount = new BigDecimal(amountStr);
            }

            Category newCategory = null;
            if (input.readConfirmation("Change category?")) {
                newCategory = selectCategory();
            }

            if (newDesc.isEmpty() && newAmount == null && newCategory == null) {
                input.printInfo("No changes made.");
                input.waitForEnter();
                return;
            }

            // Note: This requires editor membership ID
            input.printInfo("This feature requires membership ID tracking.");
            input.waitForEnter();

        } catch (Exception e) {
            input.printError("Failed to edit expense: " + e.getMessage());
            input.waitForEnter();
        }
    }

    /**
     * Handles deleting an expense.
     */
    private void handleDeleteExpense() {
        input.printHeader("DELETE EXPENSE");

        try {
            List<Expense> expenses = expenseController.loadExpenseHistory();

            if (expenses == null || expenses.isEmpty()) {
                input.printInfo("No expenses found.");
                input.waitForEnter();
                return;
            }

            // Show expenses
            System.out.println("\nSelect an expense to delete:");
            input.printSeparator();
            for (int i = 0; i < expenses.size(); i++) {
                Expense expense = expenses.get(i);
                System.out.println((i + 1) + ". " + expense.getDescription() +
                        " - " + expense.getAmount());
            }

            int choice = input.readInt("\nSelect expense (0 to cancel): ", 0, expenses.size());

            if (choice == 0) {
                return;
            }

            Expense selectedExpense = expenses.get(choice - 1);

            // Confirm deletion
            System.out.println("\nYou are about to delete:");
            System.out.println("Description: " + selectedExpense.getDescription());
            System.out.println("Amount: " + selectedExpense.getAmount());

            if (!input.readConfirmation("Are you sure you want to delete this expense?")) {
                input.printInfo("Deletion cancelled.");
                input.waitForEnter();
                return;
            }

            // Note: This requires deleter membership ID
            input.printInfo("This feature requires membership ID tracking.");
            input.waitForEnter();

        } catch (Exception e) {
            input.printError("Failed to delete expense: " + e.getMessage());
            input.waitForEnter();
        }
    }

    /**
     * Allows user to select a category.
     *
     * @return selected Category, or null if cancelled
     */
    private Category selectCategory() {
        System.out.println("\nSelect Category:");
        Category[] categories = Category.values();

        for (int i = 0; i < categories.length; i++) {
            System.out.println((i + 1) + ". " + categories[i]);
        }

        int choice = input.readInt("Select category (0 to cancel): ", 0, categories.length);

        if (choice == 0) {
            return null;
        }

        return categories[choice - 1];
    }

    /**
     * Allows user to select participants for the expense.
     *
     * @return list of selected membership IDs
     */
    private List<Long> selectParticipants() {
        List<Long> participantIds = new ArrayList<>();

        try {
            Long groupId = session.getCurrentGroup().getGroupId();
            List<Membership> members = groupService.getGroupMembers(groupId);

            if (members.isEmpty()) {
                input.printError("No members found in this group.");
                return participantIds;
            }

            System.out.println("\nSelect Participants (enter numbers separated by commas):");
            input.printSeparator();

            for (int i = 0; i < members.size(); i++) {
                Membership member = members.get(i);
                System.out.println((i + 1) + ". " + member.getUser().getFullName());
            }

            String selection = input.readString("\nParticipants (e.g., 1,2,3 or 'all'): ");

            if (selection.equalsIgnoreCase("all")) {
                // Add all members
                for (Membership member : members) {
                    participantIds.add(member.getMembershipId());
                }
            } else {
                // Parse comma-separated numbers
                String[] parts = selection.split(",");
                for (String part : parts) {
                    try {
                        int index = Integer.parseInt(part.trim()) - 1;
                        if (index >= 0 && index < members.size()) {
                            participantIds.add(members.get(index).getMembershipId());
                        }
                    } catch (NumberFormatException e) {
                        input.printError("Invalid number: " + part);
                    }
                }
            }

            if (!participantIds.isEmpty()) {
                input.printSuccess(participantIds.size() + " participants selected.");
            }

        } catch (Exception e) {
            input.printError("Failed to load members: " + e.getMessage());
        }

        return participantIds;
    }
}
