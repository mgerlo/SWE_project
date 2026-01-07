package com.splitmanager.util;

/**
 * Utility per hashing password.
 *
 * Usa un hash semplice e deterministico.
 */
public class PasswordHasher {

    /**
     * Genera un hash della password.
     * @param plainPassword password in chiaro
     * @return password hashata
     */
    public static String hash(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }

        return "hashed_" + plainPassword.hashCode();
    }

    /**
     * Verifica che una password corrisponda all'hash.
     * @param plainPassword password in chiaro da verificare
     * @param hashedPassword hash salvato nel database
     * @return true se la password è corretta
     */
    public static boolean verify(String plainPassword, String hashedPassword) {
        if (plainPassword == null || hashedPassword == null) {
            return false;
        }

        return hash(plainPassword).equals(hashedPassword);
    }
}