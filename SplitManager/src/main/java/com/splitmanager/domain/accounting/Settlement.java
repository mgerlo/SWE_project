package com.splitmanager.domain.accounting;

import com.splitmanager.domain.events.DomainEvent;
import com.splitmanager.domain.events.EventType;
import com.splitmanager.domain.events.Subject;
import com.splitmanager.domain.registry.Group;
import com.splitmanager.domain.registry.Membership;
import com.splitmanager.exception.UnauthorizedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/** * Represents a settlement payment between two members of a group.
 * A settlement is used to balance outstanding debts.
 */
public class Settlement extends Subject {

    private Long settlementId; // Tolto final per permettere al DAO di settarlo
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

        setAmount(amount); // Usa il metodo privato per validare
        this.date = date != null ? date : LocalDateTime.now();
        this.status = status != null ? status : PaymentStatus.PENDING;
    }

    /**
     * Costruttore di supporto (prima della persistenza).
     */
    public Settlement(Group group,
                      Membership payer,
                      Membership receiver,
                      BigDecimal amount) {
        this(null, group, payer, receiver, amount, LocalDateTime.now(), PaymentStatus.PENDING);
    }

    // --- Metodi di Autorizzazione (Mancavano) ---

    public boolean canBeConfirmedBy(Membership actor) {
        if (actor == null) return false;
        // Solo chi riceve i soldi può dire "Sì, li ho ricevuti"
        return this.receiver.equals(actor);
    }

    // --- Metodi di Business ---

    /**
     * Esegue la conferma del pagamento.
     * Rinominato da confirm() a executeConfirmation() come da UML.
     */
    public void executeConfirmation(Membership confirmedBy) {
        if (!canBeConfirmedBy(confirmedBy)) {
            throw new UnauthorizedException("Only the receiver can confirm this settlement");
        }

        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Only PENDING settlements can be confirmed");
        }

        this.status = PaymentStatus.COMPLETED;

        // ✅ FIX: Notifica Observer
        notifyObservers(createEvent(
                EventType.SETTLEMENT_CONFIRMED,
                confirmedBy,
                Map.of("amount", amount, "payerId", payer.getMembershipId())
        ));
    }

    /**
     * Annulla il settlement.
     * Rinominato da cancel() a executeCancellation() come da UML.
     */
    public void executeCancellation(Membership cancelledBy) {
        // Logica permissiva: Admin, Payer o Receiver possono annullare se è ancora pending
        if (!cancelledBy.isAdmin() && !cancelledBy.equals(payer) && !cancelledBy.equals(receiver)) {
            throw new UnauthorizedException("You do not have permission to cancel this settlement");
        }

        if (status == PaymentStatus.COMPLETED) {
            throw new IllegalStateException("Impossible to cancel a COMPLETED settlement");
        }

        this.status = PaymentStatus.REJECTED;
    }

    // --- Helper Eventi (Nuovo) ---

    public DomainEvent createEvent(EventType type, Membership triggeredBy, Map<String, Object> payload) {
        Long triggeredById = (triggeredBy != null) ? triggeredBy.getMembershipId() : null;
        return new DomainEvent(null, type, this.settlementId, triggeredById, payload);
    }

    // --- Metodi di supporto ---

    private void setAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Settlement amount must be positive");
        }
        this.amount = amount;
    }

    // --- Getter ---

    public Long getSettlementId() { return settlementId; }
    public Group getGroup() { return group; }
    public Membership getPayer() { return payer; }
    public Membership getReceiver() { return receiver; }
    public BigDecimal getAmount() { return amount; }
    public LocalDateTime getDate() { return date; }
    public PaymentStatus getStatus() { return status; }

    // --- Setter mancante per DAO ---

    public void setSettlementId(Long settlementId) {
        this.settlementId = settlementId;
    }

    // --- Utility ---

    public boolean isPending() {
        return status == PaymentStatus.PENDING;
    }

    public boolean isConfirmed() { // Manteniamo per retrocompatibilità o comodità
        return status == PaymentStatus.COMPLETED;
    }

    @Override
    public String toString() {
        String payerName = (payer != null && payer.getUser() != null)
                ? payer.getUser().getFullName()
                : "Unknown";

        String receiverName = (receiver != null && receiver.getUser() != null)
                ? receiver.getUser().getFullName()
                : "Unknown";

        return "Settlement{" +
                "id=" + settlementId +
                ", amount=" + amount +
                ", payer=" + payerName +
                ", receiver=" + receiverName +
                ", status=" + status +
                '}';
    }
}