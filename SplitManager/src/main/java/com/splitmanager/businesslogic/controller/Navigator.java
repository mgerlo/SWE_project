package com.splitmanager.businesslogic.controller;

import com.splitmanager.domain.registry.Group;

/**
 * Interfaccia per gestire la navigazione e l'interazione utente.
 * Permette di disaccoppiare i Controller dall'implementazione concreta (Swing, Web, Console).
 * Fondamentale per il Testing (Dependency Inversion Principle).
 */
public interface Navigator {
    // Interazione Utente
    void showSuccess(String message);
    void showError(String message);
    boolean showConfirmation(String message);

    // Navigazione
    void navigateToLogin();
    void navigateToRegister();
    void navigateToHome();
    void navigateToGroupDetails(Group group);
}