package com.splitmanager.domain.registry;

import com.splitmanager.domain.registry.MembershipStatus;
import com.splitmanager.domain.registry.Role;
import com.splitmanager.domain.events.DomainEvent;
import com.splitmanager.domain.events.Observer;
import com.splitmanager.domain.accounting.Balance;

import java.util.Objects;

public class Membership implements Observer {
    private Long membershipId;
    private User user;
    private Group group;
    private Balance balance; // Riferimento al bilancio personale
    private Role role;
    private MembershipStatus status;

    public Membership(Long membershipId, User user, Group group, Role role) {
        // Validazioni di base
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (group == null) {
            throw new IllegalArgumentException("Group cannot be null");
        }
        if (role == null) {
            throw new IllegalArgumentException("Role cannot be null");
        }

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
        String memberName = user != null ? user.getFullName() : "Unknown";
        System.out.printf("[%s] Event received: %s from source=%d%n",
                memberName, event.getType(), event.getSourceId());

        // Il Service aggiorna i Balance, qui solo notifica
    }

    // Getters e Equals
    public Long getMembershipId() {
        return membershipId;
    }

    public User getUser() {
        return user;
    }

    public Group getGroup() {
        return group;
    }

    public Balance getBalance() {
        return balance;
    }

    public Role getRole() {
        return role;
    }

    public MembershipStatus getStatus() {
        return status;
    }

    // Setters per DAO
    public void setMembershipId(Long membershipId) {
        this.membershipId = membershipId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Membership that = (Membership) o;
        return Objects.equals(membershipId, that.membershipId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(membershipId);
    }

    @Override
    public String toString() {
        return String.format("Membership[id=%d, user=%s, role=%s, status=%s]",
                membershipId, user.getFullName(), role, status);
    }
}