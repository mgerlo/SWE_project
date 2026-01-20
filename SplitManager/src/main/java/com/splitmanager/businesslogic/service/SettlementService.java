package com.splitmanager.businesslogic.service;

import com.splitmanager.dao.ConnectionManager;
import com.splitmanager.dao.SettlementDAO;
import com.splitmanager.dao.MembershipDAO;
import com.splitmanager.dao.BalanceDAO;
import com.splitmanager.dao.GroupDAO;
import com.splitmanager.domain.accounting.Balance;
import com.splitmanager.domain.accounting.PaymentStatus;
import com.splitmanager.domain.accounting.Settlement;
import com.splitmanager.domain.registry.Group;
import com.splitmanager.domain.registry.Membership;
import com.splitmanager.exception.DAOException;
import com.splitmanager.exception.DomainException;
import com.splitmanager.exception.EntityNotFoundException;
import com.splitmanager.exception.UnauthorizedException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

/**
 * Service per la gestione dei rimborsi (settlements) tra membri.
 *
 * RESPONSABILITÀ:
 * - Creazione settlement per saldare debiti (UC8: Settle Debt)
 * - Conferma/annullamento settlement
 * - WIRING del pattern Observer (Settlement è Subject, Membership è Observer)
 * - Aggiornamento Balance quando un settlement viene confermato
 *
 * NOTA SUL WIRING:
 * Anche Settlement è un Subject, quindi necessita del wiring con i Membership Observer.
 *
 * Riferimenti Use Case:
 * - UC8: Settle Debt with a Member
 */
public class SettlementService {

    private final SettlementDAO settlementDAO;
    private final MembershipDAO membershipDAO;
    private final BalanceDAO balanceDAO;
    private final GroupDAO groupDAO;

    public SettlementService() {
        this.settlementDAO = new SettlementDAO();
        this.membershipDAO = new MembershipDAO();
        this.balanceDAO = new BalanceDAO();
        this.groupDAO = new GroupDAO();
    }

    // Costruttore per dependency injection nei test
    public SettlementService(SettlementDAO settlementDAO,
                             MembershipDAO membershipDAO,
                             BalanceDAO balanceDAO,
                             GroupDAO groupDAO) {
        this.settlementDAO = settlementDAO;
        this.membershipDAO = membershipDAO;
        this.balanceDAO = balanceDAO;
        this.groupDAO = groupDAO;
    }

    /**
     * UC8 - Settle Debt
     *
     * Crea un settlement per saldare un debito tra due membri.
     * Il settlement parte in stato PENDING e deve essere confermato dal receiver.
     *
     * FLUSSO:
     * 1. Validazione: verifica che payer abbia effettivamente un debito verso receiver
     * 2. Creazione Settlement nel DB (stato PENDING)
     * 3. WIRING: collega i Membership come Observer del Settlement
     * 4. I Balance NON vengono aggiornati finché il settlement non è confermato
     *
     * @param groupId ID del gruppo
     * @param payerMembershipId ID del membro che paga (debitore)
     * @param receiverMembershipId ID del membro che riceve (creditore)
     * @param amount importo del rimborso
     * @return Settlement creato in stato PENDING
     * @throws DomainException se amount non valido o maggiore del debito (UC8 Alternative 4a)
     * @throws EntityNotFoundException se membri non trovati
     */
    public Settlement createSettlement(Long groupId,
                                       Long payerMembershipId,
                                       Long receiverMembershipId,
                                       BigDecimal amount) {

        ConnectionManager connMgr = ConnectionManager.getInstance();

        try {
            connMgr.beginTransaction();

            // ==========================================
            // FASE 1: VALIDAZIONE
            // ==========================================

            // UC8 Alternative 4a: validazione importo
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new DomainException("Settlement amount must be positive");
            }

            // Carica gruppo
            Group group = groupDAO.findById(groupId)
                    .orElseThrow(() -> new EntityNotFoundException("Group", groupId));

            if (!group.isActive()) {
                throw new DomainException("Cannot create settlement in an inactive group");
            }

            // Carica payer (debitore)
            Membership payer = membershipDAO.findById(payerMembershipId)
                    .orElseThrow(() -> new EntityNotFoundException("Membership", payerMembershipId));

            if (!payer.isActive()) {
                throw new DomainException("Only active members can create settlements");
            }

            if (!payer.getGroup().getGroupId().equals(groupId)) {
                throw new DomainException("Payer must be a member of the group");
            }

            // Carica receiver (creditore)
            Membership receiver = membershipDAO.findById(receiverMembershipId)
                    .orElseThrow(() -> new EntityNotFoundException("Membership", receiverMembershipId));

            if (!receiver.isActive()) {
                throw new DomainException("Receiver must be an active member");
            }

            if (!receiver.getGroup().getGroupId().equals(groupId)) {
                throw new DomainException("Receiver must be a member of the group");
            }

            // Verifica che payer e receiver siano diversi
            if (payerMembershipId.equals(receiverMembershipId)) {
                throw new DomainException("You cannot create a settlement to yourself");
            }

            // Verifica che il payer abbia effettivamente un debito
            Balance payerBalance = payer.getBalance();
            if (payerBalance == null || payerBalance.getAmount().compareTo(BigDecimal.ZERO) >= 0) {
                throw new DomainException("Payer has no debts to settle");
            }

            // UC8 Alternative 4a: verifica che l'importo non superi il debito
            BigDecimal debtAmount = payerBalance.getAmount().abs(); // Valore assoluto del debito
            if (amount.compareTo(debtAmount) > 0) {
                throw new DomainException(
                        String.format("Amount (%s) exceeds actual debt (%s)",
                                amount, debtAmount)
                );
            }

            // ==========================================
            // FASE 2: CREAZIONE SETTLEMENT
            // ==========================================

            Settlement settlement = settlementDAO.create(
                    groupId,
                    payerMembershipId,
                    receiverMembershipId,
                    amount
            );

            // ==========================================
            // FASE 3: WIRING DEL PATTERN OBSERVER
            // ==========================================
            // Settlement è un Subject, quindi necessita del wiring

            List<Membership> allMembers = membershipDAO.findByGroup(groupId);
            for (Membership member : allMembers) {
                settlement.attach(member); // ← WIRING!
            }

            // NOTA: I Balance NON vengono aggiornati qui.
            // Vengono aggiornati solo quando il settlement viene confermato
            // tramite confirmSettlement()

            connMgr.commit();
            return settlement;

        } catch (Exception e) {
            try {
                connMgr.rollback();
            } catch (SQLException ex) {
                throw new DAOException("Error during transaction rollback", ex);
            }

            if (e instanceof DomainException) {
                throw (DomainException) e;
            }
            if (e instanceof EntityNotFoundException) {
                throw (EntityNotFoundException) e;
            }

            throw new DomainException(
                    "Error creating settlement: " + e.getMessage(), e
            );
        }
    }

    /**
     * UC8 - Confirm Settlement
     *
     * Il receiver conferma di aver ricevuto il pagamento.
     * Solo il receiver può confermare un settlement.
     *
     * Quando confermato:
     * - Il settlement passa da PENDING a COMPLETED
     * - I Balance di payer e receiver vengono aggiornati
     * - Viene scatenato l'evento SETTLEMENT_CONFIRMED
     *
     * @param settlementId ID del settlement da confermare
     * @param confirmerMembershipId ID del membro che conferma (deve essere il receiver)
     * @throws EntityNotFoundException se settlement non trovato
     * @throws UnauthorizedException se non è il receiver a confermare
     * @throws DomainException se il settlement non è in stato PENDING
     */
    public void confirmSettlement(Long settlementId, Long confirmerMembershipId) {

        ConnectionManager connMgr = ConnectionManager.getInstance();

        try {
            connMgr.beginTransaction();

            // Carica settlement
            Settlement settlement = settlementDAO.findById(settlementId)
                    .orElseThrow(() -> new EntityNotFoundException("Settlement", settlementId));

            // Carica confirmer
            Membership confirmer = membershipDAO.findById(confirmerMembershipId)
                    .orElseThrow(() -> new EntityNotFoundException("Membership", confirmerMembershipId));

            // WIRING: collega observer prima di confermare
            List<Membership> allMembers = membershipDAO.findByGroup(
                    settlement.getGroup().getGroupId()
            );
            for (Membership member : allMembers) {
                settlement.attach(member);
            }

            // Conferma (verifica autorizzazioni all'interno)
            // Questo scatena notifyObservers() con evento SETTLEMENT_CONFIRMED
            settlement.executeConfirmation(confirmer);

            // ==========================================
            // AGGIORNAMENTO BALANCE
            // ==========================================
            // Ora che il settlement è confermato, aggiorniamo i balance

            Membership payer = settlement.getPayer();
            Membership receiver = settlement.getReceiver();
            BigDecimal amount = settlement.getAmount();

            Balance payerBalance = payer.getBalance();
            Balance receiverBalance = receiver.getBalance();

            if (payerBalance == null || receiverBalance == null) {
                throw new DomainException("Balance not found for members");
            }

            // Il payer riduce il suo debito (incrementa perché è negativo)
            payerBalance.increment(amount);

            // Il receiver riduce il suo credito (decrementa perché è positivo)
            receiverBalance.decrement(amount);

            // Salva i balance aggiornati
            balanceDAO.update(payerBalance);
            balanceDAO.update(receiverBalance);

            // Aggiorna settlement nel DB
            settlementDAO.update(settlement);

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
            if (e instanceof EntityNotFoundException) {
                throw (EntityNotFoundException) e;
            }

            throw new DomainException("Error confirming settlement", e);
        }
    }

    /**
     * Annulla un settlement.
     *
     * Può essere annullato da:
     * - Il payer
     * - Il receiver
     * - Un admin del gruppo
     *
     * Solo i settlement in stato PENDING possono essere annullati.
     *
     * @param settlementId ID del settlement da annullare
     * @param cancellerMembershipId ID del membro che annulla
     * @throws EntityNotFoundException se settlement non trovato
     * @throws UnauthorizedException se non autorizzato
     * @throws DomainException se il settlement è già completato
     */
    public void cancelSettlement(Long settlementId, Long cancellerMembershipId) {

        ConnectionManager connMgr = ConnectionManager.getInstance();

        try {
            connMgr.beginTransaction();

            // Carica settlement
            Settlement settlement = settlementDAO.findById(settlementId)
                    .orElseThrow(() -> new EntityNotFoundException("Settlement", settlementId));

            // Carica canceller
            Membership canceller = membershipDAO.findById(cancellerMembershipId)
                    .orElseThrow(() -> new EntityNotFoundException("Membership", cancellerMembershipId));

            // WIRING
            List<Membership> allMembers = membershipDAO.findByGroup(
                    settlement.getGroup().getGroupId()
            );
            for (Membership member : allMembers) {
                settlement.attach(member);
            }

            // Annulla (verifica autorizzazioni all'interno)
            settlement.executeCancellation(canceller);

            // Aggiorna nel DB
            settlementDAO.update(settlement);

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

            throw new DomainException("Error cancelling settlement", e);
        }
    }

    /**
     * Ottiene tutti i settlement di un gruppo.
     *
     * @param groupId ID del gruppo
     * @return lista di Settlement
     */
    public List<Settlement> getGroupSettlements(Long groupId) {
        // Verifica che il gruppo esista
        groupDAO.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group", groupId));

        return settlementDAO.findByGroup(groupId);
    }

    /**
     * Ottiene tutti i settlement PENDING di un gruppo.
     * Utile per mostrare i pagamenti in attesa di conferma.
     *
     * @param groupId ID del gruppo
     * @return lista di Settlement in stato PENDING
     */
    public List<Settlement> getPendingSettlements(Long groupId) {
        // Verifica che il gruppo esista
        groupDAO.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group", groupId));

        List<Settlement> allSettlements = settlementDAO.findByGroup(groupId);

        // Filtra solo quelli PENDING
        return allSettlements.stream()
                .filter(Settlement::isPending)
                .toList();
    }

    /**
     * Ottiene tutti i settlement confermati (COMPLETED) di un gruppo.
     * Utile per la cronologia dei pagamenti.
     *
     * @param groupId ID del gruppo
     * @return lista di Settlement in stato COMPLETED
     */
    public List<Settlement> getCompletedSettlements(Long groupId) {
        groupDAO.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group", groupId));

        List<Settlement> allSettlements = settlementDAO.findByGroup(groupId);

        return allSettlements.stream()
                .filter(s -> s.getStatus() == PaymentStatus.COMPLETED)
                .toList();
    }

    /**
     * Carica un settlement specifico con il wiring completo.
     * Utile per operazioni che richiedono il settlement con observer collegati.
     *
     * @param settlementId ID del settlement
     * @return Settlement con observer collegati
     */
    public Settlement getSettlementWithObservers(Long settlementId) {
        Settlement settlement = settlementDAO.findById(settlementId)
                .orElseThrow(() -> new EntityNotFoundException("Settlement", settlementId));

        // WIRING
        List<Membership> allMembers = membershipDAO.findByGroup(
                settlement.getGroup().getGroupId()
        );
        for (Membership member : allMembers) {
            settlement.attach(member);
        }

        return settlement;
    }

    /**
     * Verifica se un settlement è confermabile da un determinato membro.
     * Utile per la UI per abilitare/disabilitare il pulsante "Conferma".
     *
     * @param settlementId ID del settlement
     * @param membershipId ID del membro
     * @return true se il membro può confermare il settlement
     */
    public boolean canConfirm(Long settlementId, Long membershipId) {
        Settlement settlement = settlementDAO.findById(settlementId)
                .orElseThrow(() -> new EntityNotFoundException("Settlement", settlementId));

        Membership member = membershipDAO.findById(membershipId)
                .orElseThrow(() -> new EntityNotFoundException("Membership", membershipId));

        return settlement.isPending() && settlement.canBeConfirmedBy(member);
    }
}
