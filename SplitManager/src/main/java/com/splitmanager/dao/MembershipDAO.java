package com.splitmanager.dao;

import com.splitmanager.domain.registry.Membership;
import com.splitmanager.domain.registry.Role;
import com.splitmanager.domain.registry.MembershipStatus;
import com.splitmanager.domain.registry.User;
import com.splitmanager.domain.registry.Group;
import com.splitmanager.exception.DAOException;

import java.sql.*;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object per la gestione della persistenza delle Membership.
 * 
 * RESPONSABILITÀ:
 * - CRUD operations su tabella 'memberships'
 * - Gestione relazione many-to-many User <-> Group
 * - Ricerca membership per user, group, o combinazione
 * - Batch loading (findAllById per efficienza)
 * 
 * PATTERN APPLICATI:
 * - Lazy Loading: NON carica automaticamente User e Group completi
 * - Eager Loading quando necessario: JOIN con users e groups
 * - Dependency Injection: UserDAO e GroupDAO iniettabili per testing
 * 
 * Ogni membership ha:
 * - Un ruolo (ADMIN o MEMBER)
 * - Uno stato (ACTIVE, WAITING_ACCEPTANCE, REMOVED)
 * - Un balance associato (gestito da BalanceDAO)
 */
public class MembershipDAO {
    private final Connection connection;
    private final UserDAO userDAO;
    private final GroupDAO groupDAO;
    
    /**
     * Costruttore di default.
     * Crea istanze dei DAO dipendenti.
     */
    public MembershipDAO() {
        this.connection = ConnectionManager.getInstance().getConnection();
        this.userDAO = new UserDAO();
        this.groupDAO = new GroupDAO();
    }
    
    /**
     * Costruttore con Dependency Injection (per testing).
     * 
     * @param userDAO istanza di UserDAO da usare
     * @param groupDAO istanza di GroupDAO da usare
     */
    public MembershipDAO(UserDAO userDAO, GroupDAO groupDAO) {
        this.connection = ConnectionManager.getInstance().getConnection();
        this.userDAO = userDAO;
        this.groupDAO = groupDAO;
    }
    
    /**
     * Crea una nuova membership con logica di business.
     * 
     * FLUSSO:
     * 1. Carica User e Group dal DB
     * 2. Crea oggetto Membership con Role specificato
     * 3. Imposta stato WAITING_ACCEPTANCE
     * 4. Salva nel DB
     * 
     * @param userId ID dell'utente
     * @param groupId ID del gruppo
     * @param role ruolo (ADMIN o MEMBER)
     * @return Membership creata con ID generato
     * @throws DAOException se user/group non trovati o errore SQL
     */
    public Membership createMembership(Long userId, Long groupId, Role role) {
        User user = userDAO.findById(userId)
            .orElseThrow(() -> new DAOException("User not found: " + userId, null));
        
        Group group = groupDAO.findById(groupId)
            .orElseThrow(() -> new DAOException("Group not found: " + groupId, null));
        
        Membership membership = new Membership(null, user, group, role);
        membership.changeStatus(MembershipStatus.WAITING_ACCEPTANCE);
        
        return save(membership);
    }
    
    /**
     * Salva una nuova membership nel database.
     * 
     * @param membership oggetto Membership da salvare
     * @return Membership salvata con ID generato
     * @throws DAOException se inserimento fallisce
     */
    public Membership save(Membership membership) {
        String sql = "INSERT INTO memberships (user_id, group_id, role, status) VALUES (?, ?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, membership.getUser().getUserId());
            stmt.setLong(2, membership.getGroup().getGroupId());
            stmt.setString(3, membership.getRole().name());
            stmt.setString(4, membership.getStatus().name());
            
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new DAOException("Creating membership failed", null);
            }
            
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    Long membershipId = keys.getLong(1);
                    membership.setMembershipId(membershipId);
                }
            }
            
            return membership;
        } catch (SQLException e) {
            throw new DAOException("Error saving membership", e);
        }
    }
    
    /**
     * Aggiorna una membership esistente.
     * 
     * Aggiorna SOLO campi modificabili:
     * - role (può essere promosso/retrocesso)
     * - status (WAITING_ACCEPTANCE -> ACTIVE -> REMOVED)
     * 
     * @param membership oggetto Membership con dati aggiornati
     * @throws DAOException se membership non trovata o errore SQL
     */
    public void update(Membership membership) {
        String sql = "UPDATE memberships SET role = ?, status = ? WHERE membership_id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, membership.getRole().name());
            stmt.setString(2, membership.getStatus().name());
            stmt.setLong(3, membership.getMembershipId());
            
            int rows = stmt.executeUpdate();
            if (rows == 0) {
                throw new DAOException("Membership not found: " + membership.getMembershipId(), null);
            }
        } catch (SQLException e) {
            throw new DAOException("Error updating membership", e);
        }
    }
    
    /**
     * Trova una membership per ID.
     * 
     * EAGER LOADING: Carica anche User e Group associati tramite JOIN.
     * 
     * @param membershipId ID della membership
     * @return Optional contenente Membership se trovata
     * @throws DAOException in caso di errore SQL
     */
    public Optional<Membership> findById(Long membershipId) {
        String sql = "SELECT m.membership_id, m.user_id, m.group_id, m.role, m.status, " +
                    "u.email, u.full_name, u.password_hash, " +
                    "g.name, g.description, g.currency, g.invite_code, " +
                    "g.invite_code_expiry_date, g.is_active " +
                    "FROM memberships m " +
                    "JOIN users u ON m.user_id = u.user_id " +
                    "JOIN groups g ON m.group_id = g.group_id " +
                    "WHERE m.membership_id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, membershipId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToMembership(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DAOException("Error finding membership by ID", e);
        }
    }
    
    /**
     * Trova le membership di un utente in un gruppo specifico.
     *
     * @param userId ID dell'utente
     * @param groupId ID del gruppo
     * @return lista di Membership
     * @throws DAOException in caso di errore SQL
     */
    public List<Membership> findByUserAndGroup(Long userId, Long groupId) {
        String sql = "SELECT m.membership_id, m.user_id, m.group_id, m.role, m.status, " +
                    "u.email, u.full_name, u.password_hash, " +
                    "g.name, g.description, g.currency, g.invite_code, " +
                    "g.invite_code_expiry_date, g.is_active " +
                    "FROM memberships m " +
                    "JOIN users u ON m.user_id = u.user_id " +
                    "JOIN groups g ON m.group_id = g.group_id " +
                    "WHERE m.user_id = ? AND m.group_id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setLong(2, groupId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                List<Membership> memberships = new ArrayList<>();
                while (rs.next()) {
                    memberships.add(mapResultSetToMembership(rs));
                }
                return memberships;
            }
        } catch (SQLException e) {
            throw new DAOException("Error finding memberships by user and group", e);
        }
    }
    
    /**
     * Carica multiple membership per ID (batch loading).
     * 
     * @param participantIds lista di ID delle membership da caricare
     * @return lista di Membership trovate
     * @throws DAOException in caso di errore SQL
     */
    public List<Membership> findAllById(List<Long> participantIds) {
        if (participantIds == null || participantIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        StringBuilder sqlBuilder = new StringBuilder(
            "SELECT m.membership_id, m.user_id, m.group_id, m.role, m.status, " +
            "u.email, u.full_name, u.password_hash, " +
            "g.name, g.description, g.currency, g.invite_code, " +
            "g.invite_code_expiry_date, g.is_active " +
            "FROM memberships m " +
            "JOIN users u ON m.user_id = u.user_id " +
            "JOIN groups g ON m.group_id = g.group_id " +
            "WHERE m.membership_id IN ("
        );
        
        for (int i = 0; i < participantIds.size(); i++) {
            sqlBuilder.append("?");
            if (i < participantIds.size() - 1) {
                sqlBuilder.append(",");
            }
        }
        sqlBuilder.append(")");
        
        String sql = sqlBuilder.toString();
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (int i = 0; i < participantIds.size(); i++) {
                stmt.setLong(i + 1, participantIds.get(i));
            }
            
            try (ResultSet rs = stmt.executeQuery()) {
                List<Membership> memberships = new ArrayList<>();
                while (rs.next()) {
                    memberships.add(mapResultSetToMembership(rs));
                }
                return memberships;
            }
        } catch (SQLException e) {
            throw new DAOException("Error finding memberships by IDs", e);
        }
    }
    
    /**
     * Trova tutte le membership di un gruppo.
     * 
     * @param groupId ID del gruppo
     * @return lista di tutte le Membership del gruppo
     * @throws DAOException in caso di errore SQL
     */
    public List<Membership> findByGroup(Long groupId) {
        String sql = "SELECT m.membership_id, m.user_id, m.group_id, m.role, m.status, " +
                    "u.email, u.full_name, u.password_hash, " +
                    "g.name, g.description, g.currency, g.invite_code, " +
                    "g.invite_code_expiry_date, g.is_active " +
                    "FROM memberships m " +
                    "JOIN users u ON m.user_id = u.user_id " +
                    "JOIN groups g ON m.group_id = g.group_id " +
                    "WHERE m.group_id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, groupId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                List<Membership> memberships = new ArrayList<>();
                while (rs.next()) {
                    memberships.add(mapResultSetToMembership(rs));
                }
                return memberships;
            }
        } catch (SQLException e) {
            throw new DAOException("Error finding memberships by group", e);
        }
    }
    
    /**
     * Trova tutte le membership di un utente (in tutti i gruppi).
     * 
     * @param userId ID dell'utente
     * @return lista di Membership dell'utente
     * @throws DAOException in caso di errore SQL
     */
    public List<Membership> findByUser(Long userId) {
        String sql = "SELECT m.membership_id, m.user_id, m.group_id, m.role, m.status, " +
                    "u.email, u.full_name, u.password_hash, " +
                    "g.name, g.description, g.currency, g.invite_code, " +
                    "g.invite_code_expiry_date, g.is_active " +
                    "FROM memberships m " +
                    "JOIN users u ON m.user_id = u.user_id " +
                    "JOIN groups g ON m.group_id = g.group_id " +
                    "WHERE m.user_id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                List<Membership> memberships = new ArrayList<>();
                while (rs.next()) {
                    memberships.add(mapResultSetToMembership(rs));
                }
                return memberships;
            }
        } catch (SQLException e) {
            throw new DAOException("Error finding memberships by user", e);
        }
    }
    
    /**
     * Mappa un ResultSet su un oggetto Membership di dominio.
     * 
     * RESPONSABILITÀ:
     * - Ricostruisce oggetti User e Group annidati
     * - Converte enum da String (Role, MembershipStatus)
     * - Gestisce Timestamp -> LocalDateTime
     * 
     * @param rs ResultSet posizionato su una riga valida
     * @return oggetto Membership popolato
     * @throws SQLException se errore nel leggere ResultSet
     */
    private Membership mapResultSetToMembership(ResultSet rs) throws SQLException {
        User user = new User(
            rs.getLong("user_id"),
            rs.getString("email"),
            rs.getString("full_name"),
            rs.getString("password_hash")
        );
        
        Group group = new Group(
            rs.getLong("group_id"),
            rs.getString("name"),
            rs.getString("currency")
        );
        group.setDescription(rs.getString("description"));
        group.setInviteCode(rs.getString("invite_code"));
        
        // Conversione nullable Timestamp -> LocalDateTime
        Timestamp expiry = rs.getTimestamp("invite_code_expiry_date");
        if (expiry != null) {
            group.setInviteCodeExpiry(expiry.toLocalDateTime());
        }
        
        group.setActive(rs.getBoolean("is_active"));
        
        Membership membership = new Membership(
            rs.getLong("membership_id"),
            user,
            group,
            Role.valueOf(rs.getString("role"))
        );
        
        String statusStr = rs.getString("status");
        switch (statusStr) {
            case "ACTIVE":
                membership.activate();
                break;
            case "REMOVED":
                membership.terminate();
                break;
            default:
                // WAITING_ACCEPTANCE è default
                break;
        }

        MembershipStatus status = MembershipStatus.valueOf(rs.getString("status"));
        membership.changeStatus(status);
        
        return membership;
    }
    
    private String membershipStatusToString(Membership membership) {
        if (membership.isActive()) {
            return "ACTIVE";
        } else if (membership.getStatus() == MembershipStatus.REMOVED) {
            return "REMOVED";
        } else {
            return "WAITING_ACCEPTANCE";
        }
    }
}