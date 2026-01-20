package com.splitmanager.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConnectionManager {

    // Singleton instance
    private static final ConnectionManager INSTANCE = new ConnectionManager();
    private Connection connection;

    // Costruttore privato
    private ConnectionManager() {
        try {
            connection = DriverManager.getConnection(
                "jdbc:h2:mem:splitmanager;DB_CLOSE_DELAY=-1;INIT=RUNSCRIPT FROM 'schema.sql'",
                "sa",
                ""
            );
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to connect to database", e);
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public static ConnectionManager getInstance() {
        return INSTANCE;
    }

    public void beginTransaction() throws SQLException {
        connection.setAutoCommit(false);
    }

    public void commit() throws SQLException {
        connection.commit();
        connection.setAutoCommit(true);
    }

    public void rollback() throws SQLException {
        connection.rollback();
        connection.setAutoCommit(true);
    }
}