// language: java
package com.splitmanager.service;

import com.splitmanager.dao.ConnectionManager;
import com.splitmanager.dao.ExpenseDAO;
import com.splitmanager.dao.MembershipDAO;
import com.splitmanager.dao.BalanceDAO;
import com.splitmanager.dao.GroupDAO;
import com.splitmanager.domain.accounting.*;
import com.splitmanager.domain.registry.Group;
import com.splitmanager.domain.registry.Membership;
import com.splitmanager.exception.DAOException;
import com.splitmanager.exception.DomainException;
import com.splitmanager.exception.EntityNotFoundException;
import com.splitmanager.exception.UnauthorizedException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service per la gestione delle spese condivise.
 *
 * RESPONSABILITÀ CHIAVE:
 * - Coordinamento transazioni (beginTransaction, commit, rollback)
 * - WIRING del pattern Observer (attach() dei Membership all'Expense)
 * - Aggiornamento Balance conseguente agli eventi di dominio
 * - Validazione business logic
 *
 * NOTA SUL WIRING (come da diagrammi UML):
 * Gli Observer (Membership) non sono persistiti nel DB (campo transient in Subject).
 * È responsabilità del Service:
 * 1. Caricare Subject (Expense) dal DB
 * 2. Caricare Observer (Membership) dal DB
 * 3. Collegare con attach()
 * 4. Eseguire operazioni che scatenano notifyObservers()
 * 5. Aggiornare Balance e persistere tramite BalanceDAO
 *
 * Riferimenti Use Case:
 * - UC5: Add New Expense
 * - UC7: View Expense History
 * - UC11: Edit/Delete Expense
 */
public class ExpenseService {

    private final ExpenseDAO expenseDAO;
    private final MembershipDAO membershipDAO;
    private final BalanceDAO balanceDAO;
    private final GroupDAO groupDAO;

    public ExpenseService() {
        this.expenseDAO = new ExpenseDAO();
        this.membershipDAO = new MembershipDAO();
        this.balanceDAO = new BalanceDAO();
        this.groupDAO = new GroupDAO();
    }

    // Costruttore per dependency injection nei test
    public ExpenseService(ExpenseDAO expenseDAO,
                          MembershipDAO membershipDAO,
                          BalanceDAO balanceDAO,
                          GroupDAO groupDAO) {
        this.expenseDAO = expenseDAO;
        this.membershipDAO = membershipDAO;
        this.balanceDAO = balanceDAO;
        this.groupDAO = groupDAO;
    }

    /**
     * UC5 - Add New Expense
     *
     * Crea una nuova spesa e aggiorna automaticamente i saldi dei partecipanti.
     *
     * FLUSSO (con wiring Observer):
     * 1. Validazione input e caricamento entità
     * 2. Creazione Expense nel DB tramite ExpenseDAO
     * 3. WIRING: carica tutti i Membership del gruppo e collega con attach()
     * 4. Calcolo e distribuzione quote
     * 5. Aggiornamento Balance per ogni partecipante
     * 6. Commit transazione
     *
     * @param groupId ID del gruppo
     * @param payerMembershipId ID del membro che ha pagato
     * @param amount importo totale della spesa
     * @param description descrizione della spesa
     * @param category categoria (FOOD, TRANSPORT, etc.)
     * @param participantIds lista di ID dei membri che partecipano alla spesa
     * @return Expense creata con ID generato
     * @throws EntityNotFoundException se gruppo/membri non trovati
     * @throws DomainException se validazioni business falliscono (UC5 Alternative 4a, 5a)
     */
    public Expense addExpense(Long groupId,
                              Long payerMembershipId,
                              BigDecimal amount,
                              String description,
                              Category category,
                              List<Long> participantIds) {

        ConnectionManager connMgr = ConnectionManager.getInstance();

        try {
            connMgr.beginTransaction();

            // ==========================================
            // FASE 1: VALIDAZIONE E CARICAMENTO ENTITÀ
            // ==========================================

            // UC5 Alternative 4a: validazione importo
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new DomainException("Amount must be positive");
            }

            // UC5 Alternative 5a: validazione campi obbligatori
            if (description == null || description.trim().isEmpty()) {
                throw new DomainException("Description is required");
            }

            if (category == null) {
                throw new DomainException("Category is required");
            }

            if (participantIds == null || participantIds.isEmpty()) {
                throw new DomainException("Expense must have at least one participant");
            }

            // Carica e valida gruppo
            Group group = groupDAO.findById(groupId)
                    .orElseThrow(() -> new EntityNotFoundException("Group", groupId));

            if (!group.isActive()) {
                throw new DomainException("Cannot add expenses to an inactive group");
            }

            // Carica e valida payer
            Membership payer = membershipDAO.findById(payerMembershipId)
                    .orElseThrow(() -> new EntityNotFoundException("Membership", payerMembershipId));

            if (!payer.isActive()) {
                throw new DomainException("Only active members can record expenses");
            }

            if (!payer.getGroup().getGroupId().equals(groupId)) {
                throw new DomainException("Payer must be a member of the group");
            }

            // Carica e valida partecipanti
            List<Membership> participants = membershipDAO.findAllById(participantIds);

            if (participants.size() != participantIds.size()) {
                throw new DomainException("Some participants were not found");
            }

            // Verifica che tutti i partecipanti siano membri attivi del gruppo
            for (Membership participant : participants) {
                if (!participant.getGroup().getGroupId().equals(groupId)) {
                    throw new DomainException(
                            "All participants must be members of the group"
                    );
                }
                if (!participant.isActive()) {
                    throw new DomainException(
                            "All participants must be active members"
                    );
                }
            }

            // ==========================================
            // FASE 2: CREAZIONE EXPENSE NEL DB
            // ==========================================

            // Il DAO crea l'Expense + ExpenseParticipant nel DB
            // e restituisce l'oggetto Expense con ID generato
            Expense expense = expenseDAO.create(
                    groupId,
                    payerMembershipId,
                    amount,
                    description,
                    category,
                    participantIds
            );

            // ==========================================
            // FASE 3: WIRING DEL PATTERN OBSERVER
            // ==========================================
            // QUESTO È IL CUORE DEL PATTERN OBSERVER!
            // Gli Observer non sono persistiti, quindi il Service
            // deve collegarli manualmente dopo ogni load dal DB

            // Carica TUTTI i membri del gruppo (non solo i partecipanti)
            // perché tutti devono essere notificati degli eventi
            List<Membership> allMembers = membershipDAO.findByGroup(groupId);

            // Collega ogni Membership come Observer dell'Expense
            for (Membership member : allMembers) {
                expense.attach(member); // ← WIRING!
            }

            // ==========================================
            // FASE 4: CALCOLO E DISTRIBUZIONE QUOTE
            // ==========================================

            // Calcola la quota per partecipante (divisione equa)
            BigDecimal shareAmount = amount.divide(
                    new BigDecimal(participantIds.size()),
                    2, // 2 decimali
                    RoundingMode.HALF_UP
            );

            // Per ogni partecipante, crea ExpenseParticipant e aggiorna Balance
            for (Membership participant : participants) {

                // Crea ExpenseParticipant
                ExpenseParticipant expenseParticipant = new ExpenseParticipant(
                        expense,
                        participant,
                        shareAmount
                );

                // Aggiunge all'expense
                // IMPORTANTE: questo scatena notifyObservers()!
                // Gli Observer (Membership) ricevono l'evento EXPENSE_CREATED
                expense.addParticipant(expenseParticipant);

                // Salva ExpenseParticipant nel DB
                expenseDAO.saveParticipant(expenseParticipant);
            }

            // ==========================================
            // FASE 5: AGGIORNAMENTO BALANCE
            // ==========================================
            // Il Service aggiorna i Balance in base alla logica di business:
            // - Il PAYER riceve CREDITO: (amount - sua_quota)
            // - Gli altri vanno in DEBITO: -shareAmount

            for (Membership participant : participants) {

                // Carica il Balance (dovrebbe già esistere, creato in GroupService)
                Balance balance = participant.getBalance();

                if (balance == null) {
                    // Fallback: crea Balance se non esiste
                    balance = new Balance(participant);
                    balance = balanceDAO.save(balance);
                    participant.setBalance(balance);
                }

                if (participant.getMembershipId().equals(payerMembershipId)) {
                    // Il pagante riceve credito
                    // Ha pagato 'amount' ma deve solo 'shareAmount'
                    // Quindi riceve: amount - shareAmount
                    BigDecimal credit = amount.subtract(shareAmount);

                    if (credit.compareTo(BigDecimal.ZERO) > 0) {
                        balance.increment(credit);
                    }
                    // Se credit == 0 significa che ha pagato solo per sé

                } else {
                    // Gli altri partecipanti vanno in debito
                    balance.decrement(shareAmount);
                }

                // Aggiorna Balance nel DB
                balanceDAO.update(balance);
            }

            // ==========================================
            // FASE 6: COMMIT TRANSAZIONE
            // ==========================================

            connMgr.commit();
            return expense;

        } catch (Exception e) {
            // Rollback in caso di errore
            try {
                connMgr.rollback();
            } catch (SQLException ex) {
                throw new DAOException("Error during transaction rollback", ex);
            }

            // Rilancia l'eccezione originale
            if (e instanceof DomainException) {
                throw (DomainException) e;
            }
            if (e instanceof EntityNotFoundException) {
                throw (EntityNotFoundException) e;
            }

            throw new DomainException(
                    "Error creating expense: " + e.getMessage(), e
            );
        }
    }

    /**
     * UC11 - Edit Expense
     *
     * Modifica i dettagli di una spesa esistente.
     * Solo il creatore o un admin possono modificarla.
     *
     * NOTA: Se si modificano i partecipanti, bisogna ricalcolare tutti i Balance.
     * Per semplicità, questa versione modifica solo amount/description/category.
     *
     * @param expenseId ID della spesa da modificare
     * @param editorMembershipId ID del membro che modifica
     * @param newAmount nuovo importo (opzionale)
     * @param newDescription nuova descrizione (opzionale)
     * @param newCategory nuova categoria (opzionale)
     * @throws EntityNotFoundException se spesa non trovata
     * @throws UnauthorizedException se non autorizzato (UC11 Alternative 2a)
     */
    public void editExpense(Long expenseId,
                            Long editorMembershipId,
                            BigDecimal newAmount,
                            String newDescription,
                            Category newCategory) {

        ConnectionManager connMgr = ConnectionManager.getInstance();

        try {
            connMgr.beginTransaction();

            // Carica expense
            Expense expense = expenseDAO.findById(expenseId)
                    .orElseThrow(() -> new EntityNotFoundException("Expense", expenseId));

            if (expense.isDeleted()) {
                throw new DomainException("Cannot modify a deleted expense");
            }

            // Carica editor
            Membership editor = membershipDAO.findById(editorMembershipId)
                    .orElseThrow(() -> new EntityNotFoundException("Membership", editorMembershipId));

            // WIRING: collega observer prima di modificare
            List<Membership> allMembers = membershipDAO.findByGroup(
                    expense.getGroup().getGroupId()
            );
            for (Membership member : allMembers) {
                expense.attach(member);
            }

            // Salva importo vecchio per ricalcolare balance
            BigDecimal oldAmount = expense.getAmount();

            // Modifica (verifica autorizzazioni all'interno)
            expense.modifyDetails(newAmount, newDescription, newCategory, editor);

            // Se l'importo è cambiato, ricalcola i balance
            if (newAmount != null && newAmount.compareTo(oldAmount) != 0) {
                recalculateBalancesForExpense(expense, oldAmount, newAmount);
            }

            // Aggiorna expense nel DB
            expenseDAO.update(expense);

            connMgr.commit();

        } catch (Exception e) {
            try {
                connMgr.rollback();
            } catch (SQLException ex) {
                throw new DAOException("Error during rollback", ex);
            }

            if (e instanceof UnauthorizedException) {
                throw (UnauthorizedException) e;
            }
            if (e instanceof DomainException) {
                throw (DomainException) e;
            }
            throw new DomainException("Error editing expense", e);
        }
    }

    /**
     * UC11 - Delete Expense
     *
     * Elimina (soft delete) una spesa.
     * Solo il creatore o un admin possono eliminarla.
     *
     * @param expenseId ID della spesa da eliminare
     * @param deleterMembershipId ID del membro che elimina
     * @throws EntityNotFoundException se spesa non trovata
     * @throws UnauthorizedException se non autorizzato
     */
    public void deleteExpense(Long expenseId, Long deleterMembershipId) {

        ConnectionManager connMgr = ConnectionManager.getInstance();

        try {
            connMgr.beginTransaction();

            // Carica expense
            Expense expense = expenseDAO.findById(expenseId)
                    .orElseThrow(() -> new EntityNotFoundException("Expense", expenseId));

            // Carica deleter
            Membership deleter = membershipDAO.findById(deleterMembershipId)
                    .orElseThrow(() -> new EntityNotFoundException("Membership", deleterMembershipId));

            // WIRING
            List<Membership> allMembers = membershipDAO.findByGroup(
                    expense.getGroup().getGroupId()
            );
            for (Membership member : allMembers) {
                expense.attach(member);
            }

            // Prima di eliminare, annulla l'impatto sui balance
            reverseBalancesForExpense(expense);

            // Elimina (soft delete, scatena evento)
            expense.markAsDeleted(deleter);

            // Aggiorna nel DB
            expenseDAO.update(expense);

            connMgr.commit();

        } catch (Exception e) {
            try {
                connMgr.rollback();
            } catch (SQLException ex) {
                throw new DAOException("Error during rollback", ex);
            }

            if (e instanceof UnauthorizedException) {
                throw (UnauthorizedException) e;
            }
            if (e instanceof DomainException) {
                throw (DomainException) e;
            }
            throw new DomainException("Error deleting expense", e);
        }
    }

    /**
     * UC7 - View Expense History
     *
     * Restituisce la cronologia delle spese di un gruppo.
     *
     * @param groupId ID del gruppo
     * @return lista di Expense (escluse quelle eliminate)
     */
    public List<Expense> getHistory(Long groupId) {
        // Verifica che il gruppo esista
        groupDAO.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group", groupId));

        // Carica spese (il DAO filtra già quelle eliminate)
        return expenseDAO.findByGroup(groupId);
    }

    /**
     * Carica una spesa specifica con il wiring completo.
     * Utile per operazioni che richiedono l'expense con observer collegati.
     *
     * @param expenseId ID della spesa
     * @return Expense con observer collegati
     */
    public Expense getExpenseWithObservers(Long expenseId) {
        Expense expense = expenseDAO.findById(expenseId)
                .orElseThrow(() -> new EntityNotFoundException("Expense", expenseId));

        // WIRING
        List<Membership> allMembers = membershipDAO.findByGroup(
                expense.getGroup().getGroupId()
        );
        for (Membership member : allMembers) {
            expense.attach(member);
        }

        return expense;
    }

    // ==========================================
    // METODI PRIVATI DI SUPPORTO
    // ==========================================

    /**
     * Ricalcola i balance quando l'importo di una spesa viene modificato.
     */
    private void recalculateBalancesForExpense(Expense expense,
                                               BigDecimal oldAmount,
                                               BigDecimal newAmount) {

        List<ExpenseParticipant> participants = expense.getParticipantDetails();
        int participantCount = participants.size();

        if (participantCount == 0) {
            return; // Nessun partecipante, niente da ricalcolare
        }

        // Calcola vecchia e nuova quota
        BigDecimal oldShare = oldAmount.divide(
                new BigDecimal(participantCount), 2, RoundingMode.HALF_UP
        );
        BigDecimal newShare = newAmount.divide(
                new BigDecimal(participantCount), 2, RoundingMode.HALF_UP
        );

        BigDecimal delta = newShare.subtract(oldShare);

        // Aggiorna i balance
        for (ExpenseParticipant ep : participants) {
            Membership participant = ep.getBeneficiary();
            Balance balance = participant.getBalance();

            if (balance == null) {
                continue; // Skip se non ha balance (non dovrebbe accadere)
            }

            if (participant.getMembershipId().equals(expense.getPayer().getMembershipId())) {
                // Il payer: se la spesa aumenta, riceve meno credito (o più debito)
                // Se la spesa diminuisce, riceve più credito
                BigDecimal oldCredit = oldAmount.subtract(oldShare);
                BigDecimal newCredit = newAmount.subtract(newShare);
                BigDecimal creditDelta = newCredit.subtract(oldCredit);

                balance.apply(creditDelta);

            } else {
                // Altri partecipanti: se la spesa aumenta, hanno più debito
                balance.apply(delta.negate()); // Negativo perché è debito
            }

            balanceDAO.update(balance);
        }
    }

    /**
     * Annulla l'impatto di una spesa sui balance (per delete).
     */
    private void reverseBalancesForExpense(Expense expense) {
        List<ExpenseParticipant> participants = expense.getParticipantDetails();

        for (ExpenseParticipant ep : participants) {
            Membership participant = ep.getBeneficiary();
            Balance balance = participant.getBalance();

            if (balance == null) {
                continue;
            }

            BigDecimal shareAmount = ep.getShareAmount();

            if (participant.getMembershipId().equals(expense.getPayer().getMembershipId())) {
                // Il payer: rimuovi il credito che aveva ricevuto
                BigDecimal credit = expense.getAmount().subtract(shareAmount);
                if (credit.compareTo(BigDecimal.ZERO) > 0) {
                    balance.decrement(credit); // Togli il credito
                }
            } else {
                // Altri: rimuovi il debito
                balance.increment(shareAmount); // Riduci il debito
            }

            balanceDAO.update(balance);
        }
    }
}
