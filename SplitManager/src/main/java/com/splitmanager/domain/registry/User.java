package com.splitmanager.domain.registry;

import java.util.Objects;
import com.splitmanager.util.PasswordHasher;

public class User {
    private Long userId;
    private String email;
    private String fullName;
    private String passwordHash; // Hash della passwordHash

    // Costruttore
    public User(Long userId, String email, String fullName, String passwordHash) {
        // validazioni di base
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email non può essere vuota");
        }
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome completo non può essere vuoto");
        }
        if (passwordHash == null || passwordHash.trim().isEmpty()) {
            throw new IllegalArgumentException("password hash non può essere vuoto");
        }
        
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
    }

    public boolean checkPassword(String plainPassword) {
        if (plainPassword == null) return false;
        // Delega al PasswordHasher
        return PasswordHasher.verify(plainPassword, this.passwordHash);
    }

    // Getters
    public Long getUserId() {
        return userId;
    }
    public String getEmail() {
        return email;
    }
    public String getFullName() {
        return fullName;
    }
    public String getPasswordHash() {
        return passwordHash;
    }

    // Override equals/hashCode basati su ID o Email per coerenza
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(userId, user.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }

    @Override
    public String toString() {
        return String.format("User[id=%d, email=%s, name=%s]",
                userId, email, fullName);
    }
}
