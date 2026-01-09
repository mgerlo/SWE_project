package com.splitmanager.dao;

import com.splitmanager.domain.registry.User;
import java.util.Optional;

public class UserDAO {
// Metodi stub per simulare l'accesso ai dati (per far compilare)
    public boolean existsByEmail(String email) {
        // Stub: per ora diciamo che non esiste nessuno
        return false;
    }

    public User save(User user) {
        // Stub: restituiamo l'utente come se fosse stato salvato
        return user;
    }

    public Optional<User> findById(Long userId) {
        // Stub: restituiamo vuoto (così il Service lancerà EntityNotFoundException)
        return Optional.empty();
    }

    public void update(User updatedUser) {
        // Stub: metodo vuoto, non fa nulla
    }

    public Optional<User> findByEmail(String email) {
        // Stub: restituiamo vuoto
        return Optional.empty();
    }
}