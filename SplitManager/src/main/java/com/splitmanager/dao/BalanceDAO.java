package com.splitmanager.dao;

import com.splitmanager.domain.accounting.Balance;
import com.splitmanager.domain.registry.Membership;
import com.splitmanager.domain.registry.User;
import com.splitmanager.domain.registry.Group;
import com.splitmanager.domain.registry.Role;
import com.splitmanager.domain.registry.MembershipStatus;
import com.splitmanager.exception.DAOException;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Data Access Object per la gestione della persistenza dei Balance.
 * 
 * RESPONSABILITÀ:
 * - CRUD operations su tabella 'balances'
 * - Gestione relazione 1:1 con Membership (ogni membership ha UN balance)
 * - Upsert pattern: inserisce se nuovo, aggiorna se esiste
 * - Query aggregate per gruppi (findByGroup)
 * 
 * PATTERN APPLICATI:
 * - Upsert Pattern: save() gestisce sia INSERT che UPDATE
 * - Lazy Loading: Ricostruisce oggetti Membership direttamente dal ResultSet evitando dipendenze circolari
 * - Self-Contained Mapping: Evita dipendenze da MembershipDAO per prevenire cicli infiniti
 * 
 * RISOLUZIONE DIPENDENZE CIRCOLARI:
 * Invece di dipendere da MembershipDAO (che creerebbe un ciclo BalanceDAO <-> MembershipDAO),
 * questo DAO ricostruisce autonomamente gli oggetti Membership necessari attraverso JOIN SQL.
 */
public class BalanceDAO {
    private final Connection connection;
    
    /**
     * Costruttore di default.
     */
    public BalanceDAO() {
        this.connection = ConnectionManager.getInstance().getConnection();
    }
    
    /**
     * Salva o aggiorna un balance (upsert pattern).
     * 
     * STRATEGIA UPSERT:
     * 1. Prova a fare INSERT
     * 2. Se fallisce per constraint violation
     *    -> Cattura eccezione e delega a update()
     * 3. Altrimenti ritorna balance con ID generato
     * 
     * @param balance oggetto Balance da salvare
     * @return Balance salvato/aggiornato
     * @throws DAOException se operazione fallisce
     */
    public Balance save(Balance balance) {
        String sql = "INSERT INTO balances (membership_id, net_balance, last_updated) VALUES (?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, balance.getMembership().getMembershipId());
            stmt.setBigDecimal(2, balance.getNetBalance());
            stmt.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new DAOException("Creating balance failed", null);
            }
            
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    Long balanceId = keys.getLong(1);
                    balance.setBalanceId(balanceId);
                }
            }
            
            return balance;
        } catch (SQLException e) {
            // Se già esiste, aggiorna
            if (e.getErrorCode() == 1062 || "23505".equals(e.getSQLState())) {
                return update(balance);
            }
            throw new DAOException("Error saving balance", e);
        }
    }
    
    /**
     * Aggiorna un balance esistente.
     * 
     * Aggiorna SOLO campi modificabili:
     * - net_balance (importo corrente del saldo)
     * - last_updated (timestamp ultima modifica)
     * 
     * @param balance oggetto Balance con dati aggiornati
     * @return stesso oggetto Balance (per fluent interface)
     * @throws DAOException se balance non trovato o errore SQL
     */
    public Balance update(Balance balance) {
        String sql = "UPDATE balances SET net_balance = ?, last_updated = ? WHERE membership_id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setBigDecimal(1, balance.getNetBalance());
            stmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setLong(3, balance.getMembership().getMembershipId());
            
            int rows = stmt.executeUpdate();
            if (rows == 0) {
                throw new DAOException("Balance not found for membership: " + balance.getMembership().getMembershipId(), null);
            }
            
            return balance;
        } catch (SQLException e) {
            throw new DAOException("Error updating balance", e);
        }
    }
    
    /**
     * Trova un balance per membership ID.
     * 
     * RELAZIONE 1:1: Ogni membership ha esattamente UN balance.
     * 
     * LAZY LOADING: Ricostruisce l'oggetto Membership necessario attraverso JOIN
     * invece di delegare a MembershipDAO, evitando dipendenze circolari.
     * 
     * @param membershipId ID della membership
     * @return Optional contenente Balance se trovato
     * @throws DAOException in caso di errore SQL
     */
    public Optional<Balance> findByMembershipId(Long membershipId) {
        String sql = "SELECT b.balance_id, b.membership_id, b.net_balance, b.last_updated, " +
                    "m.user_id, m.group_id, m.role, m.status, " +
                    "u.email, u.full_name, u.password_hash, " +
                    "g.name, g.description, g.currency, g.invite_code, " +
                    "g.invite_code_expiry_date, g.is_active " +
                    "FROM balances b " +
                    "JOIN memberships m ON b.membership_id = m.membership_id " +
                    "JOIN users u ON m.user_id = u.user_id " +
                    "JOIN groups g ON m.group_id = g.group_id " +
                    "WHERE b.membership_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, membershipId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToBalance(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DAOException("Error finding balance by membership ID", e);
        }
    }
    
    /**
     * Trova tutti i balance di un gruppo.
     * 
     * CASO D'USO: UC6 - View group balance
     * 
     * Utilizza JOIN per ottenere tutti i dati necessari in una singola query,
     * evitando N+1 query problem e dipendenze da altri DAO.
     * 
     * @param groupId ID del gruppo
     * @return Map da Membership al loro saldo netto
     * @throws DAOException in caso di errore SQL
     */
    public Map<Membership, BigDecimal> findByGroup(Long groupId) {
        String sql = "SELECT b.balance_id, b.membership_id, b.net_balance, b.last_updated, " +
                    "m.user_id, m.group_id, m.role, m.status, " +
                    "u.email, u.full_name, u.password_hash, " +
                    "g.name, g.description, g.currency, g.invite_code, " +
                    "g.invite_code_expiry_date, g.is_active " +
                    "FROM balances b " +
                    "JOIN memberships m ON b.membership_id = m.membership_id " +
                    "JOIN users u ON m.user_id = u.user_id " +
                    "JOIN groups g ON m.group_id = g.group_id " +
                    "WHERE m.group_id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, groupId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                Map<Membership, BigDecimal> balances = new HashMap<>();
                while (rs.next()) {
                    Membership membership = mapResultSetToMembership(rs);
                    BigDecimal amount = rs.getBigDecimal("net_balance");
                    balances.put(membership, amount);
                }
                return balances;
            }
        } catch (SQLException e) {
            throw new DAOException("Error finding balances by group", e);
        }
    }
    
    /**
     * Mappa un ResultSet su un oggetto Balance di dominio.
     * 
     * RESPONSABILITÀ:
     * - Ricostruisce oggetto Membership associato autonomamente dal ResultSet
     * - Converte Timestamp -> LocalDateTime
     * - Gestisce BigDecimal per precisione monetaria
     * 
     * PATTERN: Self-Contained Mapping per evitare dipendenze circolari
     * 
     * @param rs ResultSet posizionato su una riga valida
     * @return oggetto Balance popolato
     * @throws SQLException se errore nel leggere ResultSet
     */
    private Balance mapResultSetToBalance(ResultSet rs) throws SQLException {
        Membership membership = mapResultSetToMembership(rs);

        return new Balance(
            rs.getLong("balance_id"),
            membership,
            rs.getBigDecimal("net_balance"),
            rs.getTimestamp("last_updated").toLocalDateTime()
        );
    }

    /**
     * Ricostruisce un oggetto Membership dal ResultSet.
     * 
     * NOTA: Questa è una copia locale del mapping fatto da MembershipDAO.
     * La duplicazione è accettabile per evitare dipendenze circolari.
     * 
     * @param rs ResultSet contenente dati di membership, user e group
     * @return oggetto Membership ricostruito
     * @throws SQLException se errore nel leggere ResultSet
     */
    private Membership mapResultSetToMembership(ResultSet rs) throws SQLException {
        // Ricostruisce User
        User user = new User(
            rs.getLong("user_id"),
            rs.getString("email"),
            rs.getString("full_name"),
            rs.getString("password_hash")
        );
        
        // Ricostruisce Group
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
        
        // Ricostruisce Membership
        Membership membership = new Membership(
            rs.getLong("membership_id"),
            user,
            group,
            Role.valueOf(rs.getString("role"))
        );
        
        // Imposta lo status corretto
        MembershipStatus status = MembershipStatus.valueOf(rs.getString("status"));
        membership.changeStatus(status);
        
        return membership;
    }
}
