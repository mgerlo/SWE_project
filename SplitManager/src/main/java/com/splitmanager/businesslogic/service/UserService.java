// language: java
package com.splitmanager.service;

import com.splitmanager.dao.UserDAO;
import com.splitmanager.domain.registry.User;
import com.splitmanager.exception.DomainException;
import com.splitmanager.exception.EntityNotFoundException;
import com.splitmanager.util.PasswordHasher;

import java.util.Optional;

/**
 * Service per la gestione degli utenti.
 *
 * Responsabilità:
 * - Registrazione (UC1: Sign Up)
 * - Login (UC2: Login)
 * - Validazione credenziali
 */
public class UserService {

    private final UserDAO userDAO;

    public UserService() {
        this.userDAO = new UserDAO();
    }

    // Costruttore per dependency injection nei test
    public UserService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    /**
     * UC1 - Sign Up
     * Registra un nuovo utente nel sistema.
     *
     * @param email indirizzo email (deve essere univoco)
     * @param password password in chiaro (verrà hashata)
     * @param fullName nome completo dell'utente
     * @return User registrato con ID generato
     * @throws DomainException se email già registrata o dati non validi
     */
    public User signUp(String email, String password, String fullName) {
        // Validazione email
        if (email == null || !email.contains("@") || !email.contains(".")) {
            throw new DomainException("Invalid email");
        }

        // Validazione password
        if (password == null || password.length() < 8) {
            throw new DomainException("Password must be at least 8 characters");
        }

        // Validazione nome
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new DomainException("Full name is required");
        }

        // Verifica unicità email (UC1 Alternative 5a)
        if (userDAO.existsByEmail(email)) {
            throw new DomainException("Email already registered");
        }

        // Hash della password
        String hashedPassword = PasswordHasher.hash(password);

        // Crea e salva utente
        User user = new User(null, email, fullName, hashedPassword);
        return userDAO.save(user);
    }

    /**
     * UC2 - Login
     * Verifica le credenziali e restituisce l'utente se valide.
     *
     * @param email indirizzo email
     * @param password password in chiaro
     * @return User se credenziali valide
     * @throws EntityNotFoundException se email non trovata (UC2 Alternative 4a)
     * @throws DomainException se password errata (UC2 Alternative 4a)
     */
    public User login(String email, String password) {
        // Validazioni base
        if (email == null || password == null) {
            throw new DomainException("Email and password are required");
        }

        // Trova utente per email
        Optional<User> userOpt = userDAO.findByEmail(email);

        if (userOpt.isEmpty()) {
            // UC2 Alternative 4a: credenziali non valide
            throw new EntityNotFoundException("User", email, "email");
        }

        User user = userOpt.get();

        // Verifica password
        if (!user.checkPassword(password)) {
            throw new DomainException("Incorrect password");
        }

        return user;
    }

    /**
     * Aggiorna il profilo di un utente.
     * (Opzionale - non previsto dagli UC ma utile)
     *
     * @param userId ID utente
     * @param newFullName nuovo nome (opzionale)
     * @param newPassword nuova password (opzionale)
     * @throws EntityNotFoundException se utente non trovato
     */
    public void updateProfile(Long userId, String newFullName, String newPassword) {
        User user = userDAO.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User", userId));

        // Crea nuovo User con dati aggiornati
        String updatedName = (newFullName != null && !newFullName.trim().isEmpty())
                ? newFullName
                : user.getFullName();

        String updatedPasswordHash = (newPassword != null && newPassword.length() >= 8)
                ? PasswordHasher.hash(newPassword)
                : user.getPasswordHash();

        User updatedUser = new User(
                user.getUserId(),
                user.getEmail(), // Email non si può cambiare
                updatedName,
                updatedPasswordHash
        );

        userDAO.update(updatedUser);
    }
}
