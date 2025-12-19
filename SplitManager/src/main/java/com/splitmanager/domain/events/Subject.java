package com.splitmanager.domain.events;

import java.util.List;

public abstract class Subject {
    private List<Observer> observers;

    public void attach(Observer observer) {}
    public void detach(Observer observer) {}
    protected void notifyObservers(DomainEvent event) {}
}
