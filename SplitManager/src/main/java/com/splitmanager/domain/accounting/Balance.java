package com.splitmanager.domain.accounting;

import com.splitmanager.domain.registry.Membership;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Rappresenta il saldo netto di un membro all'interno di un gruppo.
 *
 * netBalance > 0  -> posizione creditoria
 * netBalance < 0  -> posizione debitoria
 * netBalance = 0  -> saldo chiuso
 */
public class Balance {

    private Long balanceId;
    private Membership membership;
    private BigDecimal netBalance;
    private LocalDateTime lastUpdated;

    // --- Costruttori ---

    /**
     * Costruttore per nuova istanza di dominio.
     */
    public Balance(Long balanceId, Membership membership) {
        this.balanceId = balanceId;
        this.membership = Objects.requireNonNull(membership);
        this.netBalance = BigDecimal.ZERO;
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
        this.netBalance = netBalance != null ? netBalance : BigDecimal.ZERO;
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
    public void increase(BigDecimal amount) {
        validateAmount(amount);
        netBalance = netBalance.add(amount);
        touch();
    }

    /**
     * Decrementa il saldo (posizione debitoria).
     */
    public void decrease(BigDecimal amount) {
        validateAmount(amount);
        netBalance = netBalance.subtract(amount);
        touch();
    }

    /**
     * Applica una variazione firmata al saldo.
     */
    public void apply(BigDecimal delta) {
        if (delta == null || delta.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        netBalance = netBalance.add(delta);
        touch();
    }

    /**
     * Riporta il saldo a zero.
     */
    public void settle() {
        netBalance = BigDecimal.ZERO;
        touch();
    }

    // --- Metodi di supporto ---

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("L'importo deve essere positivo");
        }
    }

    private void touch() {
        this.lastUpdated = LocalDateTime.now();
    }

    // --- Getter ---

    public Long getBalanceId() {
        return balanceId;
    }

    public Membership getMembership() {
        return membership;
    }

    public BigDecimal getNetBalance() {
        return netBalance;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    @Override
    public String toString() {
        return "Balance{" +
                "netBalance=" + netBalance +
                ", lastUpdated=" + lastUpdated +
                '}';
    }
}
