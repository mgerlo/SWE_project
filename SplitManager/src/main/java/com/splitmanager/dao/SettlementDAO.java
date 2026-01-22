package com.splitmanager.dao;

import com.splitmanager.domain.accounting.Settlement;
import com.splitmanager.domain.accounting.PaymentStatus;
import com.splitmanager.domain.registry.Group;
import com.splitmanager.domain.registry.Membership;
import com.splitmanager.exception.DAOException;

import java.math.BigDecimal;
import java.util.Collections;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object per la gestione della persistenza dei Settlement.
 * 
 * RESPONSABILITÀ:
 * - CRUD operations su tabella 'settlements'
 * - Gestione stato dei pagamenti (PENDING, COMPLETED, REJECTED)
 * - Query filtrate per status (findByPayer, findByReceiver)
 * 
 * DOMINIO:
 * Un Settlement rappresenta un rimborso tra due membri:
 * - payer: chi invia i soldi (debitore)
 * - receiver: chi riceve i soldi (creditore)
 * - status: PENDING (creato) -> COMPLETED (confermato) | REJECTED (annullato)
 * 
 * PATTERN APPLICATI:
 * - State Pattern: status evolve da PENDING -> COMPLETED/REJECTED
 * - Dependency Injection: GroupDAO e MembershipDAO iniettabili
 */
public class SettlementDAO {
    private final Connection connection;
    private final GroupDAO groupDAO;
    private final MembershipDAO membershipDAO;
    
    /**
     * Costruttore di default.
     * Crea istanze dei DAO dipendenti.
     */
    public SettlementDAO() {
        this.connection = ConnectionManager.getInstance().getConnection();
        this.groupDAO = new GroupDAO();
        this.membershipDAO = new MembershipDAO();
    }
    
    /**
     * Costruttore con Dependency Injection (per testing).
     * 
     * @param groupDAO istanza di GroupDAO da usare
     * @param membershipDAO istanza di MembershipDAO da usare
     */
    public SettlementDAO(GroupDAO groupDAO, MembershipDAO membershipDAO) {
        this.connection = ConnectionManager.getInstance().getConnection();
        this.groupDAO = groupDAO;
        this.membershipDAO = membershipDAO;
    }

    /**
     * Crea un nuovo settlement.
     * 
     * FACTORY METHOD: Incapsula logica di creazione.
     * 
     * FLUSSO:
     * 1. Carica Group, Payer, Receiver dal DB
     * 2. Crea oggetto Settlement di dominio (status = PENDING)
     * 3. Salva nel DB
     * 4. Restituisce Settlement con ID generato
     * 
     * @param groupId ID del gruppo
     * @param payerMembershipId ID del membro che paga
     * @param receiverMembershipId ID del membro che riceve
     * @param amount importo del rimborso
     * @return Settlement creato con ID generato
     * @throws DAOException se entità non trovate o errore SQL
     */
    public Settlement create(Long groupId, Long payerMembershipId, Long receiverMembershipId, BigDecimal amount) {
        Group group = groupDAO.findById(groupId)
            .orElseThrow(() -> new DAOException("Group not found: " + groupId, null));
        
        Membership payer = membershipDAO.findById(payerMembershipId)
            .orElseThrow(() -> new DAOException("Payer not found: " + payerMembershipId, null));
        
        Membership receiver = membershipDAO.findById(receiverMembershipId)
            .orElseThrow(() -> new DAOException("Receiver not found: " + receiverMembershipId, null));
        
        Settlement settlement = new Settlement(
            group,
            payer,
            receiver,
            amount
            // LocalDateTime.now() e PaymentStatus.PENDING sono default
        );
        
        return save(settlement);
    }
    
    /**
     * Salva un nuovo settlement nel database.
     * 
     * @param settlement oggetto Settlement da salvare
     * @return Settlement salvato con ID generato
     * @throws DAOException se inserimento fallisce
     */
    public Settlement save(Settlement settlement) {
        String sql = "INSERT INTO settlements (group_id, payer_membership_id, receiver_membership_id, " +
                    "amount, settlement_date, status) VALUES (?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, settlement.getGroup().getGroupId());
            stmt.setLong(2, settlement.getPayer().getMembershipId());
            stmt.setLong(3, settlement.getReceiver().getMembershipId());
            stmt.setBigDecimal(4, settlement.getAmount());
            stmt.setTimestamp(5, Timestamp.valueOf(settlement.getDate()));
            stmt.setString(6, settlement.getStatus().name());
            
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new DAOException("Creating settlement failed", null);
            }
            
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    Long settlementId = keys.getLong(1);
                    settlement.setSettlementId(settlementId);
                }
            }
            
            return settlement;
        } catch (SQLException e) {
            throw new DAOException("Error saving settlement", e);
        }
    }
    
    /**
     * Aggiorna un settlement esistente.
     * 
     * Aggiorna SOLO campi modificabili:
     * - amount (può essere rettificato prima della conferma)
     * - status (PENDING -> COMPLETED/REJECTED)
     * 
     * @param settlement oggetto Settlement con dati aggiornati
     * @throws DAOException se settlement non trovato o errore SQL
     */
    public void update(Settlement settlement) {
        String sql = "UPDATE settlements SET amount = ?, status = ? WHERE settlement_id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setBigDecimal(1, settlement.getAmount());
            stmt.setString(2, settlement.getStatus().name());
            stmt.setLong(3, settlement.getSettlementId());
            
            int rows = stmt.executeUpdate();
            if (rows == 0) {
                throw new DAOException("Settlement not found: " + settlement.getSettlementId(), null);
            }
        } catch (SQLException e) {
            throw new DAOException("Error updating settlement", e);
        }
    }
    
    /**
     * Trova un settlement per ID.
     * 
     * EAGER LOADING: Carica Group, Payer, Receiver tramite i rispettivi DAO.
     * 
     * @param settlementId ID del settlement
     * @return Optional contenente Settlement se trovato
     * @throws DAOException in caso di errore SQL
     */
    public Optional<Settlement> findById(Long settlementId) {
        String sql = "SELECT settlement_id, group_id, payer_membership_id, receiver_membership_id, " +
                    "amount, settlement_date, status FROM settlements WHERE settlement_id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, settlementId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToSettlement(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DAOException("Error finding settlement by ID", e);
        }
    }
    
    /**
     * Trova tutti i settlement di un gruppo.
     * 
     * ORDINAMENTO: ORDER BY settlement_date DESC
     * 
     * @param groupId ID del gruppo
     * @return lista di Settlement del gruppo
     * @throws DAOException in caso di errore SQL
     */
    public List<Settlement> findByGroup(Long groupId) {
        String sql = "SELECT settlement_id, group_id, payer_membership_id, receiver_membership_id, " +
                    "amount, settlement_date, status FROM settlements WHERE group_id = ? " +
                    "ORDER BY settlement_date DESC";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, groupId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                List<Settlement> settlements = new ArrayList<>();
                while (rs.next()) {
                    settlements.add(mapResultSetToSettlement(rs));
                }
                return settlements;
            }
        } catch (SQLException e) {
            throw new DAOException("Error finding settlements by group", e);
        }
    }
    
    /**
     * Trova tutti i settlement in cui un membro è il pagatore.
     * 
     * CASO D'USO: UC8 - Sttle debt with a member
     * 
     * @param payerMembershipId ID della membership pagatore
     * @return lista di Settlement in cui l'utente è payer
     * @throws DAOException in caso di errore SQL
     */
    public List<Settlement> findByPayer(Long payerMembershipId) {
        String sql = "SELECT settlement_id, group_id, payer_membership_id, receiver_membership_id, " +
                    "amount, settlement_date, status FROM settlements WHERE payer_membership_id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, payerMembershipId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                List<Settlement> settlements = new ArrayList<>();
                while (rs.next()) {
                    settlements.add(mapResultSetToSettlement(rs));
                }
                return settlements;
            }
        } catch (SQLException e) {
            throw new DAOException("Error finding settlements by payer", e);
        }
    }
    
    /**
     * Trova tutti i settlement in cui un membro è il ricevente.
     * 
     * CASO D'USO: UC8 - Sttle debt with a member
     * 
     * @param receiverMembershipId ID della membership ricevente
     * @return lista di Settlement in cui l'utente è receiver
     * @throws DAOException in caso di errore SQL
     */
    public List<Settlement> findByReceiver(Long receiverMembershipId) {
        String sql = "SELECT settlement_id, group_id, payer_membership_id, receiver_membership_id, " +
                    "amount, settlement_date, status FROM settlements WHERE receiver_membership_id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, receiverMembershipId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                List<Settlement> settlements = new ArrayList<>();
                while (rs.next()) {
                    settlements.add(mapResultSetToSettlement(rs));
                }
                return settlements;
            }
        } catch (SQLException e) {
            throw new DAOException("Error finding settlements by receiver", e);
        }
    }
    
    /**
     * Mappa un ResultSet su un oggetto Settlement di dominio.
     * 
     * RESPONSABILITÀ:
     * - Carica Group, Payer, Receiver tramite i rispettivi DAO
     * - Converte PaymentStatus da String a Enum
     * - Converte Timestamp a LocalDateTime
     *
     * @param rs ResultSet posizionato su una riga valida
     * @return oggetto Settlement popolato
     * @throws SQLException se errore nel leggere ResultSet
     */
    private Settlement mapResultSetToSettlement(ResultSet rs) throws SQLException {
        Long groupId = rs.getLong("group_id");
        Group group = groupDAO.findById(groupId)
            .orElseThrow(() -> new DAOException("Group not found: " + groupId, null));
        
        Long payerId = rs.getLong("payer_membership_id");
        Membership payer = membershipDAO.findById(payerId)
            .orElseThrow(() -> new DAOException("Payer not found: " + payerId, null));
        
        Long receiverId = rs.getLong("receiver_membership_id");
        Membership receiver = membershipDAO.findById(receiverId)
            .orElseThrow(() -> new DAOException("Receiver not found: " + receiverId, null));
        
        return new Settlement(
            rs.getLong("settlement_id"),
            group,
            payer,
            receiver,
            rs.getBigDecimal("amount"),
            rs.getTimestamp("settlement_date").toLocalDateTime(),
            PaymentStatus.valueOf(rs.getString("status"))
        );
    }
}
