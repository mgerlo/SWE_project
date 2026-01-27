package com.splitmanager.businesslogic.service;

import com.splitmanager.dao.BalanceDAO;
import com.splitmanager.dao.GroupDAO;
import com.splitmanager.dao.MembershipDAO;
import com.splitmanager.domain.accounting.Balance;
import com.splitmanager.domain.accounting.Settlement;
import com.splitmanager.domain.registry.Group;
import com.splitmanager.domain.registry.Membership;
import com.splitmanager.exception.EntityNotFoundException;
import com.splitmanager.businesslogic.service.BalanceStrategy;
import com.splitmanager.businesslogic.service.MinTransactionsStrategy;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service per la gestione dei saldi e l'ottimizzazione dei debiti.
 *
 * RESPONSABILITÀ:
 * - Calcolare i saldi netti dei membri del gruppo (UC6: Visualizza saldi del gruppo)
 * - Ottimizzare i rimborsi usando il Pattern Strategy
 * - Fornire una panoramica dei saldi del gruppo
 *
 * PATTERN STRATEGY:
 * Usa BalanceStrategy per permettere diversi algoritmi di ottimizzazione.
 * Predefinito: MinTransactionsStrategy (algoritmo greedy per minimizzare il numero di transazioni)
 *
 * Riferimenti Use Case:
 * - UC6: Visualizza saldi del gruppo
 * - UC8: Saldare debiti (usa i debiti ottimizzati)
 */
public class BalanceService {

    private final BalanceDAO balanceDAO;
    private final MembershipDAO membershipDAO;
    private final GroupDAO groupDAO;
    private final BalanceStrategy strategy;

    /**
     * Costruttore di default.
     * Usa MinTransactionsStrategy come algoritmo di ottimizzazione predefinito.
     */
    public BalanceService() {
        this.balanceDAO = new BalanceDAO();
        this.membershipDAO = new MembershipDAO();
        this.groupDAO = new GroupDAO();
        this.strategy = new MinTransactionsStrategy();
    }

    /**
     * Costruttore con dependency injection (per i test).
     * Permette di iniettare una strategy personalizzata.
     */
    public BalanceService(BalanceDAO balanceDAO,
                          MembershipDAO membershipDAO,
                          GroupDAO groupDAO,
                          BalanceStrategy strategy) {
        this.balanceDAO = balanceDAO;
        this.membershipDAO = membershipDAO;
        this.groupDAO = groupDAO;
        this.strategy = strategy;
    }

    /**
     * UC6 - Visualizza saldi del gruppo
     *
     * Restituisce il saldo corrente per ogni membro del gruppo.
     *
     * Saldo positivo = credito (gli altri gli devono soldi)
     * Saldo negativo = debito (l'utente deve soldi agli altri)
     * Saldo zero = saldo chiuso (nessun debito)
     *
     * @param groupId ID del gruppo
     * @return Mappa da Membership al loro saldo netto
     * @throws EntityNotFoundException se il gruppo non viene trovato
     */
    /**
     * UC6 - Visualizza saldi del gruppo
     */
    public Map<Membership, BigDecimal> getGroupBalances(Long groupId) {
        // Verifica esistenza gruppo
        groupDAO.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group", groupId));

        Map<Membership, BigDecimal> activeBalances = balanceDAO.findByGroup(groupId);

        return activeBalances;
    }

    /**
     * UC6/UC8 - Ottieni debiti ottimizzati
     *
     * Calcola la lista ottimale di settlement per bilanciare tutti i conti
     * con il minimo numero di transazioni.
     *
     * Usa la BalanceStrategy configurata (predefinita: MinTransactionsStrategy).
     *
     * Esempio:
     * - Alice deve 50€ (saldo: -50)
     * - Bob deve 30€ (saldo: -30)
     * - Charlie ha credito 80€ (saldo: +80)
     *
     * Risultato ottimizzato:
     * - Alice paga 50€ a Charlie
     * - Bob paga 30€ a Charlie
     * Totale: 2 transazioni invece di potenzialmente di più
     *
     * @param groupId ID del gruppo
     * @return Lista di oggetti Settlement suggeriti (in stato PENDING)
     * @throws EntityNotFoundException se il gruppo non viene trovato
     */
    public List<Settlement> getOptimizedDebts(Long groupId) {
        // Ottieni i saldi correnti
        Map<Membership, BigDecimal> balances = getGroupBalances(groupId);

        // Applica la strategy di ottimizzazione
        return strategy.optimize(balances);
    }

    /**
     * Ottieni il saldo di un membro specifico.
     *
     * @param membershipId ID del membership
     * @return BigDecimal saldo (positivo = credito, negativo = debito, zero = saldo chiuso)
     * @throws EntityNotFoundException se il membership non viene trovato
     */
    public BigDecimal getMemberBalance(Long membershipId) {
        Membership member = membershipDAO.findById(membershipId)
                .orElseThrow(() -> new EntityNotFoundException("Membership", membershipId));

        Balance balance = member.getBalance();

        if (balance == null) {
            return BigDecimal.ZERO;
        }

        return balance.getAmount();
    }

    /**
     * Verifica se tutti i saldi in un gruppo sono saldati (tutti zero).
     * Utile per determinare se un gruppo può essere chiuso/archiviato.
     *
     * @param groupId ID del gruppo
     * @return true se tutti i membri hanno saldo zero
     */
    public boolean isGroupSettled(Long groupId) {
        Map<Membership, BigDecimal> balances = getGroupBalances(groupId);

        return balances.values().stream()
                .allMatch(balance -> balance.compareTo(BigDecimal.ZERO) == 0);
    }

    /**
     * Ottieni l'ammontare totale del debito nel gruppo.
     * Questo è la somma di tutti i saldi negativi (valore assoluto).
     *
     * @param groupId ID del gruppo
     * @return importo totale del debito
     */
    public BigDecimal getTotalGroupDebt(Long groupId) {
        Map<Membership, BigDecimal> balances = getGroupBalances(groupId);

        return balances.values().stream()
                .filter(balance -> balance.compareTo(BigDecimal.ZERO) < 0)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Ottieni l'ammontare totale del credito nel gruppo.
     * Questa è la somma di tutti i saldi positivi.
     *
     * Nota: il totale dei crediti dovrebbe essere uguale al totale dei debiti (sistema chiuso).
     *
     * @param groupId ID del gruppo
     * @return importo totale del credito
     */
    public BigDecimal getTotalGroupCredit(Long groupId) {
        Map<Membership, BigDecimal> balances = getGroupBalances(groupId);

        return balances.values().stream()
                .filter(balance -> balance.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Ottieni i membri che hanno debiti (saldo negativo).
     *
     * @param groupId ID del gruppo
     * @return Lista di Membership con saldo negativo
     */
    public List<Membership> getDebtors(Long groupId) {
        Map<Membership, BigDecimal> balances = getGroupBalances(groupId);

        return balances.entrySet().stream()
                .filter(entry -> entry.getValue().compareTo(BigDecimal.ZERO) < 0)
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * Ottieni i membri che hanno crediti (saldo positivo).
     *
     * @param groupId ID del gruppo
     * @return Lista di Membership con saldo positivo
     */
    public List<Membership> getCreditors(Long groupId) {
        Map<Membership, BigDecimal> balances = getGroupBalances(groupId);

        return balances.entrySet().stream()
                .filter(entry -> entry.getValue().compareTo(BigDecimal.ZERO) > 0)
                .map(Map.Entry::getKey)
                .toList();
    }
}
