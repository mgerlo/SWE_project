package com.splitmanager.domain.registry;

import com.splitmanager.domain.events.EventType;
import com.splitmanager.domain.events.DomainEvent;
import com.splitmanager.domain.events.Subject;
import java.time.LocalDateTime;
import java.util.Map;

public class Group extends Subject {
    private Long groupId;
    private String name;
    private String description;
    private String currency;
    private String inviteCode;
    private LocalDateTime inviteCodeExpiry;
    private boolean isActive;

    public Group(Long groupId, String name, String currency) {
        this.groupId = groupId;
        this.name = name;
        this.currency = currency;
        this.isActive = true;
    }

    // --- Metodi di Business ---

    public boolean isInviteCodeValid(String code) {
        if (!this.isActive) return false;
        if (this.inviteCode == null || code == null) return false;

        // Verifica corrispondenza stringa e scadenza temporale
        boolean matches = this.inviteCode.equals(code);
        boolean notExpired = this.inviteCodeExpiry != null && LocalDateTime.now().isBefore(this.inviteCodeExpiry);

        return matches && notExpired;
    }

    public void updateGroupInfo(String name, String desc, String curr, Membership actor) {
        // Controllo permessi: solo ADMIN può modificare
        if (!actor.isAdmin()) {
            throw new SecurityException("Solo gli amministratori possono modificare le impostazioni del gruppo.");
        }

        this.name = name;
        this.description = desc;
        this.currency = curr;

        // Notifica gli observer del cambiamento
        DomainEvent event = createEvent(EventType.GROUP_SETTINGS_UPDATED, actor,
                Map.of("newName", name, "newCurrency", curr));
        notifyObservers(event);
    }

    public void updateInviteCode(String newCode, Membership actor) {
        if (!actor.isAdmin()) {
            throw new SecurityException("Solo gli amministratori possono generare codici invito.");
        }
        this.inviteCode = newCode;
        // Imposta scadenza a 48 ore (esempio di regola di business)
        this.inviteCodeExpiry = LocalDateTime.now().plusHours(48);

        notifyObservers(createEvent(EventType.INVITE_SENT, actor, Map.of("code", newCode)));
    }

    public void addMembership(Membership membership) {
        if (!this.isActive) {
            throw new IllegalStateException("Impossibile aggiungere membri a un gruppo disattivato.");
        }
        // Qui potresti chiamare attach() se il membro entra subito attivo
        if (membership.isActive()) {
            this.attach(membership);
        }
        notifyObservers(createEvent(EventType.MEMBER_JOINED, membership, Map.of("joinedMemberId", membership.getMembershipId())));
    }

    public void removeMembership(Membership target, Membership actor) {
        if (!canRemoveMember(actor, target)) {
            throw new SecurityException("Non hai i permessi per rimuovere questo membro.");
        }

        // Logica di rimozione delegata all'entità Membership
        target.terminate();
        this.detach(target); // Rimuove dalla lista observer

        notifyObservers(createEvent(EventType.MEMBER_REMOVED, actor, Map.of("removedMemberId", target.getMembershipId())));
    }

    public void deactivate() {
        this.isActive = false;
        // Nessuno riceverà più notifiche future dopo questo evento
        // (Logica gestita eventualmente nel Service svuotando gli observer)
    }

    // --- Controlli di Permessi ---

    public boolean canInviteMember(Membership actor) {
        return this.isActive && actor.isActive();
    }

    public boolean canRemoveMember(Membership actor, Membership target) {
        if (!this.isActive) return false;

        // Un utente può rimuovere se stesso (abbandonare)
        if (actor.equals(target)) return true;

        // Un admin può rimuovere altri (ma non se stesso se è l'ultimo admin, controllo extra da fare nel service)
        return actor.isAdmin();
    }

    // --- Helper per Eventi ---

    public DomainEvent createEvent(EventType type, Membership triggeredBy, Map<String, Object> payload) {
        // Crea un evento immutabile
        return new DomainEvent(
                null, // ID generato poi o nullo per eventi volatili
                type,
                this.groupId, // Source ID
                triggeredBy.getMembershipId(),
                payload
        );
    }

    // Getters necessari
    public Long getGroupId() { return groupId; }
    public boolean isActive() { return isActive; }
}