package com.splitmanager.domain.events;

import com.splitmanager.domain.events.EventType;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class DomainEvent {
    private final Long eventId;
    private final EventType type;
    private final Long sourceId;
    private final LocalDateTime timestamp;
    private final Long triggeredBy;
    private final Map<String, Object> payload;

    public DomainEvent(Long eventId, EventType type, Long sourceId, Long triggeredBy, Map<String, Object> payload) {
        this.eventId = eventId;
        this.type = Objects.requireNonNull(type, "EventType cannot be null");
        this.sourceId = Objects.requireNonNull(sourceId, "SourceId cannot be null");
        this.triggeredBy = triggeredBy;
        this.timestamp = LocalDateTime.now();

        // Copia difensiva + immutabilità
        if (payload != null) {
            this.payload = Collections.unmodifiableMap(new HashMap<>(payload));
        } else {
            this.payload = Collections.emptyMap();
        }
    }

    public Long getEventId() {
        return eventId;
    }

    public EventType getType() { 
        return type;
    }
    
    public Long getSourceId() {
        return sourceId;
    }

    public Long getTriggeredBy() {
        return triggeredBy;
    }

    public Map<String, Object> getPayload() {
        // Restituisce il payload come mappa immutabile
        // Non può essere modificato dall'esterno
        return payload;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DomainEvent that = (DomainEvent) o;
        
        // Se entrambi hanno eventId, usa quello
        if (eventId != null && that.eventId != null) {
            return Objects.equals(eventId, that.eventId);
        }
        // Altrimenti confronta per contenuto
        return Objects.equals(type, that.type) &&
               Objects.equals(sourceId, that.sourceId) &&
               Objects.equals(triggeredBy, that.triggeredBy);
    }

    @Override
    public int hashCode() {
        return eventId != null ? Objects.hash(eventId) 
                               : Objects.hash(type, sourceId, triggeredBy);
    }

    @Override
    public String toString() {
        return String.format("DomainEvent[id=%s, type=%s, source=%d, triggeredBy=%s]",
                eventId, type, sourceId, triggeredBy);
    }    
}
