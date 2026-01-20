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

    // --- Constructor ---

    /**
     * Creates a new BalanceController with the required dependencies.
     *
     * @param balanceService the balance service for balance operations
     * @param settlementService the settlement service for debt settlement operations
     * @param session the user session manager
     */
    public BalanceController(BalanceService balanceService,
                             SettlementService settlementService,
                             UserSession session) {

        if (balanceService == null) {
            throw new IllegalArgumentException("BalanceService cannot be null");
        }
        if (settlementService == null) {
            throw new IllegalArgumentException("SettlementService cannot be null");
        }
        if (session == null) {
            throw new IllegalArgumentException("UserSession cannot be null");
        }

        this.balanceService = balanceService;
        this.settlementService = settlementService;
        this.session = session;
    }

    // --- Business Methods (exactly as per UML) ---

    /**
     * Views the balances of all members in the current group.
     * Returns a map of each membership to their current balance.
     *
     * Positive balance = member has credit (others owe them)
     * Negative balance = member has debt (they owe others)
     * Zero balance = all settled
     *
     * @return map of Membership to their BigDecimal balance
     */
    public Map<Membership, BigDecimal> viewBalances() {
        try {
            // Check if user is logged in
            if (!session.isLoggedIn()) {
                return null;
            }

            // Check if group is selected
            if (!session.hasGroupSelected()) {
                return null;
            }

            // Get balances through BalanceService
            // BalanceService.getGroupBalances expects: (groupId)
            Map<Membership, BigDecimal> balances = balanceService.getGroupBalances(
                    session.getCurrentGroup().getGroupId()
            );

            return balances;

        } catch (EntityNotFoundException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Views the optimized list of settlements to balance all debts.
     * Uses the MinTransactionsStrategy to minimize the number of payments needed.
     *
     * This is useful for showing users the most efficient way to settle all debts
     * in the group with the minimum number of transactions.
     *
     * @return list of suggested Settlement objects
     */
    public List<Settlement> viewOptimizedDebts() {
        try {
            // Check if user is logged in
            if (!session.isLoggedIn()) {
                return null;
            }

            // Check if group is selected
            if (!session.hasGroupSelected()) {
                return null;
            }

            // Get optimized debts through BalanceService
            // BalanceService.getOptimizedDebts expects: (groupId)
            List<Settlement> optimizedSettlements = balanceService.getOptimizedDebts(
                    session.getCurrentGroup().getGroupId()
            );

            return optimizedSettlements;

        } catch (EntityNotFoundException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Settles a debt by recording a payment from payer to receiver.
     * Creates a Settlement in PENDING status that needs to be confirmed by the receiver.
     *
     * @param payerMembershipId the ID of the membership who is paying (the debtor)
     * @param receiverId the ID of the membership who will receive the payment (the creditor)
     * @param amount the amount to pay
     */
    public void settleDebt(Long payerMembershipId, Long receiverId, BigDecimal amount) {
        try {
            // Validate input
            if (payerMembershipId == null) {
                return;
            }

            if (receiverId == null) {
                return;
            }

            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }

            // Check if user is logged in
            if (!session.isLoggedIn()) {
                return;
            }

            // Check if group is selected
            if (!session.hasGroupSelected()) {
                return;
            }

            // Create settlement through SettlementService
            // SettlementService.createSettlement expects: (groupId, payerMembershipId, receiverMembershipId, amount)
            settlementService.createSettlement(
                    session.getCurrentGroup().getGroupId(),
                    payerMembershipId,
                    receiverId,
                    amount
            );

        } catch (EntityNotFoundException e) {
            // Handle error silently or log
        } catch (DomainException e) {
            // Handle error silently or log
        } catch (Exception e) {
            // Handle error silently or log
        }
    }

    /**
     * Confirms a settlement payment.
     * Only the receiver of the payment can confirm it.
     * Once confirmed, the balances are updated.
     *
     * @param settlementId the ID of the settlement to confirm
     * @param confirmerMembershipId the ID of the membership confirming (must be receiver)
     */
    public void confirmSettlement(Long settlementId, Long confirmerMembershipId) {
        try {
            // Validate input
            if (settlementId == null) {
                return;
            }

            if (confirmerMembershipId == null) {
                return;
            }

            // Check if user is logged in
            if (!session.isLoggedIn()) {
                return;
            }

            // Confirm settlement through SettlementService
            // SettlementService.confirmSettlement expects: (settlementId, confirmerMembershipId)
            settlementService.confirmSettlement(settlementId, confirmerMembershipId);

        } catch (EntityNotFoundException e) {
            // Settlement not found - handle error
        } catch (UnauthorizedException e) {
            // Only receiver can confirm - handle error
        } catch (DomainException e) {
            // Business logic error - handle error
        } catch (Exception e) {
            // Unexpected error - handle error
        }
    }

    // --- Additional Utility Methods ---

    /**
     * Cancels a pending settlement.
     * Can be cancelled by payer, receiver, or admin.
     *
     * @param settlementId the ID of the settlement to cancel
     * @param cancellerMembershipId the ID of the membership cancelling
     */
    public void cancelSettlement(Long settlementId, Long cancellerMembershipId) {
        try {
            // Validate input
            if (settlementId == null) {
                return;
            }

            if (cancellerMembershipId == null) {
                return;
            }

            // Check if user is logged in
            if (!session.isLoggedIn()) {
                return;
            }

            // Cancel settlement through SettlementService
            settlementService.cancelSettlement(settlementId, cancellerMembershipId);

        } catch (EntityNotFoundException e) {
            // Handle error
        } catch (UnauthorizedException e) {
            // Handle error
        } catch (DomainException e) {
            // Handle error
        } catch (Exception e) {
            // Handle error
        }
    }

    /**
     * Gets all pending settlements for the current group.
     * Useful for showing payments awaiting confirmation.
     *
     * @return list of pending settlements
     */
    public List<Settlement> getPendingSettlements() {
        try {
            // Check if user is logged in
            if (!session.isLoggedIn()) {
                return null;
            }

            // Check if group is selected
            if (!session.hasGroupSelected()) {
                return null;
            }

            return settlementService.getPendingSettlements(
                    session.getCurrentGroup().getGroupId()
            );

        } catch (EntityNotFoundException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gets all completed settlements for the current group.
     * Useful for payment history.
     *
     * @return list of completed settlements
     */
    public List<Settlement> getCompletedSettlements() {
        try {
            // Check if user is logged in
            if (!session.isLoggedIn()) {
                return null;
            }

            // Check if group is selected
            if (!session.hasGroupSelected()) {
                return null;
            }

            return settlementService.getCompletedSettlements(
                    session.getCurrentGroup().getGroupId()
            );

        } catch (EntityNotFoundException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Checks if the current group has all balances settled (all zero).
     *
     * @return true if all members have zero balance
     */
    public boolean isGroupSettled() {
        try {
            // Check if user is logged in
            if (!session.isLoggedIn()) {
                return false;
            }

            // Check if group is selected
            if (!session.hasGroupSelected()) {
                return false;
            }

            return balanceService.isGroupSettled(
                    session.getCurrentGroup().getGroupId()
            );

        } catch (EntityNotFoundException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    // --- Getter Methods ---

    /**
     * Gets the balance service.
     *
     * @return the BalanceService instance
     */
    public BalanceService getBalanceService() {
        return balanceService;
    }

    /**
     * Gets the settlement service.
     *
     * @return the SettlementService instance
     */
    public SettlementService getSettlementService() {
        return settlementService;
    }

    /**
     * Gets the user session.
     *
     * @return the UserSession instance
     */
    public UserSession getSession() {
        return session;
    }

    @Override
    public String toString() {
        return "BalanceController{" +
                "currentGroup=" + (session.getCurrentGroup() != null ?
                session.getCurrentGroup().getName() : "none") +
                '}';
    }
}
