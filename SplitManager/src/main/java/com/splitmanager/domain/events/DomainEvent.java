package com.splitmanager.domain.events;
import com.splitmanager.domain.events.EventType;

import java.util.Map;

// STUB: Classe temporanea
public class DomainEvent {
    public DomainEvent(Long eventId, EventType type, Long sourceId, Long triggeredBy, Map<String, Object> payload) {
        // vuoto, serve per far compilare Registry
    }

    public EventType getType() { return null; }

    // Aggiungere gli altri metodi e campi necessari
}
