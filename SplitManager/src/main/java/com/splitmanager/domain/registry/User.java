package com.splitmanager.domain.registry;

import java.util.Objects;

public class User {
    private Long userId;
    private String email;
    private String fullName;
    private String password; // Hash della password

    // Costruttore
    public User(Long userId, String email, String fullName, String password) {
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
        this.password = password;
    }

    // --- Metodi di Business ---

    public boolean checkPassword(String input) {
        if (input == null) return false;
        return this.password.equals(input);
    }

    // Getters
    public Long getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }

    // Override equals/hashCode basati su ID o Email per coerenza
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(userId, user.userId);
    }
}
