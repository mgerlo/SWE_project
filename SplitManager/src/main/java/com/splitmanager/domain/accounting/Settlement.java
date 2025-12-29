package com.splitmanager.domain.accounting;

import com.splitmanager.domain.registry.Group;
import com.splitmanager.domain.registry.Membership;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/** Represents a settlement payment between two members of a group.
 * A settlement is used to balance outstanding debts. */
public class Settlement {

    private final Long settlementId;
    private final Group group;
    private final Membership payer;
    private final Membership receiver;
    private BigDecimal amount;
    private final LocalDateTime date;
    private PaymentStatus status;

    // --- Costruttori ---

    /**
     * Costruttore principale.
     */
    public Settlement(Long settlementId,
                      Group group,
                      Membership payer,
                      Membership receiver,
                      BigDecimal amount,
                      LocalDateTime date,
                      PaymentStatus status) {

        this.settlementId = settlementId;
        this.group = Objects.requireNonNull(group, "Group cannot be null");
        this.payer = Objects.requireNonNull(payer, "Payer cannot be null");
        this.receiver = Objects.requireNonNull(receiver, "Receiver cannot be null");

        if (payer.equals(receiver)) {
            throw new IllegalArgumentException("Payer and receiver must be different");
        }

        setAmount(amount);
        this.date = date != null ? date : LocalDateTime.now();
        this.status = status != null ? status : PaymentStatus.CREATED;
    }

    /**
     * Costruttore di supporto (prima della persistenza).
     */
    public Settlement(Group group,
                      Membership payer,
                      Membership receiver,
                      BigDecimal amount) {

        this(null, group, payer, receiver, amount, LocalDateTime.now(), PaymentStatus.CREATED);
    }

    // --- Metodi di Business ---

    /**
     * Conferma il pagamento del settlement.
     */
    public void confirm() {
        if (status != PaymentStatus.CREATED) {
            throw new IllegalStateException("Only a CREATED settlement can be confirmed");
        }
        this.status = PaymentStatus.CONFIRMED;
    }

    /**
     * Annulla il settlement (se previsto dallo stato).
     */
    public void cancel() {
        if (status == PaymentStatus.CONFIRMED) {
            throw new IllegalStateException("A confirmed settlement cannot be cancelled");
        }
        this.status = PaymentStatus.CANCELLED;
    }

    // --- Metodi di supporto ---

    private void setAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Settlement amount must be positive");
        }
        this.amount = amount;
    }

    // --- Getter ---

    public Long getSettlementId() {
        return settlementId;
    }

    public Group getGroup() {
        return group;
    }

    public Membership getPayer() {
        return payer;
    }

    public Membership getReceiver() {
        return receiver;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    // --- Utility ---

    public boolean isConfirmed() {
        return status == PaymentStatus.CONFIRMED;
    }

    @Override
    public String toString() {
        return "Settlement{" +
                "amount=" + amount +
                ", payer=" + payer +
                ", receiver=" + receiver +
                ", status=" + status +
                '}';
    }
}
