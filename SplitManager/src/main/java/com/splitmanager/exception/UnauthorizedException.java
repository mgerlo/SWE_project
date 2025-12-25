package com.splitmanager.exception;

/**
 * Eccezione lanciata quando un utente tenta un'operazione per cui non ha i permessi.
 *
 * Esempi:
 * - Un Member normale cerca di rimuovere altri membri
 * - Un utente cerca di modificare un gruppo di cui non è Admin
 */
public class UnauthorizedException extends DomainException {

    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}