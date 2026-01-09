package com.splitmanager.dao;

import com.splitmanager.domain.accounting.Category;
import com.splitmanager.domain.accounting.Expense;
import com.splitmanager.domain.accounting.ExpenseParticipant;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ExpenseDAO {
    // Metodi stub per simulare l'accesso ai dati (per far compilare)

    public Optional<Expense> findById(Long expenseId) {
        // Stub: ritorna vuoto
        return Optional.empty();
    }

    public void update(Expense expense) {
        // Stub: non fa nulla
    }

    public List<Expense> findByGroup(Long groupId) {
        // Stub: ritorna lista vuota
        return Collections.emptyList();
    }

    public Expense create(Long groupId, Long payerMembershipId, BigDecimal amount, String description, Category category, List<Long> participantIds) {
        // Stub: Ritorna null per far compilare.
        // ATTENZIONE: Se esegui il codice vero (non i test unitari mockati), questo darà errore.
        return null;
    }

    public void saveParticipant(ExpenseParticipant expenseParticipant) {
        // Stub: non fa nulla
    }
}