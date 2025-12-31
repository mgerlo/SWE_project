package com.splitmanager.exception;

/**
 * Eccezione lanciata quando viene violata una regola di business nel Domain Model.
 *
 * Esempi:
 * - "Impossibile aggiungere membri a un gruppo disattivato"
 * - "Il membro ha debiti pendenti e non può essere rimosso"
 * - "Impossibile eliminare una spesa già saldata"
 */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }

    public DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}