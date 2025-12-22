package com.splitmanager.domain.events;

import java.util.ArrayList;
import java.util.List;

public abstract class Subject {
    private final List<Observer> observers = new ArrayList<>();

    public void attach(Observer observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
        }
    }
    
    public void detach(Observer observer) {
        observers.remove(observer);
    }
    
    protected void notifyObservers(DomainEvent event) {
        if (event == null) {
            return;
        }
        
        // Copia per sicurezza
        List<Observer> observersCopy = new ArrayList<>(observers);
        
        for (Observer observer : observersCopy) {
            try {
                observer.onDomainEvent(event);
            } catch (Exception e) {
                // Log errore ma continua con altri observer
                System.err.println("Error notifying observer: " + e.getMessage());
            }
        }
    }
}
