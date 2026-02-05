package com.splitmanager.integration;

import com.splitmanager.businesslogic.service.*;
import com.splitmanager.dao.*;
import com.splitmanager.domain.accounting.Settlement;
import com.splitmanager.domain.accounting.PaymentStatus;
import com.splitmanager.domain.registry.Membership;
import com.splitmanager.exception.DomainException;
import com.splitmanager.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test di integrazione per BalanceService e SettlementService.
 * 
 * CASI D'USO TESTATI:
 * - UC6: View group balances - Visualizzazione saldi del gruppo
 * - UC8: Settle up debts - Rimborso debiti tra membri
 * 
 * SCENARIO DI TEST:
 * Un gruppo "Vacanza" con 3 membri:
 * - Alice (Admin): debito di 50€
 * - Bob (Member): debito di 30€  
 * - Charlie (Member): credito di 80€
 * 
 * OBIETTIVO:
 * Verificare che:
 * 1. I saldi siano calcolati correttamente
 * 2. Le transazioni ottimizzate minimizzino i trasferimenti
 * 3. I settlement aggiornino i balance correttamente
 * 4. Le validazioni business prevengano operazioni non valide
 */
public class BalanceServiceTest_UC6_UC8 extends BaseIntegrationTest {
    
    private BalanceService balanceService;
    private SettlementService settlementService;
    private SettlementDAO settlementDAO;
    
    // ID delle entità di test
    private Long groupId;
    private Long userId1, userId2, userId3;
    private Long membershipId1, membershipId2, membershipId3;
    
    /**
     * Setup del test: crea gruppo con 3 membri e saldi di test.
     * 
     * SETUP:
     * - Alice (Admin): -50€ (deve ricevere)
     * - Bob (Member): -30€ (deve ricevere)
     * - Charlie (Member): +80€ (deve pagare)
     */
    @BeforeEach
    void setUp() throws Exception {
        super.setUp();
        
        // Inizializza i DAO usando quelli del BaseIntegrationTest
        this.settlementDAO = new SettlementDAO(groupDAO, membershipDAO);
        
        // Inizializza i Service
        this.balanceService = new BalanceService(balanceDAO, membershipDAO, groupDAO, new MinTransactionsStrategy());
        this.settlementService = new SettlementService(settlementDAO, membershipDAO, balanceDAO, groupDAO);
        
        // Crea dati di test
        userId1 = createUser("alice@test.com", "Alice", "password123");
        userId2 = createUser("bob@test.com", "Bob", "password123");
        userId3 = createUser("charlie@test.com", "Charlie", "password123");
        
        groupId = createGroup("Vacanza", "EUR", userId1);
        
        membershipId1 = createMembership(userId1, groupId, "ADMIN", "ACTIVE");
        membershipId2 = createMembership(userId2, groupId, "MEMBER", "ACTIVE");
        membershipId3 = createMembership(userId3, groupId, "MEMBER", "ACTIVE");
        
        updateBalance(membershipId1, new BigDecimal("-50.00"));
        updateBalance(membershipId2, new BigDecimal("-30.00"));
        updateBalance(membershipId3, new BigDecimal("80.00"));
    }
    
    /**
     * UC6: Test visualizzazione saldi del gruppo.
     * -Gruppo con 3 membri con saldi noti
     * -Si richiede la visualizzazione dei saldi
     * -Tutti i saldi sono restituiti correttamente
     */
    @Test
    void UC6_viewGroupBalances_shouldReturnCorrectDebts() {
        Map<Membership, BigDecimal> balances = balanceService.getGroupBalances(groupId);
        
        assertEquals(3, balances.size());
        
        BigDecimal aliceBalance = findBalanceByName(balances, "Alice");
        BigDecimal bobBalance = findBalanceByName(balances, "Bob");
        BigDecimal charlieBalance = findBalanceByName(balances, "Charlie");
        
        assertEquals(0, new BigDecimal("-50.00").compareTo(aliceBalance));
        assertEquals(0, new BigDecimal("-30.00").compareTo(bobBalance));
        assertEquals(0, new BigDecimal("80.00").compareTo(charlieBalance));
    }
    
    /**
     * UC6: Test calcolo transazioni ottimizzate.
     * 
     * -Gruppo con debiti e crediti complessi
     * -Si richiede il calcolo delle transazioni ottimizzate
     * -Il numero di transazioni è minimizzato
     */
    @Test
    void UC6_getOptimizedDebts_shouldMinimizeTransactions() {
        List<Settlement> optimizedDebts = balanceService.getOptimizedDebts(groupId);
        
        assertEquals(2, optimizedDebts.size());
        
        BigDecimal totalFromDebtors = optimizedDebts.stream()
            .map(Settlement::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        assertEquals(0, new BigDecimal("80.00").compareTo(totalFromDebtors));
    }
    
    /**
     * UC8: Test settlement completo tra due utenti.
     * 
     * SCENARIO: Alice paga l'intero debito a Charlie
     * 
     */
    @Test
    void UC8_settleUp_betweenTwoUsers_shouldUpdateBalances() throws Exception {
        BigDecimal aliceDebt = new BigDecimal("50.00");
        
        System.out.println("[TEST] Starting: Alice pays 50€ to Charlie");
        
        Settlement settlement = settlementService.createSettlement(
            groupId, membershipId1, membershipId3, aliceDebt);
        
        Long settlementId = settlement.getSettlementId();
        System.out.println("[TEST] Created settlement ID: " + settlementId);
        
        settlementService.confirmSettlement(settlementId, membershipId3);
        
        settlement = settlementDAO.findById(settlementId)
                .orElseThrow(() -> new EntityNotFoundException("Settlement", settlementId));
        
        System.out.println("[TEST] Settlement status: " + settlement.getStatus());
        
        BigDecimal aliceBalanceAfter = getBalance(membershipId1);
        BigDecimal charlieBalanceAfter = getBalance(membershipId3);
        
        System.out.println("[TEST] Alice balance: " + aliceBalanceAfter);
        System.out.println("[TEST] Charlie balance: " + charlieBalanceAfter);
        
        // Verifica balance aggiornati
        assertEquals(0, BigDecimal.ZERO.compareTo(aliceBalanceAfter),
                    "Alice should have zero balance");
        assertEquals(0, new BigDecimal("30.00").compareTo(charlieBalanceAfter),
                    "Charlie should have 30€");
        
        // Verifica stato settlement
        assertFalse(settlement.isPending());
        assertEquals(PaymentStatus.COMPLETED, settlement.getStatus());
    }
    
    /**
     * UC8: Test settlement parziale.
     * 
     * SCENARIO: Alice paga solo parte del debito
     * 
     */
    @Test
    void UC8_settleUp_partialAmount_shouldWork() throws Exception {
        Settlement settlement = settlementService.createSettlement(
            groupId, membershipId1, membershipId3, new BigDecimal("20.00"));
        
        Long settlementId = settlement.getSettlementId();
        settlementService.confirmSettlement(settlementId, membershipId3);
        
        settlement = settlementDAO.findById(settlementId)
                .orElseThrow(() -> new EntityNotFoundException("Settlement", settlementId));
        
        BigDecimal aliceBalance = getBalance(membershipId1);
        BigDecimal charlieBalance = getBalance(membershipId3);
        
        assertEquals(0, new BigDecimal("-30.00").compareTo(aliceBalance));
        assertEquals(0, new BigDecimal("60.00").compareTo(charlieBalance));
        
        assertFalse(settlement.isPending());
        assertEquals(PaymentStatus.COMPLETED, settlement.getStatus());
    }
    
    /**
     * UC8 - Alternative Flow 4a: Importo supera il debito.
     */
    @Test
    void UC8_settleUp_amountExceedsDebt_shouldThrowException() {
        DomainException exception = assertThrows(DomainException.class, () -> {
            settlementService.createSettlement(
                groupId, membershipId1, membershipId3, new BigDecimal("100.00"));
        });
        
        assertTrue(exception.getMessage().contains("exceeds actual debt"));
    }
    
    /**
     * UC8 - Alternative Flow: Utente senza debiti prova a pagare.
     */
    @Test
    void UC8_settleUp_userWithNoDebt_shouldThrowException() {
        DomainException exception = assertThrows(DomainException.class, () -> {
            settlementService.createSettlement(
                groupId, membershipId3, membershipId1, new BigDecimal("10.00"));
        });
        
        assertTrue(exception.getMessage().contains("has no debts"));
    }
    
    /**
     * UC6: Test verifica gruppo completamente saldato.
     */
    @Test
    void UC6_isGroupSettled_whenAllZero_shouldReturnTrue() throws Exception {
        updateBalance(membershipId1, BigDecimal.ZERO);
        updateBalance(membershipId2, BigDecimal.ZERO);
        updateBalance(membershipId3, BigDecimal.ZERO);
        
        boolean isSettled = balanceService.isGroupSettled(groupId);
        
        assertTrue(isSettled);
    }
    
     /**
     * UC6: Test verifica gruppo con debiti pendenti.
     */
    @Test
    void UC6_isGroupSettled_whenDebtsExist_shouldReturnFalse() {
        boolean isSettled = balanceService.isGroupSettled(groupId);
        
        assertFalse(isSettled);
    }
    
    /**
     * UC6: Test recupero lista debitori.
     */
    @Test
    void UC6_getDebtors_shouldReturnOnlyNegativeBalances() {
        List<Membership> debtors = balanceService.getDebtors(groupId);
        
        assertEquals(2, debtors.size());
        
        final String aliceName = "Alice";
        final String bobName = "Bob";
        final String charlieName = "Charlie";
        
        assertTrue(debtors.stream().anyMatch(m -> m.getUser().getFullName().equals(aliceName)));
        assertTrue(debtors.stream().anyMatch(m -> m.getUser().getFullName().equals(bobName)));
        assertFalse(debtors.stream().anyMatch(m -> m.getUser().getFullName().equals(charlieName)));
    }
    
    /**
     * UC6: Test recupero lista creditori.
     */
    @Test
    void UC6_getCreditors_shouldReturnOnlyPositiveBalances() {
        List<Membership> creditors = balanceService.getCreditors(groupId);
        
        assertEquals(1, creditors.size());
        assertEquals("Charlie", creditors.get(0).getUser().getFullName());
    }
    
    /**
     * UC8: Test cancellazione settlement prima della conferma.
     * 
     * SCENARIO: Alice annulla il settlement prima che Charlie lo confermi
     * 
     */
    @Test
    void UC8_cancelSettlement_beforeConfirmation_shouldWork() throws Exception {
        Settlement settlement = settlementService.createSettlement(
            groupId, membershipId1, membershipId3, new BigDecimal("20.00"));
        
        Long settlementId = settlement.getSettlementId();
        
        settlementService.cancelSettlement(settlementId, membershipId1);
        
        settlement = settlementDAO.findById(settlementId)
                .orElseThrow(() -> new EntityNotFoundException("Settlement", settlementId));
        
        BigDecimal aliceBalance = getBalance(membershipId1);
        BigDecimal charlieBalance = getBalance(membershipId3);
        
        assertEquals(0, new BigDecimal("-50.00").compareTo(aliceBalance));
        assertEquals(0, new BigDecimal("80.00").compareTo(charlieBalance));
        
        assertEquals(PaymentStatus.REJECTED, settlement.getStatus());
        assertFalse(settlement.isPending());
    }
    
    /**
     * Helper method per trovare il balance di un utente per nome.
     * 
     * @param balances mappa Membership → BigDecimal
     * @param name nome completo dell'utente
     * @return BigDecimal del balance dell'utente
     * @throws AssertionError se l'utente non viene trovato
     */
    private BigDecimal findBalanceByName(Map<Membership, BigDecimal> balances, String name) {
        final String searchName = name;
        return balances.entrySet().stream()
            .filter(entry -> entry.getKey().getUser().getFullName().equals(searchName))
            .findFirst()
            .map(Map.Entry::getValue)
            .orElseThrow(() -> new AssertionError("User " + name + " not found in balances"));
    }
}