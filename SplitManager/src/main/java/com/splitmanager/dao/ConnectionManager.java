package com.splitmanager.dao;

import java.sql.SQLException; // <--- Import fondamentale per il catch del Service

public class ConnectionManager {

    // Singleton instance
    private static final ConnectionManager INSTANCE = new ConnectionManager();

    // Costruttore privato
    private ConnectionManager() {}

    public static ConnectionManager getInstance() {
        return INSTANCE;
    }

    public void beginTransaction() {
        // Stub: simula l'inizio transazione
    }

    public void commit() {
        // Stub: simula il commit
    }

    // Aggiungi 'throws SQLException' per far compilare il catch in ExpenseService
    public void rollback() throws SQLException {
        // Stub: simula il rollback
    }
}