package com.splitmanager.domain.accounting;

/** Represents the lifecycle status of a settlement payment. */
public enum PaymentStatus {

    /** The settlement has been created but not yet confirmed. */
    CREATED,

    /** The settlement has been completed and confirmed.
     *  This state triggers balance updates. */
    CONFIRMED,

    /** The settlement has been cancelled and has no accounting effect. */
    CANCELLED
}
