package com.splitmanager.domain.accounting;

public enum PaymentStatus {
    PENDING,     // In attesa di conferma
    COMPLETED,   // Confermato e completato
    REJECTED     // Rifiutato/annullato
}