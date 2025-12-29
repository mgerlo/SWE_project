package com.splitmanager.domain.accounting;

import com.splitmanager.domain.registry.Group;
import com.splitmanager.domain.registry.Membership;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Represents an expense within a group.
 * It defines who paid, who participates and how the amount is shared. */
public class Expense {

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

    /**
     * Costruttore per nuova spesa.
     */
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

    /**
     * Costruttore di supporto senza ID (prima della persistenza).
     */
    public Expense(Group group,
                   Membership payer,
                   Membership createdBy,
                   BigDecimal amount,
                   String description,
                   Category category,
                   LocalDateTime expenseDate) {

        this(null, group, payer, createdBy, amount, description, category, expenseDate);
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
        if (participants.remove(participant)) {
            touch();
        }
    }

    /** Verifica che la somma delle quote corrisponda all'importo totale. */
    public boolean isConsistent() {
        BigDecimal totalShares = participants.stream()
                .map(ExpenseParticipant::getShareAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return totalShares.compareTo(amount) == 0;
    }

    /** Modifica i dati principali della spesa. */
    public void update(BigDecimal newAmount,
                       String newDescription,
                       Category newCategory,
                       LocalDateTime newExpenseDate) {

        if (isDeleted) {
            throw new IllegalStateException("Cannot update a deleted expense");
        }

        if (newAmount != null) {
            setAmount(newAmount);
        }
        if (newDescription != null) {
            this.description = newDescription;
        }
        if (newCategory != null) {
            this.category = newCategory;
        }
        if (newExpenseDate != null) {
            this.expenseDate = newExpenseDate;
        }

        touch();
    }

    /**
     * Soft delete della spesa.
     */
    public void delete() {
        this.isDeleted = true;
        touch();
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

    public Long getExpenseId() {
        return expenseId;
    }

    public Group getGroup() {
        return group;
    }

    public Membership getPayer() {
        return payer;
    }

    public Membership getCreatedBy() {
        return createdBy;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public LocalDateTime getExpenseDate() {
        return expenseDate;
    }

    public LocalDateTime getLastModifiedDate() {
        return lastModifiedDate;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public List<ExpenseParticipant> getParticipants() {
        return Collections.unmodifiableList(participants);
    }

    @Override
    public String toString() {
        return "Expense{" +
                "amount=" + amount +
                ", description='" + description + '\'' +
                ", category=" + category +
                '}';
    }
}
