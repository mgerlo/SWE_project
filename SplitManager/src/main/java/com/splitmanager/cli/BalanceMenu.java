package com.splitmanager.cli;

import com.splitmanager.businesslogic.controller.BalanceController;
import com.splitmanager.businesslogic.controller.UserSession;
import com.splitmanager.businesslogic.service.BalanceService;
import com.splitmanager.businesslogic.service.SettlementService;
import com.splitmanager.domain.accounting.Settlement;
import com.splitmanager.domain.registry.Membership;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Menu for balance and settlement operations.
 */
public class BalanceMenu {

    private final InputHandler input;
    private final BalanceController balanceController;
    private final UserSession session;

    /**
     * Creates a new BalanceMenu.
     *
     * @param input the input handler
     */
    public BalanceMenu(InputHandler input) {
        this.input = input;
        this.session = UserSession.getInstance();
        BalanceService balanceService = new BalanceService();
        SettlementService settlementService = new SettlementService();
        this.balanceController = new BalanceController(balanceService, settlementService, session);
    }

    /**
     * Shows the balance menu.
     */
    public void show() {
        // Check if a group is selected
        if (!session.hasGroupSelected()) {
            input.printError("Please select a group first.");
            input.waitForEnter();
            return;
        }

        while (true) {
            input.printHeader("BALANCE & SETTLEMENTS");
            System.out.println("Group: " + session.getCurrentGroup().getName());
            input.printSeparator();

            System.out.println("1. View Group Balances");
            System.out.println("2. View Optimized Debts");
            System.out.println("3. Settle Debt");
            System.out.println("4. View Pending Settlements");
            System.out.println("5. Confirm Settlement");
            System.out.println("0. Back to Main Menu");
            input.printSeparator();

            int choice = input.readInt("Select an option: ", 0, 5);

            switch (choice) {
                case 1:
                    handleViewBalances();
                    break;
                case 2:
                    handleViewOptimizedDebts();
                    break;
                case 3:
                    handleSettleDebt();
                    break;
                case 4:
                    handleViewPendingSettlements();
                    break;
                case 5:
                    handleConfirmSettlement();
                    break;
                case 0:
                    return; // Back to main menu
                default:
                    input.printError("Invalid option.");
            }
        }
    }

    /**
     * Handles viewing group balances.
     */
    private void handleViewBalances() {
        input.printHeader("GROUP BALANCES");

        try {
            Map<Membership, BigDecimal> balances = balanceController.viewBalances();

            if (balances == null || balances.isEmpty()) {
                input.printInfo("No balance information available.");
                input.waitForEnter();
                return;
            }

            System.out.println("\nBalances for " + session.getCurrentGroup().getName() + ":");
            input.printSeparator();

            String currency = session.getCurrentGroup().getCurrency();

            for (Map.Entry<Membership, BigDecimal> entry : balances.entrySet()) {
                Membership member = entry.getKey();
                BigDecimal balance = entry.getValue();

                String name = member.getUser().getFullName();
                String status;

                if (balance.compareTo(BigDecimal.ZERO) > 0) {
                    status = "gets back " + balance + " " + currency;
                } else if (balance.compareTo(BigDecimal.ZERO) < 0) {
                    status = "owes " + balance.abs() + " " + currency;
                } else {
                    status = "settled";
                }

                System.out.println(name + ": " + status);
            }

            input.printSeparator();

            // Check if group is fully settled
            if (balanceController.isGroupSettled()) {
                input.printSuccess("All balances are settled!");
            }

            input.waitForEnter();

        } catch (Exception e) {
            input.printError("Failed to load balances: " + e.getMessage());
            input.waitForEnter();
        }
    }

    /**
     * Handles viewing optimized debts (minimum transactions needed).
     */
    private void handleViewOptimizedDebts() {
        input.printHeader("OPTIMIZED DEBTS");

        try {
            List<Settlement> optimizedDebts = balanceController.viewOptimizedDebts();

            if (optimizedDebts == null || optimizedDebts.isEmpty()) {
                input.printSuccess("No debts to settle! Everyone is even.");
                input.waitForEnter();
                return;
            }

            System.out.println("\nSuggested payments to settle all debts:");
            System.out.println("(Minimum number of transactions)");
            input.printSeparator();

            String currency = session.getCurrentGroup().getCurrency();

            for (int i = 0; i < optimizedDebts.size(); i++) {
                Settlement settlement = optimizedDebts.get(i);
                String payer = settlement.getPayer().getUser().getFullName();
                String receiver = settlement.getReceiver().getUser().getFullName();
                BigDecimal amount = settlement.getAmount();

                System.out.println((i + 1) + ". " + payer + " pays " +
                        amount + " " + currency + " to " + receiver);
            }

            input.printSeparator();
            input.printInfo("Total transactions needed: " + optimizedDebts.size());
            input.waitForEnter();

        } catch (Exception e) {
            input.printError("Failed to calculate optimized debts: " + e.getMessage());
            input.waitForEnter();
        }
    }

    /**
     * Handles settling a debt (recording a payment).
     */
    private void handleSettleDebt() {
        input.printHeader("SETTLE DEBT");

        try {
            input.printInfo("This feature requires membership ID tracking.");
            input.printInfo("You would be able to:");
            System.out.println("- Select who you are paying");
            System.out.println("- Enter the amount");
            System.out.println("- Create a settlement pending receiver confirmation");
            input.waitForEnter();

        } catch (Exception e) {
            input.printError("Failed to settle debt: " + e.getMessage());
            input.waitForEnter();
        }
    }

    /**
     * Handles viewing pending settlements (awaiting confirmation).
     */
    private void handleViewPendingSettlements() {
        input.printHeader("PENDING SETTLEMENTS");

        try {
            List<Settlement> pendingSettlements = balanceController.getPendingSettlements();

            if (pendingSettlements == null || pendingSettlements.isEmpty()) {
                input.printInfo("No pending settlements.");
                input.waitForEnter();
                return;
            }

            System.out.println("\nPending Settlements:");
            input.printSeparator();

            String currency = session.getCurrentGroup().getCurrency();

            for (int i = 0; i < pendingSettlements.size(); i++) {
                Settlement settlement = pendingSettlements.get(i);
                String payer = settlement.getPayer().getUser().getFullName();
                String receiver = settlement.getReceiver().getUser().getFullName();
                BigDecimal amount = settlement.getAmount();

                System.out.println((i + 1) + ". " + payer + " -> " + receiver +
                        ": " + amount + " " + currency);
                System.out.println("   Status: PENDING");
                System.out.println("   Date: " + settlement.getDate());
                input.printSeparator();
            }

            input.waitForEnter();

        } catch (Exception e) {
            input.printError("Failed to load pending settlements: " + e.getMessage());
            input.waitForEnter();
        }
    }

    /**
     * Handles confirming a settlement (receiver confirms payment received).
     */
    private void handleConfirmSettlement() {
        input.printHeader("CONFIRM SETTLEMENT");

        try {
            List<Settlement> pendingSettlements = balanceController.getPendingSettlements();

            if (pendingSettlements == null || pendingSettlements.isEmpty()) {
                input.printInfo("No pending settlements to confirm.");
                input.waitForEnter();
                return;
            }

            System.out.println("\nSelect a settlement to confirm:");
            input.printSeparator();

            String currency = session.getCurrentGroup().getCurrency();

            for (int i = 0; i < pendingSettlements.size(); i++) {
                Settlement settlement = pendingSettlements.get(i);
                String payer = settlement.getPayer().getUser().getFullName();
                String receiver = settlement.getReceiver().getUser().getFullName();
                BigDecimal amount = settlement.getAmount();

                System.out.println((i + 1) + ". " + payer + " -> " + receiver +
                        ": " + amount + " " + currency);
            }

            int choice = input.readInt("\nSelect settlement (0 to cancel): ", 0, pendingSettlements.size());

            if (choice == 0) {
                return;
            }

            Settlement selectedSettlement = pendingSettlements.get(choice - 1);

            // Show details
            System.out.println("\nSettlement Details:");
            System.out.println("From: " + selectedSettlement.getPayer().getUser().getFullName());
            System.out.println("To: " + selectedSettlement.getReceiver().getUser().getFullName());
            System.out.println("Amount: " + selectedSettlement.getAmount() + " " + currency);
            input.printSeparator();

            if (!input.readConfirmation("Confirm that you received this payment?")) {
                input.printInfo("Confirmation cancelled.");
                input.waitForEnter();
                return;
            }

            input.printInfo("This feature requires membership ID tracking.");
            input.printInfo("The settlement would be confirmed and balances updated.");
            input.waitForEnter();

        } catch (Exception e) {
            input.printError("Failed to confirm settlement: " + e.getMessage());
            input.waitForEnter();
        }
    }
}
