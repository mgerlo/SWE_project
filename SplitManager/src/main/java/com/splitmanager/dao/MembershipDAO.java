package com.splitmanager.dao;

import com.splitmanager.domain.registry.Membership;
import com.splitmanager.domain.registry.Role;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class MembershipDAO {
// Metodi stub per simulare l'accesso ai dati (per far compilare)
    public List<Membership> findByGroup(Long groupId) {
        // Stub: ritorna lista vuota
        return Collections.emptyList();
    }

    // Stub: ritorna Optional vuoto
    public Optional<Membership> findById(Long membershipId) {
        return Optional.empty();
    }

    public Membership createMembership(Long userId, Long groupId, Role role) {
        // Stub: ritorniamo null (o un oggetto dummy se preferisci)
        // Attenzione: se il service usa questo oggetto, potrebbe dare NullPointerException
        return null;
    }

    public void update(Membership membership) {
        // Stub: non fa nulla
    }

    public List<Membership> findByUserAndGroup(Long userId, Long groupId) {
        return Collections.emptyList();
    }

    public List<Membership> findAllById(List<Long> participantIds) {
        return Collections.emptyList();
    }
}