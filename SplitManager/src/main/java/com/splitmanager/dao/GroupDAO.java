package com.splitmanager.dao;

import com.splitmanager.domain.registry.Group;
import java.util.Optional; // Fondamentale importare questo

public class GroupDAO {
// Metodi stub per simulare l'accesso ai dati (per far compilare)
    public Optional<Group> findById(Long groupId) {
        // Stub: simula "gruppo non trovato"
        return Optional.empty();
    }

    public Group save(Group group) {
        // Stub: restituisce il gruppo come se fosse stato salvato
        return group;
    }

    // CORRETTO: Restituisce Optional<Group>
    public Optional<Group> findByInviteCode(String inviteCode) {
        // Stub: simula "codice non valido"
        return Optional.empty();
    }

    public void update(Group group) {
        // Stub: metodo vuoto, non fa nulla
    }
}