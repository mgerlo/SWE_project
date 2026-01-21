package com.splitmanager.dao;

import com.splitmanager.domain.registry.User;
import com.splitmanager.exception.DAOException;

import java.sql.*;
import java.util.Optional;

/**
 * Data Access Object per la gestione della persistenza degli User.
 * 
 * RESPONSABILITÀ:
 * - CRUD operations su tabella 'users'
 * - Ricerca per email (usato per login)
 * - Verifica esistenza email (per prevenire duplicati)
 * - Mapping ResultSet <-> User domain object
 * 
 * SICUREZZA:
 * - Le password sono SEMPRE hashate prima di arrivare al DAO
 * - Il DAO memorizza solo password_hash, MAI password in chiaro
 * - L'hashing è responsabilità del Service Layer (UserService + PasswordHasher)
 */
public class UserDAO {
    private final Connection connection;
    
    /**
     * Costruttore di default.
     * Ottiene la connessione dal Singleton ConnectionManager.
     */
    public UserDAO() {
        this.connection = ConnectionManager.getInstance().getConnection();
    }
    
    /**
     * Salva un nuovo utente nel database.
     * 
     * FLUSSO:
     * 1. INSERT in tabella users
     * 2. Recupera user_id autogenerato
     * 3. Crea nuovo oggetto User con ID
     * 4. Restituisce User persistito
     * 
     * @param user oggetto User da salvare
     * @return nuovo oggetto User con ID generato dal DB
     * @throws DAOException se inserimento fallisce
     */
    public User save(User user) {
        String sql = "INSERT INTO users (email, full_name, password_hash) VALUES (?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, user.getEmail());
            stmt.setString(2, user.getFullName());
            stmt.setString(3, user.getPasswordHash());
            
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new DAOException("Creating user failed", null);
            }
            
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    Long userId = keys.getLong(1);
                    // Crea nuovo User con ID
                    return new User(userId, user.getEmail(), user.getFullName(), user.getPasswordHash());
                } else {
                    throw new DAOException("No ID obtained for new user", null);
                }
            }
        } catch (SQLException e) {
            throw new DAOException("Error saving user", e);
        }
    }
    
    /**
     * Aggiorna un utente esistente.
     * 
     * Aggiorna SOLO campi modificabili:
     * - full_name
     * - password_hash (se cambiata)
     *
     * @param updatedUser oggetto User con dati aggiornati
     * @throws DAOException se utente non trovato o errore SQL
     */
    public void update(User updatedUser) {
        String sql = "UPDATE users SET full_name = ?, password_hash = ? WHERE user_id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, user.getFullName());
            stmt.setString(2, user.getPassword());
            stmt.setLong(3, user.getUserId());
            
            int rows = stmt.executeUpdate();
            if (rows == 0) {
                throw new DAOException("User not found: " + user.getUserId(), null);
            }
        } catch (SQLException e) {
            throw new DAOException("Error updating user", e);
        }
    }
    
    /**
     * Trova un utente per ID.
     * 
     * @param userId ID dell'utente da cercare
     * @return Optional contenente User se trovato, vuoto altrimenti
     * @throws DAOException in caso di errore SQL
     */
    public Optional<User> findById(Long userId) {
        String sql = "SELECT user_id, email, full_name, password_hash FROM users WHERE user_id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToUser(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DAOException("Error finding user by ID", e);
        }
    }
    
    /**
     * Trova un utente per email.
     * 
     * CASO D'USO: UC2 - Login
     * 
     * @param email indirizzo email da cercare
     * @return Optional contenente User se trovato, vuoto altrimenti
     * @throws DAOException in caso di errore SQL
     */
    public Optional<User> findByEmail(String email) {
        String sql = "SELECT user_id, email, full_name, password_hash FROM users WHERE email = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, email);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToUser(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DAOException("Error finding user by email", e);
        }
    }
    
    /**
     * Verifica se esiste un utente con la email specificata.
     * 
     * CASO D'USO: UC1 - Sign up
     * 
     * @param email indirizzo email da verificare
     * @return true se email già registrata, false altrimenti
     * @throws DAOException in caso di errore SQL
     */
    public boolean existsByEmail(String email) {
        String sql = "SELECT COUNT(*) as count FROM users WHERE email = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, email);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count") > 0;
                }
                return false;
            }
        } catch (SQLException e) {
            throw new DAOException("Error checking email existence", e);
        }
    }
    
    /**
     * Mappa un ResultSet su un oggetto User di dominio.
     * 
     * RESPONSABILITÀ: Conversione dati SQL -> oggetti Java
     * 
     * @param rs ResultSet posizionato su una riga valida
     * @return oggetto User popolato
     * @throws SQLException se errore nel leggere ResultSet
     */
    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        return new User(
            rs.getLong("user_id"),
            rs.getString("email"),
            rs.getString("full_name"),
            rs.getString("password_hash")
        );
    }
}