package com.splitmanager.domain.accounting;

import com.splitmanager.domain.events.DomainEvent;
import com.splitmanager.domain.events.EventType;
import com.splitmanager.domain.events.Subject;
import com.splitmanager.domain.registry.Group;
import com.splitmanager.domain.registry.Membership;
import com.splitmanager.exception.UnauthorizedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Represents an expense within a group.
 * It defines who paid, who participates and how the amount is shared. */
public class Expense extends Subject {

    private final Long expenseId;
    private final Group group;
    private final Membership payer;
    private final Membership createdBy;
    private BigDecimal amount;
    private String description;
    private Category category;
    private LocalDateTime expenseDate;
    private LocalDateTime lastModifiedDate;
    private boolean isDeleted;

    private final List<ExpenseParticipant> participants = new ArrayList<>();

    // --- Costruttori ---

    public Expense(Long expenseId,
                   Group group,
                   Membership payer,
                   Membership createdBy,
                   BigDecimal amount,
                   String description,
                   Category category,
                   LocalDateTime expenseDate) {

        this.expenseId = expenseId;
        this.group = Objects.requireNonNull(group, "Group cannot be null");
        this.payer = Objects.requireNonNull(payer, "Payer cannot be null");
        this.createdBy = Objects.requireNonNull(createdBy, "Creator cannot be null");
        setAmount(amount);
        this.description = description;
        this.category = category;
        this.expenseDate = expenseDate != null ? expenseDate : LocalDateTime.now();
        this.lastModifiedDate = LocalDateTime.now();
        this.isDeleted = false;
    }

    public Expense(Group group,
                   Membership payer,
                   Membership createdBy,
                   BigDecimal amount,
                   String description,
                   Category category,
                   LocalDateTime expenseDate) {

        this(null, group, payer, createdBy, amount, description, category, expenseDate);
    }

    // --- Metodi di Autorizzazione (Mancavano) ---

    public boolean isEditableBy(Membership actor) {
        if (actor == null) return false;
        // Solo admin o chi l'ha creata può modificarla
        return actor.isAdmin() || this.createdBy.equals(actor);
    }

    public boolean canBeDeletedBy(Membership actor) {
        if (actor == null) return false;
        return actor.isAdmin() || this.createdBy.equals(actor);
    }

    // --- Metodi di Business ---

    /** Aggiunge un partecipante alla spesa. */
    public void addParticipant(ExpenseParticipant participant) {
        Objects.requireNonNull(participant, "Participant cannot be null");

        if (isDeleted) {
            throw new IllegalStateException("Cannot modify a deleted expense");
        }

        if (!participants.contains(participant)) {
            participants.add(participant);
            touch();
        }
    }

    /** Rimuove un partecipante dalla spesa. */
    public void removeParticipant(ExpenseParticipant participant) {
        if (isDeleted) {
            throw new IllegalStateException("Cannot modify a deleted expense");
        }
        if (participants.remove(participant)) {
            touch();
        }
    }

    /** Verifica che la somma delle quote corrisponda all'importo totale. */
    public boolean isConsistent() {
        BigDecimal totalShares = participants.stream()
                .map(ExpenseParticipant::getShareAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Uso compareTo per sicurezza con BigDecimal
        return totalShares.compareTo(amount) == 0;
    }

    /**
     * Modifica i dati principali della spesa.
     * Rinominato da update() a modifyDetails() come da UML.
     */
    public void modifyDetails(BigDecimal newAmount,
                              String newDescription,
                              Category newCategory,
                              Membership modifiedBy) {

        // Controllo autorizzazioni
        if (!isEditableBy(modifiedBy)) {
            throw new UnauthorizedException("Solo admin o creatore possono modificare la spesa");
        }

        if (isDeleted) {
            throw new IllegalStateException("Cannot update a deleted expense");
        }

        if (newAmount != null) setAmount(newAmount);
        if (newDescription != null) this.description = newDescription;
        if (newCategory != null) this.category = newCategory;

        touch();

        // Notifica Observer
        notifyObservers(createEvent(
                EventType.EXPENSE_UPDATED,
                modifiedBy,
                Map.of("newAmount", this.amount, "description", this.description)
        ));
    }

    /**
     * Soft delete della spesa.
     * Rinominato da delete() a markAsDeleted() come da UML.
     */
    public void markAsDeleted(Membership deletedBy) {
        if (!canBeDeletedBy(deletedBy)) {
            throw new UnauthorizedException("Solo admin o creatore possono eliminare la spesa");
        }

        if (isDeleted) {
            throw new IllegalStateException("Expense already deleted");
        }

        this.isDeleted = true;
        touch();

        // Notifica Observer
        notifyObservers(createEvent(
                EventType.EXPENSE_DELETED,
                deletedBy,
                Map.of("amount", amount, "description", description)
        ));
    }

    // --- Helper Eventi ---

    public DomainEvent createEvent(EventType type, Membership triggeredBy, Map<String, Object> payload) {
        Long triggeredById = (triggeredBy != null) ? triggeredBy.getMembershipId() : null;
        return new DomainEvent(null, type, this.expenseId, triggeredById, payload);
    }

    // --- Metodi di supporto ---

    private void setAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("L'importo deve essere positivo");
        }
        this.amount = amount;
    }

    private void touch() {
        this.lastModifiedDate = LocalDateTime.now();
    }

    // --- Getter ---

    public List<Membership> getParticipants() {
        return participants.stream()
                .map(ExpenseParticipant::getBeneficiary)
                .collect(Collectors.toList());
    }

    public List<ExpenseParticipant> getParticipantDetails() {
        return Collections.unmodifiableList(participants);
    }

    public Long getExpenseId() { return expenseId; }
    public Group getGroup() { return group; }
    public Membership getPayer() { return payer; }
    public Membership getCreatedBy() { return createdBy; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public LocalDateTime getExpenseDate() { return expenseDate; }
    public LocalDateTime getLastModifiedDate() { return lastModifiedDate; }
    public boolean isDeleted() { return isDeleted; }


    @Override
    public String toString() {
        String payerName = (payer != null && payer.getUser() != null)
                ? payer.getUser().getFullName()
                : "Unknown";

        return "Expense{" +
                "id=" + expenseId +
                ", amount=" + amount +
                ", description='" + description + '\'' +
                ", payer=" + payerName +
                '}';
    }
}