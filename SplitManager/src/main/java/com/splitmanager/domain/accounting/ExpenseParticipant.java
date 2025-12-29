package com.splitmanager.domain.accounting;

import com.splitmanager.domain.registry.Membership;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Represents the participation of a member in an expense,
 * defining the share of the total amount assigned to them.
 */
public class ExpenseParticipant {

    private final Long participantId;
    private final Expense expense;
    private final Membership beneficiary;
    private BigDecimal shareAmount;

    // --- Costruttori ---

    /**
     * Costruttore per nuova partecipazione a una spesa.
     */
    public ExpenseParticipant(Long participantId,
                              Expense expense,
                              Membership beneficiary,
                              BigDecimal shareAmount) {

        this.participantId = participantId;
        this.expense = Objects.requireNonNull(expense, "Expense cannot be null");
        this.beneficiary = Objects.requireNonNull(beneficiary, "Beneficiary cannot be null");
        setShareAmount(shareAmount);
    }

    /**
     * Costruttore di supporto senza ID (es. prima della persistenza).
     */
    public ExpenseParticipant(Expense expense,
                              Membership beneficiary,
                              BigDecimal shareAmount) {

        this(null, expense, beneficiary, shareAmount);
    }

    // --- Metodi di Business ---

    /**
     * Modifica la quota assegnata al beneficiario.
     * Usato in caso di ricalcolo delle quote.
     */
    public void updateShareAmount(BigDecimal newAmount) {
        setShareAmount(newAmount);
    }

    private void setShareAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("La quota deve essere positiva");
        }
        this.shareAmount = amount;
    }

    // --- Getter ---

    public Long getParticipantId() {
        return participantId;
    }

    public Expense getExpense() {
        return expense;
    }

    public Membership getBeneficiary() {
        return beneficiary;
    }

    public BigDecimal getShareAmount() {
        return shareAmount;
    }

    // --- Equals / HashCode ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExpenseParticipant that = (ExpenseParticipant) o;

        if (participantId != null && that.participantId != null) {
            return Objects.equals(participantId, that.participantId);
        }

        return Objects.equals(expense, that.expense) &&
                Objects.equals(beneficiary, that.beneficiary);
    }

    @Override
    public int hashCode() {
        return participantId != null
                ? Objects.hash(participantId)
                : Objects.hash(expense, beneficiary);
    }

    @Override
    public String toString() {
        return "ExpenseParticipant{" +
                "beneficiary=" + beneficiary.getUser().getFullName() +
                ", shareAmount=" + shareAmount +
                '}';
    }
}
