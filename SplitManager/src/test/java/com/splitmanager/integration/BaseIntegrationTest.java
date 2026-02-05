package com.splitmanager.integration;

import com.splitmanager.dao.*;
import org.junit.jupiter.api.BeforeEach;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Classe base per tutti i test di integrazione.
 * Fornisce metodi helper per setup e pulizia del database.
 */
public abstract class BaseIntegrationTest {
    
    protected Connection connection;
    protected UserDAO userDAO;
    protected GroupDAO groupDAO;
    protected MembershipDAO membershipDAO;
    protected BalanceDAO balanceDAO;
    protected ExpenseDAO expenseDAO;
    protected SettlementDAO settlementDAO;
    
    @BeforeEach
    void setUp() throws Exception {
        // Ottieni la connessione dal ConnectionManager
        connection = ConnectionManager.getInstance().getConnection();
        
        // Inizializza i DAO
        userDAO = new UserDAO();
        groupDAO = new GroupDAO();
        membershipDAO = new MembershipDAO(userDAO, groupDAO);
        balanceDAO = new BalanceDAO();
        expenseDAO = new ExpenseDAO(groupDAO, membershipDAO);
        settlementDAO = new SettlementDAO(groupDAO, membershipDAO);
        
        // Pulisci il database
        cleanDatabase();
    }
    
    protected void cleanDatabase() throws SQLException {
        // Disabilita i vincoli foreign key
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET REFERENTIAL_INTEGRITY FALSE");
            
            // Elimina in ordine inverso rispetto ai vincoli
            stmt.executeUpdate("DELETE FROM settlements");
            stmt.executeUpdate("DELETE FROM balances");
            stmt.executeUpdate("DELETE FROM expense_participants");
            stmt.executeUpdate("DELETE FROM expenses");
            stmt.executeUpdate("DELETE FROM memberships");
            stmt.executeUpdate("DELETE FROM groups");
            stmt.executeUpdate("DELETE FROM users");
            
            // Riabilita i vincoli
            stmt.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }
    
    // =========== METODI HELPER PER CREAZIONE DATI DI TEST ===========
    
    protected Long createUser(String email, String fullName, String password) throws SQLException {
        String sql = "INSERT INTO users (email, full_name, password_hash) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, email);
            stmt.setString(2, fullName);
            stmt.setString(3, password);
            stmt.executeUpdate();
            
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new RuntimeException("Failed to create user");
    }
    
    protected Long createGroup(String name, String currency, Long createdByUserId) throws SQLException {
        String inviteCode = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        LocalDateTime expiry = LocalDateTime.now().plusHours(48);
        
        String sql = "INSERT INTO groups (name, description, currency, invite_code, invite_code_expiry_date, " +
                     "created_by_user_id, is_active) VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name);
            stmt.setString(2, null);
            stmt.setString(3, currency);
            stmt.setString(4, inviteCode);
            stmt.setTimestamp(5, Timestamp.valueOf(expiry));
            stmt.setLong(6, createdByUserId);
            stmt.setBoolean(7, true);
            stmt.executeUpdate();
            
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new RuntimeException("Failed to create group");
    }
    
    protected Long createMembership(Long userId, Long groupId, String role, String status) throws SQLException {
        String sql = "INSERT INTO memberships (user_id, group_id, role, status) VALUES (?, ?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, userId);
            stmt.setLong(2, groupId);
            stmt.setString(3, role); // "ADMIN" o "MEMBER"
            stmt.setString(4, status); // "ACTIVE", "WAITING_ACCEPTANCE", "REMOVED"
            stmt.executeUpdate();
            
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    Long membershipId = rs.getLong(1);
                    
                    // Crea automaticamente il balance associato
                    createBalance(membershipId, BigDecimal.ZERO);
                    
                    return membershipId;
                }
            }
        }
        throw new RuntimeException("Failed to create membership");
    }
    
    protected void createBalance(Long membershipId, BigDecimal amount) throws SQLException {
        String sql = "INSERT INTO balances (membership_id, net_balance, last_updated) VALUES (?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, membershipId);
            stmt.setBigDecimal(2, amount);
            stmt.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            stmt.executeUpdate();
        }
    }
    
    protected void updateBalance(Long membershipId, BigDecimal newAmount) throws SQLException {
        String sql = "UPDATE balances SET net_balance = ?, last_updated = ? WHERE membership_id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setBigDecimal(1, newAmount);
            stmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setLong(3, membershipId);
            stmt.executeUpdate();
        }
    }
    
    protected BigDecimal getBalance(Long membershipId) throws SQLException {
        String sql = "SELECT net_balance FROM balances WHERE membership_id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, membershipId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("net_balance");
                }
            }
        }
        // Ritorna ZERO con scala 2 invece di BigDecimal.ZERO (scala 0)
        return new BigDecimal("0.00");
    }
    
    protected Long getMembershipId(Long userId, Long groupId) throws SQLException {
        String sql = "SELECT membership_id FROM memberships WHERE user_id = ? AND group_id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setLong(2, groupId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("membership_id");
                }
            }
        }
        throw new RuntimeException("Membership not found");
    }
}