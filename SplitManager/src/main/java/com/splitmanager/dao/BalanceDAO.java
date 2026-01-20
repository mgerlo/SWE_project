package com.splitmanager.dao;

import com.splitmanager.domain.accounting.Balance;
import com.splitmanager.domain.registry.Membership;
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
 * - Dependency Injection: MembershipDAO iniettabile per evitare cicli
 * 
 */
public class BalanceDAO {
private final Connection connection;
    private final MembershipDAO membershipDAO;
    
    /**
     * Costruttore di default.
     * Crea istanza di MembershipDAO per caricare membership associate.
     */
    public BalanceDAO() {
        this.connection = ConnectionManager.getInstance().getConnection();
        this.membershipDAO = new MembershipDAO();
    }
    
    /**
     * Costruttore con Dependency Injection (per testing e evitare dipendenze circolari).
     * 
     * @param membershipDAO istanza di MembershipDAO da usare
     */
    public BalanceDAO(MembershipDAO membershipDAO) {
        this.connection = ConnectionManager.getInstance().getConnection();
        this.membershipDAO = membershipDAO;
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
     * @param membershipId ID della membership
     * @return Optional contenente Balance se trovato
     * @throws DAOException in caso di errore SQL
     */
    public Optional<Balance> findByMembershipId(Long membershipId) {
        String sql = "SELECT balance_id, membership_id, net_balance, last_updated FROM balances WHERE membership_id = ?";
        
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
     * @param groupId ID del gruppo
     * @return Map da Membership al loro saldo netto
     * @throws DAOException in caso di errore SQL
     */
    public Map<Membership, BigDecimal> findByGroup(Long groupId) {
        String sql = "SELECT b.balance_id, b.membership_id, b.net_balance, b.last_updated " +
                    "FROM balances b " +
                    "JOIN memberships m ON b.membership_id = m.membership_id " +
                    "WHERE m.group_id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, groupId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                Map<Membership, BigDecimal> balances = new HashMap<>();
                while (rs.next()) {
                    Long membershipId = rs.getLong("membership_id");
                    Membership membership = membershipDAO.findById(membershipId)
                        .orElseThrow(() -> new DAOException("Membership not found: " + membershipId, null));
                    
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
     * - Ricostruisce oggetto Membership associato (tramite MembershipDAO)
     * - Converte Timestamp -> LocalDateTime
     * - Gestisce BigDecimal per precisione monetaria
     * 
     * @param rs ResultSet posizionato su una riga valida
     * @return oggetto Balance popolato
     * @throws SQLException se errore nel leggere ResultSet
     */
    private Balance mapResultSetToBalance(ResultSet rs) throws SQLException {
        Long membershipId = rs.getLong("membership_id");
        Membership membership = membershipDAO.findById(membershipId)
            .orElseThrow(() -> new DAOException("Membership not found: " + membershipId, null));
        
        return new Balance(
            rs.getLong("balance_id"),
            membership,
            rs.getBigDecimal("net_balance"),
            rs.getTimestamp("last_updated").toLocalDateTime()
        );
    }
}
