package com.splitmanager.exception;

import java.sql.SQLException;

/**
 * Eccezione lanciata quando si verifica un errore di persistenza (database).
 * Wrappa SQLException per disaccoppiare il Service Layer da JDBC.
 */
public class DAOException extends RuntimeException {

    private final SQLException sqlException;

    public DAOException(String message, SQLException cause) {
        super(message, cause);
        this.sqlException = cause;
    }

    public DAOException(String message, Throwable cause) {
        super(message, cause);
        this.sqlException = null;
    }

    public SQLException getSqlException() {
        return sqlException;
    }
}