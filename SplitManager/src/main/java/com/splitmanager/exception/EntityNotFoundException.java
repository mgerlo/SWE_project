package com.splitmanager.exception;

/**
 * Eccezione lanciata quando un'entità richiesta non viene trovata nel database.
 *
 * Esempi:
 * - User con email "user@example.com" non trovato
 * - Group con ID 123 non trovato
 */
public class EntityNotFoundException extends DomainException {

    private final String entityName;
    private final Object entityId;

    public EntityNotFoundException(String entityName, Object entityId) {
        super(String.format("%s con ID '%s' non trovato", entityName, entityId));
        this.entityName = entityName;
        this.entityId = entityId;
    }

    // Costruttore alternativo per email
    public EntityNotFoundException(String entityName, String identifier, String identifierType) {
        super(String.format("%s con %s '%s' non trovato", entityName, identifierType, identifier));
        this.entityName = entityName;
        this.entityId = identifier;
    }

    public String getEntityName() {
        return entityName;
    }

    public Object getEntityId() {
        return entityId;
    }
}