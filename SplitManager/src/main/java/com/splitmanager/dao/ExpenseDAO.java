package com.splitmanager.dao;

import com.splitmanager.domain.accounting.Category;
import com.splitmanager.domain.accounting.Expense;
import com.splitmanager.domain.accounting.ExpenseParticipant;
import com.splitmanager.domain.registry.Group;
import com.splitmanager.domain.registry.Membership;
import com.splitmanager.domain.registry.Role;
import com.splitmanager.domain.registry.User;
import com.splitmanager.exception.DAOException;

import java.math.BigDecimal;
import java.util.Collections;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object per la gestione della persistenza delle Expense.
 * 
 * RESPONSABILITÀ:
 * - CRUD operations su tabella 'expenses'
 * - Gestione tabella associativa 'expense_participants'
 * - Creazione transazionale: expense + partecipanti in un'unica operazione
 * - Lazy/Eager loading dei partecipanti
 * 
 * COMPLESSITÀ:
 * - Gestisce relazione 1:N con ExpenseParticipant
 * - Richiede JOIN multipli per ricostruire oggetti completi
 * - Coordina insert su 2 tabelle (expenses + expense_participants)
 * 
 * PATTERN APPLICATI:
 * - Composite Pattern: Expense contiene lista di ExpenseParticipant
 * - Lazy Loading: findById() può scegliere se caricare o no i partecipanti
 * - Factory Method: create() incapsula tutta la logica di creazione
 */
public class ExpenseDAO {
private final Connection connection;
    private final GroupDAO groupDAO;
    private final MembershipDAO membershipDAO;
    
    /**
     * Costruttore di default.
     * Crea istanze dei DAO dipendenti.
     */
    public ExpenseDAO() {
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
    public ExpenseDAO(GroupDAO groupDAO, MembershipDAO membershipDAO) {
        this.connection = ConnectionManager.getInstance().getConnection();
        this.groupDAO = groupDAO;
        this.membershipDAO = membershipDAO;
    }
    
    /**
     * Crea una nuova spesa completa con partecipanti.
     * 
     * FACTORY METHOD: Incapsula tutta la logica di creazione.
     * 
     * FLUSSO (operazione atomica):
     * 1. Carica Group e Payer dal DB
     * 2. Crea oggetto Expense di dominio
     * 3. Salva Expense -> genera expense_id
     * 4. Calcola quota per partecipante (split equo)
     * 5. Per ogni partecipante:
     *    - Crea ExpenseParticipant
     *    - Salva in expense_participants
     * 
     * @param groupId ID del gruppo
     * @param payerMembershipId ID del membro che ha pagato
     * @param amount importo totale della spesa
     * @param description descrizione
     * @param category categoria (FOOD, TRANSPORT, etc.)
     * @param participantIds lista di ID dei membri che partecipano
     * @return Expense creata con partecipanti associati
     * @throws DAOException se creazione fallisce
     */
    public Expense create(Long groupId, Long payerMembershipId, BigDecimal amount, 
                         String description, Category category, List<Long> participantIds) {
        
        Group group = groupDAO.findById(groupId)
            .orElseThrow(() -> new DAOException("Group not found: " + groupId, null));
        
        Membership payer = membershipDAO.findById(payerMembershipId)
            .orElseThrow(() -> new DAOException("Payer not found: " + payerMembershipId, null));
        
        Expense expense = new Expense(
            null,
            group,
            payer,
            payer, // createdBy = payer
            amount,
            description,
            category,
            LocalDateTime.now()
        );
        
        expense = save(expense);
        
        // Calcola la quota per ogni partecipante
        BigDecimal shareAmount = amount.divide(
            BigDecimal.valueOf(participantIds.size()),
            2,
            RoundingMode.HALF_UP
        );
        
        // Crea i partecipanti
        for (Long participantId : participantIds) {
            Membership participant = membershipDAO.findById(participantId)
                .orElseThrow(() -> new DAOException("Participant not found: " + participantId, null));
            
            ExpenseParticipant expenseParticipant = new ExpenseParticipant(
                null,
                expense,
                participant,
                shareAmount
            );
            
            saveParticipant(expenseParticipant);
        }
        
        return expense;
    }
    
    /**
     * Salva o aggiorna un'expense.
     * 
     * LOGICA:
     * - Se expense.getExpenseId() != null -> UPDATE
     * - Altrimenti -> INSERT
     * 
     * @param expense oggetto Expense da salvare
     * @return Expense salvato/aggiornato
     * @throws DAOException se operazione fallisce
     */
    public Expense save(Expense expense) {
        // Se l'expense ha già un ID, è un UPDATE
        if (expense.getExpenseId() != null) {
            update(expense);
            return expense;
        }
        
        // Altrimenti è un INSERT
        String sql = "INSERT INTO expenses (group_id, payer_membership_id, created_by_membership, " +
                    "amount, description, category, expense_date, last_modified_date, is_deleted) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setLong(1, expense.getGroup().getGroupId());
            stmt.setLong(2, expense.getPayer().getMembershipId());
            stmt.setLong(3, expense.getCreatedBy().getMembershipId());
            stmt.setBigDecimal(4, expense.getAmount());
            stmt.setString(5, expense.getDescription());
            stmt.setString(6, expense.getCategory().name());
            stmt.setTimestamp(7, Timestamp.valueOf(expense.getExpenseDate()));
            stmt.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setBoolean(9, expense.isDeleted());
            
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new DAOException("Creating expense failed", null);
            }
            
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    Long expenseId = keys.getLong(1);
                    expense.setExpenseId(expenseId);
                }
            }
            
            return expense;
        } catch (SQLException e) {
            throw new DAOException("Error saving expense", e);
        }
    }
    
    /**
     * Salva un singolo ExpenseParticipant.
     * 
     * Inserisce una riga nella tabella 'expense_participants'.
     * 
     * @param expenseParticipant oggetto da salvare
     * @throws DAOException se inserimento fallisce
     */
    public void saveParticipant(ExpenseParticipant expenseParticipant) {
        String sql = "INSERT INTO expense_participants (expense_id, beneficiary_membership_id, share_amount) " +
                    "VALUES (?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setLong(1, expenseParticipant.getExpense().getExpenseId());
            stmt.setLong(2, expenseParticipant.getBeneficiary().getMembershipId());
            stmt.setBigDecimal(3, expenseParticipant.getShareAmount());
            
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new DAOException("Creating expense participant failed", null);
            }
            
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    Long participantId = keys.getLong(1);
                    expenseParticipant.setParticipantId(participantId);
                }
            }
        } catch (SQLException e) {
            throw new DAOException("Error saving expense participant", e);
        }
    }
    
    /**
     * Aggiorna un'expense esistente.
     * 
     * Aggiorna SOLO campi modificabili:
     * - amount
     * - description
     * - category
     * - last_modified_date (sempre aggiornato)
     * - is_deleted (soft delete)
     * 
     * @param expense oggetto Expense con dati aggiornati
     * @throws DAOException se expense non trovata o errore SQL
     */
    public void update(Expense expense) {
        String sql = "UPDATE expenses SET amount = ?, description = ?, category = ?, " +
                    "last_modified_date = ?, is_deleted = ? WHERE expense_id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            
            stmt.setBigDecimal(1, expense.getAmount());
            stmt.setString(2, expense.getDescription());
            stmt.setString(3, expense.getCategory().name());
            stmt.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setBoolean(5, expense.isDeleted());
            stmt.setLong(6, expense.getExpenseId());
            
            int rows = stmt.executeUpdate();
            if (rows == 0) {
                throw new DAOException("Expense not found: " + expense.getExpenseId(), null);
            }
        } catch (SQLException e) {
            throw new DAOException("Error updating expense", e);
        }
    }
    
    /**
     * Trova un'expense per ID con i suoi partecipanti.
     * 
     * EAGER LOADING: Carica automaticamente tutti i partecipanti.
     * 
     * @param expenseId ID dell'expense da cercare
     * @return Optional contenente Expense se trovata
     * @throws DAOException in caso di errore SQL
     */
    public Optional<Expense> findById(Long expenseId) {
        String sql = "SELECT e.expense_id, e.group_id, e.payer_membership_id, e.created_by_membership, " +
                    "e.amount, e.description, e.category, e.expense_date, e.last_modified_date, e.is_deleted, " +
                    "g.name as group_name, g.currency as group_currency, " +
                    "p.membership_id as payer_id, u.user_id as payer_user_id, u.email as payer_email, u.full_name as payer_name " +
                    "FROM expenses e " +
                    "JOIN groups g ON e.group_id = g.group_id " +
                    "JOIN memberships p ON e.payer_membership_id = p.membership_id " +
                    "JOIN users u ON p.user_id = u.user_id " +
                    "WHERE e.expense_id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, expenseId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Expense expense = mapResultSetToExpense(rs);
                    loadParticipants(expense);
                    return Optional.of(expense);
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DAOException("Error finding expense by ID", e);
        }
    }
    
    /**
     * Trova tutte le expense di un gruppo (escluse quelle eliminate).
     * 
     * CASO D'USO: UC7 - View Expense History
     * 
     * FILTRO: WHERE is_deleted = FALSE (soft delete)
     * ORDINAMENTO: ORDER BY expense_date DESC
     * 
     * @param groupId ID del gruppo
     * @return lista di Expense del gruppo
     * @throws DAOException in caso di errore SQL
     */
    public List<Expense> findByGroup(Long groupId) {
        String sql = "SELECT e.expense_id, e.group_id, e.payer_membership_id, e.created_by_membership, " +
                    "e.amount, e.description, e.category, e.expense_date, e.last_modified_date, e.is_deleted, " +
                    "g.name as group_name, g.currency as group_currency, " +
                    "p.membership_id as payer_id, u.user_id as payer_user_id, u.email as payer_email, u.full_name as payer_name " +
                    "FROM expenses e " +
                    "JOIN groups g ON e.group_id = g.group_id " +
                    "JOIN memberships p ON e.payer_membership_id = p.membership_id " +
                    "JOIN users u ON p.user_id = u.user_id " +
                    "WHERE e.group_id = ? AND e.is_deleted = FALSE " +
                    "ORDER BY e.expense_date DESC";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, groupId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                List<Expense> expenses = new ArrayList<>();
                while (rs.next()) {
                    Expense expense = mapResultSetToExpense(rs);
                    loadParticipants(expense);
                    expenses.add(expense);
                }
                return expenses;
            }
        } catch (SQLException e) {
            throw new DAOException("Error finding expenses by group", e);
        }
    }
    
    /**
     * Trova tutti i partecipanti di un'expense.
     * 
     * @param expenseId ID dell'expense
     * @return lista di ExpenseParticipant
     * @throws DAOException in caso di errore SQL
     */
    public List<ExpenseParticipant> findParticipantsByExpenseId(Long expenseId) {
        // Prima carica l'Expense
        Optional<Expense> expenseOpt = findById(expenseId);
        if (expenseOpt.isEmpty()) {
            return Collections.emptyList();
        }
        
        Expense expense = expenseOpt.get();
        
        String sql = "SELECT ep.participant_id, ep.expense_id, ep.beneficiary_membership_id, ep.share_amount, " +
                    "m.user_id, u.full_name, u.email " +
                    "FROM expense_participants ep " +
                    "JOIN memberships m ON ep.beneficiary_membership_id = m.membership_id " +
                    "JOIN users u ON m.user_id = u.user_id " +
                    "WHERE ep.expense_id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, expenseId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                List<ExpenseParticipant> participants = new ArrayList<>();
                while (rs.next()) {
                    participants.add(mapResultSetToExpenseParticipant(rs, expense));
                }
                return participants;
            }
        } catch (SQLException e) {
            throw new DAOException("Error finding expense participants", e);
        }
    }
    
    /**
     * Mappa un ResultSet su un oggetto Expense di dominio.
     * 
     * RESPONSABILITÀ:
     * - Ricostruisce Group (semplificato con solo dati base)
     * - Ricostruisce Payer (Membership + User)
     * - Converte Category da String a Enum
     * - Converte Timestamp a LocalDateTime
     * 
     * @param rs ResultSet posizionato su una riga valida
     * @return oggetto Expense popolato
     * @throws SQLException se errore nel leggere ResultSet
     */
    private Expense mapResultSetToExpense(ResultSet rs) throws SQLException {
        Group group = new Group(
            rs.getLong("group_id"),
            rs.getString("group_name"),
            rs.getString("group_currency")
        );
        
        // Crea User semplificato per payer
        User payerUser = new User(
            rs.getLong("payer_user_id"),
            rs.getString("payer_email"),
            rs.getString("payer_name"),
            "" // Password hash non necessaria
        );
        
        Membership payer = new Membership(
            rs.getLong("payer_id"),
            payerUser,
            group,
            Role.MEMBER // assumiamo MEMBER, ma potrebbe essere ADMIN
        );
        
        Expense expense = new Expense(
            rs.getLong("expense_id"),
            group,
            payer,
            payer, // createdBy = payer
            rs.getBigDecimal("amount"),
            rs.getString("description"),
            Category.valueOf(rs.getString("category")),
            rs.getTimestamp("expense_date").toLocalDateTime()
        );
        
        // Imposta last_modified_date
        expense.setLastModifiedDate(rs.getTimestamp("last_modified_date").toLocalDateTime());
        
        return expense;
    }
    
    /**
     * Mappa un ResultSet su un oggetto ExpenseParticipant.
     * 
     * @param rs ResultSet posizionato su una riga valida
     * @param expense oggetto Expense di riferimento
     * @return oggetto ExpenseParticipant popolato
     * @throws SQLException se errore nel leggere ResultSet
     */
    private ExpenseParticipant mapResultSetToExpenseParticipant(ResultSet rs, Expense expense) throws SQLException {
        // Crea User per il beneficiario
        User beneficiaryUser = new User(
            rs.getLong("user_id"),
            rs.getString("email"),
            rs.getString("full_name"),
            "" // Password hash non necessaria
        );
        
        // Crea Membership per il beneficiario
        Membership beneficiary = new Membership(
            rs.getLong("beneficiary_membership_id"),
            beneficiaryUser,
            expense.getGroup(),
            Role.MEMBER
        );
        
        return new ExpenseParticipant(
            rs.getLong("participant_id"),
            expense,
            beneficiary,
            rs.getBigDecimal("share_amount")
        );
    }
    
    /**
     * Carica i partecipanti di un'expense e li aggiunge all'oggetto.
     * 
     * SIDE EFFECT: Modifica l'oggetto Expense passato aggiungendo i partecipanti.
     * 
     * PATTERN: Lazy Loading helper method
     * 
     * @param expense oggetto Expense da popolare
     * @throws DAOException in caso di errore SQL
     */
    private void loadParticipants(Expense expense) {
        String sql = "SELECT ep.participant_id, ep.beneficiary_membership_id, ep.share_amount, " +
                    "m.user_id, u.full_name, u.email, g.group_id, g.name as group_name, g.currency " +
                    "FROM expense_participants ep " +
                    "JOIN memberships m ON ep.beneficiary_membership_id = m.membership_id " +
                    "JOIN users u ON m.user_id = u.user_id " +
                    "JOIN groups g ON m.group_id = g.group_id " +
                    "WHERE ep.expense_id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, expense.getExpenseId());
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ExpenseParticipant participant = mapResultSetToExpenseParticipant(rs, expense);
                    expense.addParticipant(participant);
                }
            }
        } catch (SQLException e) {
            throw new DAOException("Error loading expense participants", e);
        }
    }
}