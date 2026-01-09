package com.splitmanager.dao;

import com.splitmanager.domain.accounting.Settlement;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class SettlementDAO {
    // Metodi stub per simulare l'accesso ai dati (per far compilare)
    public Optional<Settlement> findById(Long settlementId) {
        // Stub: restituiamo vuoto
        return Optional.empty();
    }

    public void update(Settlement settlement) {
        // Stub: metodo vuoto, non fa nulla
    }

    public Settlement create(Long groupId, Long payerMembershipId, Long receiverMembershipId, BigDecimal amount) {
        // Stub: Ritorna null per permettere la compilazione.
        // In produzione qui ci sarebbe la INSERT INTO e la creazione dell'oggetto.
        return null;
    }

    public List<Settlement> findByGroup(Long groupId) {
        // Stub: ritorna lista vuota
        return Collections.emptyList();
    }
}
