package com.splitmanager.domain.accounting;

import com.splitmanager.domain.registry.Membership;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Rappresenta il saldo netto di un membro all'interno di un gruppo.
 * netBalance > 0  -> posizione creditoria
 * netBalance < 0  -> posizione debitoria
 * netBalance = 0  -> saldo chiuso
 */
public class Balance {

    private Long balanceId; // Tolto final per DAO
    private final Membership membership;
    private BigDecimal netBalance;
    private LocalDateTime lastUpdated;

    // --- Costruttori ---

    /**
     * Costruttore per nuova istanza di dominio.
     */
    public Balance(Long balanceId, Membership membership) {
        this.balanceId = balanceId;
        this.membership = Objects.requireNonNull(membership);
        this.netBalance = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.lastUpdated = LocalDateTime.now();
    }

    /**
     * Costruttore completo per ricostruzione da persistenza.
     */
    public Balance(Long balanceId,
                   Membership membership,
                   BigDecimal netBalance,
                   LocalDateTime lastUpdated) {

        this.balanceId = balanceId;
        this.membership = Objects.requireNonNull(membership);
        this.netBalance = netBalance != null ? netBalance : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.lastUpdated = lastUpdated != null ? lastUpdated : LocalDateTime.now();
    }

    // --- Metodi di Business ---

    /**
     * Indica se il saldo è chiuso.
     */
    public boolean isSettled() {
        return netBalance.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * Incrementa il saldo (posizione creditoria).
     */
    public void increment(BigDecimal amount) {
        validateAmount(amount);
        netBalance = netBalance.add(amount).setScale(2, RoundingMode.HALF_UP);
        touch();
    }

    /**
     * Decrementa il saldo (posizione debitoria).
     */
    public void decrement(BigDecimal amount) {
        validateAmount(amount);
        netBalance = netBalance.subtract(amount).setScale(2, RoundingMode.HALF_UP);
        touch();
    }

    /**
     * Applica una variazione firmata al saldo.
     */
    public void apply(BigDecimal delta) {
        if (delta == null || delta.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        netBalance = netBalance.add(delta).setScale(2, RoundingMode.HALF_UP);
        touch();
    }

    /**
     * Riporta il saldo a zero.
     */
    public void settle() {
        netBalance = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        touch();
    }

    // --- Metodi di supporto ---

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive.");
        }
    }

    private void touch() {
        this.lastUpdated = LocalDateTime.now();
    }

    // --- Getter ---

    public Long getBalanceId() {
        return balanceId;
    }

    // Fondamentale per il DAO
    public void setBalanceId(Long balanceId) {
        this.balanceId = balanceId;
    }

    public Membership getMembership() {
        return membership;
    }

    public BigDecimal getAmount() {
        return netBalance;
    }

    public BigDecimal getNetBalance() {
        return netBalance;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Balance balance = (Balance) o;

        // Se c'è l'ID, usa quello
        if (balanceId != null && balance.balanceId != null) {
            return Objects.equals(balanceId, balance.balanceId);
        }

        // Altrimenti basati sulla Membership (un membro ha un solo balance per gruppo)
        return Objects.equals(membership, balance.membership);
    }

    @Override
    public int hashCode() {
        return balanceId != null ? Objects.hash(balanceId) : Objects.hash(membership);
    }

    @Override
    public String toString() {
        return "Balance{" +
                "id=" + balanceId +
                ", member=" + (membership != null ? membership.getUser().getFullName() : "null") +
                ", net=" + netBalance +
                '}';
    }
}
