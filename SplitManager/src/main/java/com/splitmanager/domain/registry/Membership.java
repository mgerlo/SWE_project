package com.splitmanager.domain.registry;

import com.splitmanager.domain.registry.MembershipStatus;
import com.splitmanager.domain.registry.Role;
import com.splitmanager.domain.events.DomainEvent;
import com.splitmanager.domain.events.Observer; // Interfaccia definita prima

import java.util.Objects;

public class Membership implements Observer {
    private Long membershipId;
    private User user;
    private Group group;
    private Balance balance; // Riferimento al bilancio personale
    private Role role;
    private MembershipStatus status;

    public Membership(Long membershipId, User user, Group group, Role role) {
        this.membershipId = membershipId;
        this.user = user;
        this.group = group;
        this.role = role;
        this.status = MembershipStatus.WAITING_ACCEPTANCE; // Default
    }

    // Setter per il wiring (in caso di dipendenza circolare/lazy load)
    public void setBalance(Balance balance) {
        this.balance = balance;
    }

    // --- Metodi di Business ---

    public boolean isAdmin() {
        return this.role == Role.ADMIN;
    }

    public boolean isActive() {
        return this.status == MembershipStatus.ACTIVE;
    }

    public boolean hasPendingDebts() {
        // Se non ha ancora un balance, tecnicamente non ha debiti
        if (balance == null) return false;
        return !balance.isSettled(); // Delega a Balance la verifica (netBalance == 0)
    }

    public boolean canBeRemoved() {
        // Regola: non puoi uscire se hai debiti pendenti
        return !hasPendingDebts();
    }

    public void activate() {
        this.status = MembershipStatus.ACTIVE;
    }

    public void terminate() {
        this.status = MembershipStatus.REMOVED;
    }

    public void changeRole(Role newRole) {
        this.role = newRole;
    }

    public void changeStatus(MembershipStatus newStatus) {
        this.status = newStatus;
    }

    // --- Implementazione Pattern Observer ---

    @Override
    public void onDomainEvent(DomainEvent event) {
        // Logica di reazione agli eventi.
        // Esempio: Se arriva una notifica di bilancio aggiornato, potremmo aggiornare cache locali
        // o loggare l'attività.

        System.out.println("Utente " + user.getFullName() + " ha ricevuto evento: " + event.getType());

        // Esempio concreto:
        // if (event.getType() == EventType.BALANCE_UPDATED && event.getPayload().containsKey(this.membershipId)) {
        //     this.balance.refresh();
        // }
    }

    // Getters e Equals
    public Long getMembershipId() { return membershipId; }
    public User getUser() { return user; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Membership that = (Membership) o;
        return Objects.equals(membershipId, that.membershipId);
    }
}