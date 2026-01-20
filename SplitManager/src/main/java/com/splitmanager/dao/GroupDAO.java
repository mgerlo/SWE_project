package com.splitmanager.dao;

import com.splitmanager.domain.registry.Group;
import com.splitmanager.exception.DAOException;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Data Access Object per la gestione della persistenza dei Group.
 * 
 * RESPONSABILITÀ:
 * - CRUD operations su tabella 'groups'
 * - Gestione codici invito e scadenze
 * - Mapping ResultSet <-> Domain Object
 * 
 * PATTERN APPLICATI:
 * - DAO Pattern: disaccoppia logica di business dalla persistenza
 * - Singleton Connection: usa ConnectionManager per transazioni coordinate
 */
public class GroupDAO {
private final Connection connection;
    
    /**
     * Costruttore di default.
     * Ottiene la connessione dal Singleton ConnectionManager.
     */
    public GroupDAO() {
        this.connection = ConnectionManager.getInstance().getConnection();
    }
    
    /**
     * Salva o aggiorna un gruppo esistente.
     * 
     * Se il gruppo ha già un ID -> delega a update()
     * Altrimenti -> delega a create()
     * 
     * @param group oggetto Group da salvare
     * @return Group salvato con ID generato (se nuovo)
     * @throws DAOException in caso di errore SQL
     */
    public Group save(Group group) {
        // Se ha già un ID, è un UPDATE
        if (group.getGroupId() != null) {
            return update(group);
        }
        
        // Altrimenti è un INSERT
        // Questo metodo non può essere usato per CREATE senza userId
        throw new DAOException("Cannot save a new group without creator userId. Use create() instead.", null);
    }
    
    /**
     * Crea un nuovo gruppo e lo associa immediatamente al creatore.
     * 
     * FLUSSO:
     * 1. Inserisce record in tabella 'groups'
     * 2. Genera codice invito univoco
     * 3. Imposta scadenza invito a 48h
     * 4. Restituisce Group con ID generato
     * 
     * @param userId ID dell'utente creatore (diventerà ADMIN)
     * @param name nome del gruppo
     * @param currency valuta (es. "EUR", "USD")
     * @return Group creato con ID generato
     * @throws DAOException se inserimento fallisce
     */
    public Group create(Long userId, String name, String currency) {
       String sql = "INSERT INTO groups (name, description, currency, invite_code, " +
                    "invite_code_expiry_date, created_by_user_id, is_active) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            // Genera codice invito univoco (es. "A3F9K2L1")
            String inviteCode = generateInviteCode();
            
            // Scadenza invito: 48 ore da ora
            LocalDateTime expiry = LocalDateTime.now().plusHours(48);
            
            // Binding parametri
            stmt.setString(1, name);
            stmt.setString(2, null);
            stmt.setString(3, currency);
            stmt.setString(4, inviteCode);
            stmt.setTimestamp(5, Timestamp.valueOf(expiry));
            stmt.setLong(6, userId);
            stmt.setBoolean(7, true);
            
            // Esegue INSERT
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new DAOException("Creating group failed, no rows affected", null);
            }
            
            // Recupera ID autogenerato
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    Long groupId = keys.getLong(1);
                    
                    // Costruisce oggetto Group di dominio
                    Group group = new Group(groupId, name, currency);
                    group.setInviteCode(inviteCode);
                    group.setInviteCodeExpiry(expiry);
                    group.setActive(true);
                    
                    return group;
                } else {
                    throw new DAOException("Creating group failed, no ID obtained", null);
                }
            }
            
        } catch (SQLException e) {
            throw new DAOException("Error creating group", e);
        }
    }

    /**
     * Aggiorna un gruppo esistente nel database.
     * 
     * Aggiorna SOLO i campi modificabili:
     * - name
     * - description
     * - currency
     * - invite_code
     * - invite_code_expiry_date
     * - is_active
     * 
     * @param group oggetto Group con dati aggiornati
     * @return stesso oggetto Group (per fluent interface)
     * @throws DAOException se gruppo non trovato o errore SQL
     */
    public Group update(Group group) {
        String sql = "UPDATE groups SET name = ?, description = ?, currency = ?, " +
                    "invite_code = ?, invite_code_expiry_date = ?, is_active = ? " +
                    "WHERE group_id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, group.getName());
            stmt.setString(2, group.getDescription());
            stmt.setString(3, group.getCurrency());
            stmt.setString(4, group.getInviteCode());
            
            if (group.getInviteCodeExpiry() != null) {
                stmt.setTimestamp(5, Timestamp.valueOf(group.getInviteCodeExpiry()));
            } else {
                stmt.setNull(5, Types.TIMESTAMP);
            }
            
            stmt.setBoolean(6, group.isActive());
            stmt.setLong(7, group.getGroupId());
            
            int rows = stmt.executeUpdate();
            if (rows == 0) {
                throw new DAOException("Group not found: " + group.getGroupId(), null);
            }
            
            return group;
        } catch (SQLException e) {
            throw new DAOException("Error updating group", e);
        }
    }
    
    /**
     * Trova un gruppo per ID.
     * 
     * @param groupId ID del gruppo da cercare
     * @return Optional contenente Group se trovato, vuoto altrimenti
     * @throws DAOException in caso di errore SQL
     */
    public Optional<Group> findById(Long groupId) {
        String sql = "SELECT group_id, name, description, currency, invite_code, " +
                    "invite_code_expiry_date, is_active FROM groups WHERE group_id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, groupId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToGroup(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DAOException("Error finding group by ID", e);
        }
    }
    
    /**
     * Trova un gruppo attivo tramite codice invito.
     * 
     * @param inviteCode codice invito
     * @return Optional contenente Group se trovato e attivo, vuoto altrimenti
     * @throws DAOException in caso di errore SQL
     */
    public Optional<Group> findByInviteCode(String inviteCode) {
        String sql = "SELECT group_id, name, description, currency, invite_code, " +
                    "invite_code_expiry_date, is_active FROM groups WHERE invite_code = ? AND is_active = TRUE";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, inviteCode);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToGroup(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DAOException("Error finding group by invite code", e);
        }
    }
    
    /**
     * Mappa un ResultSet su un oggetto Group di dominio.
     * 
     * RESPONSABILITÀ: Conversione dati SQL -> oggetti Java
     * - Gestisce conversioni tipo (Timestamp -> LocalDateTime)
     * - Gestisce campi nullable (description, expiry)
     * 
     * @param rs ResultSet posizionato su una riga valida
     * @return oggetto Group popolato
     * @throws SQLException se errore nel leggere ResultSet
     */
    private Group mapResultSetToGroup(ResultSet rs) throws SQLException {
        Long groupId = rs.getLong("group_id");
        String name = rs.getString("name");
        String currency = rs.getString("currency");
        
        Group group = new Group(groupId, name, currency);
        group.setDescription(rs.getString("description"));
        group.setInviteCode(rs.getString("invite_code"));
        
        // Conversione Timestamp -> LocalDateTime (nullable)
        Timestamp expiry = rs.getTimestamp("invite_code_expiry_date");
        if (expiry != null) {
            group.setInviteCodeExpiry(expiry.toLocalDateTime());
        }
        
        group.setActive(rs.getBoolean("is_active"));
        
        return group;
    }
    
    /**
     * Genera un codice invito univoco alfanumerico di 8 caratteri.
     * 
     * ALGORITMO:
     * 1. Genera UUID casuale
     * 2. Rimuove trattini
     * 3. Taglia a 8 caratteri
     * 4. Converte in maiuscolo
     * 
     * @return stringa alfanumerica di 8 caratteri maiuscoli
     */
    private String generateInviteCode() {
        return UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();
    }
}