package com.splitmanager.domain.events;

public interface Observer {
    void onDomainEvent(DomainEvent event);
}