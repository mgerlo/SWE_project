package com.splitmanager.businesslogic.controller;

import com.splitmanager.businesslogic.service.BalanceService;
import com.splitmanager.businesslogic.service.SettlementService;
import com.splitmanager.domain.accounting.Settlement;
import com.splitmanager.domain.registry.Membership;
import com.splitmanager.exception.DomainException;
import com.splitmanager.exception.EntityNotFoundException;
import com.splitmanager.exception.UnauthorizedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Controller that handles balance and settlement operations.
 * Manages viewing balances, optimizing debts, and settling debts between members.
 */
public class BalanceController {

    // Dependencies
    private final BalanceService balanceService;
    private final SettlementService settlementService;
    private final UserSession session;
    private final Navigator navigator;

    // --- Constructor ---

    /**
     * Creates a new BalanceController with the required dependencies.
     *
     * @param balanceService the balance service for balance operations
     * @param settlementService the settlement service for debt settlement operations
     * @param session the user session manager
     * @param navigator the navigation manager (Interface used for DI)
     */
    public BalanceController(BalanceService balanceService,
                             SettlementService settlementService,
                             UserSession session,
                             Navigator navigator) {

        if (balanceService == null) {
            throw new IllegalArgumentException("BalanceService cannot be null");
        }
        if (settlementService == null) {
            throw new IllegalArgumentException("SettlementService cannot be null");
        }
        if (session == null) {
            throw new IllegalArgumentException("UserSession cannot be null");
        }
        if (navigator == null) {
            throw new IllegalArgumentException("Navigator cannot be null");
        }

        this.balanceService = balanceService;
        this.settlementService = settlementService;
        this.session = session;
        this.navigator = navigator;
    }

    // --- Business Methods ---

    /**
     * Views the balances of all members in the current group.
     * @return map of Membership to their BigDecimal balance
     */
    public Map<Membership, BigDecimal> viewBalances() {
        try {
            if (!checkSession()) return null;

            Map<Membership, BigDecimal> balances = balanceService.getGroupBalances(
                    session.getCurrentGroup().getGroupId()
            );

            return balances;

        } catch (EntityNotFoundException e) {
            navigator.showError("Error retrieving balances: " + e.getMessage());
            return null;
        } catch (Exception e) {
            navigator.showError("Unexpected error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Views the optimized list of settlements.
     * @return list of suggested Settlement objects
     */
    public List<Settlement> viewOptimizedDebts() {
        try {
            if (!checkSession()) return null;

            return balanceService.getOptimizedDebts(
                    session.getCurrentGroup().getGroupId()
            );

        } catch (EntityNotFoundException e) {
            navigator.showError("Error calculating optimized debts: " + e.getMessage());
            return null;
        } catch (Exception e) {
            navigator.showError("Unexpected error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Settles a debt by recording a payment from payer to receiver.
     */
    public void settleDebt(Long payerMembershipId, Long receiverId, BigDecimal amount) {
        try {
            if (!checkSession()) return;

            // Validate input
            if (payerMembershipId == null || receiverId == null) {
                navigator.showError("Invalid member IDs");
                return;
            }

            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                navigator.showError("Amount must be positive");
                return;
            }

            settlementService.createSettlement(
                    session.getCurrentGroup().getGroupId(),
                    payerMembershipId,
                    receiverId,
                    amount
            );

            navigator.showSuccess("Settlement created successfully. Waiting for confirmation.");

        } catch (EntityNotFoundException e) {
            navigator.showError("Member not found: " + e.getMessage());
        } catch (DomainException e) {
            navigator.showError("Operation failed: " + e.getMessage());
        } catch (Exception e) {
            navigator.showError("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Confirms a settlement payment.
     */
    public void confirmSettlement(Long settlementId, Long confirmerMembershipId) {
        try {
            if (!checkSession()) return;

            if (settlementId == null || confirmerMembershipId == null) {
                navigator.showError("Invalid parameters");
                return;
            }

            settlementService.confirmSettlement(settlementId, confirmerMembershipId);
            navigator.showSuccess("Payment confirmed! Balances updated.");

        } catch (EntityNotFoundException e) {
            navigator.showError("Settlement not found: " + e.getMessage());
        } catch (UnauthorizedException e) {
            navigator.showError("Permission denied: Only the receiver can confirm this payment.");
        } catch (DomainException e) {
            navigator.showError("Cannot confirm: " + e.getMessage());
        } catch (Exception e) {
            navigator.showError("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Cancels a pending settlement.
     */
    public void cancelSettlement(Long settlementId, Long cancellerMembershipId) {
        try {
            if (!checkSession()) return;

            if (settlementId == null || cancellerMembershipId == null) {
                navigator.showError("Invalid parameters");
                return;
            }

            settlementService.cancelSettlement(settlementId, cancellerMembershipId);
            navigator.showSuccess("Settlement cancelled.");

        } catch (EntityNotFoundException e) {
            navigator.showError("Settlement not found");
        } catch (UnauthorizedException e) {
            navigator.showError("Permission denied: " + e.getMessage());
        } catch (DomainException e) {
            navigator.showError("Cannot cancel: " + e.getMessage());
        } catch (Exception e) {
            navigator.showError("Unexpected error: " + e.getMessage());
        }
    }

    public List<Settlement> getPendingSettlements() {
        try {
            if (!checkSession()) return null;
            return settlementService.getPendingSettlements(session.getCurrentGroup().getGroupId());
        } catch (Exception e) {
            navigator.showError("Error loading pending settlements: " + e.getMessage());
            return null;
        }
    }

    public List<Settlement> getCompletedSettlements() {
        try {
            if (!checkSession()) return null;
            return settlementService.getCompletedSettlements(session.getCurrentGroup().getGroupId());
        } catch (Exception e) {
            navigator.showError("Error loading history: " + e.getMessage());
            return null;
        }
    }

    public boolean isGroupSettled() {
        try {
            if (!checkSession()) return false;
            return balanceService.isGroupSettled(session.getCurrentGroup().getGroupId());
        } catch (Exception e) {
            return false;
        }
    }

    // --- Helper ---

    /**
     * Validates session state and navigates/shows error if invalid.
     */
    private boolean checkSession() {
        if (!session.isLoggedIn()) {
            navigator.showError("You must be logged in.");
            navigator.navigateToLogin();
            return false;
        }
        if (!session.hasGroupSelected()) {
            navigator.showError("No group selected.");
            return false;
        }
        return true;
    }

    // --- Getters ---

    public BalanceService getBalanceService() { return balanceService; }
    public SettlementService getSettlementService() { return settlementService; }
    public UserSession getSession() { return session; }
}