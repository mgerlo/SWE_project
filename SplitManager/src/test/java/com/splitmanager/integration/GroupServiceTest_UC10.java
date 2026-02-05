package com.splitmanager.integration;

import com.splitmanager.businesslogic.service.GroupService;
import com.splitmanager.dao.*;
import com.splitmanager.exception.DomainException;
import com.splitmanager.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test di integrazione per la gestione dei membri del gruppo.
 * 
 * CASI D'USO TESTATI:
 * - UC10: Manage group members
 *   - Rimozione membri (con validazione balance)
 *   - Approvazione nuovi membri
 *   - Generazione codici invito
 * 
 * REGOLA BUSINESS CRITICA:
 * Un membro con debiti (balance < 0) NON può essere rimosso dal gruppo.
 * Questa regola garantisce che i debiti vengano sempre saldati prima
 * dell'uscita di un membro.
 * 
 * SCENARIO DI TEST:
 * Gruppo "Progetto" con 4 membri:
 * - Admin: amministratore (saldo 0)
 * - Debtor: membro con debito di 50€
 * - Creditor: membro con credito di 50€
 * - Neutral: membro con saldo 0
 */
public class GroupServiceTest_UC10 extends BaseIntegrationTest {
    
    private GroupService groupService;
    
    // ID delle entità di test
    private Long groupId;
    private Long adminId, memberWithDebtId, memberInCreditId, neutralUserId;
    private Long adminMembershipId, debtorMembershipId, creditorMembershipId, neutralMembershipId;
    
    /**
     * Setup del test: crea gruppo con membri con diversi stati di balance.
     * 
     * MEMBRI CREATI:
     * - Admin (ADMIN, saldo 0): può eseguire operazioni amministrative
     * - Debtor (MEMBER, saldo -50€): ha debiti pendenti
     * - Creditor (MEMBER, saldo +50€): ha crediti
     * - Neutral (MEMBER, saldo 0): né debiti né crediti
     */
    @BeforeEach
    void setUp() throws Exception {
        super.setUp();
        
        System.out.println("[TEST SETUP] Starting GroupServiceTest_UC10 setup");
        
        // Inizializza il Service con DAO reali
        groupService = new GroupService(groupDAO, membershipDAO, balanceDAO);
        
        // Crea utenti
        adminId = createUser("admin@test.com", "Admin", "password123");
        memberWithDebtId = createUser("debtor@test.com", "Debtor", "password123");
        memberInCreditId = createUser("creditor@test.com", "Creditor", "password123");
        neutralUserId = createUser("neutral@test.com", "Neutral", "password123");
        
        System.out.println("[TEST SETUP] Created users: Admin, Debtor, Creditor, Neutral");
        
        // Crea gruppo
        groupId = createGroup("Progetto", "EUR", adminId);
        System.out.println("[TEST SETUP] Created group ID: " + groupId);
        
        // Crea membership con ruoli diversi
        adminMembershipId = createMembership(adminId, groupId, "ADMIN", "ACTIVE");
        debtorMembershipId = createMembership(memberWithDebtId, groupId, "MEMBER", "ACTIVE");
        creditorMembershipId = createMembership(memberInCreditId, groupId, "MEMBER", "ACTIVE");
        neutralMembershipId = createMembership(neutralUserId, groupId, "MEMBER", "ACTIVE");
        
        System.out.println("[TEST SETUP] Created memberships:");
        System.out.println("  - Admin: " + adminMembershipId);
        System.out.println("  - Debtor: " + debtorMembershipId);
        System.out.println("  - Creditor: " + creditorMembershipId);
        System.out.println("  - Neutral: " + neutralMembershipId);
        
        // Imposta saldi: Debtor ha debito, Creditor ha credito, Neutral ha saldo zero
        updateBalance(debtorMembershipId, new BigDecimal("-50.00"));
        updateBalance(creditorMembershipId, new BigDecimal("50.00"));
        updateBalance(neutralMembershipId, BigDecimal.ZERO);
        
        System.out.println("[TEST SETUP] Set balances:");
        System.out.println("  - Debtor: -50.00");
        System.out.println("  - Creditor: +50.00");
        System.out.println("  - Neutral: 0.00");
        
        // Verifica che i balance siano stati salvati
        BigDecimal debtorBalance = getBalance(debtorMembershipId);
        BigDecimal creditorBalance = getBalance(creditorMembershipId);
        BigDecimal neutralBalance = getBalance(neutralMembershipId);
        
        System.out.println("[TEST SETUP] Verified balances from DB:");
        System.out.println("  - Debtor: " + debtorBalance);
        System.out.println("  - Creditor: " + creditorBalance);
        System.out.println("  - Neutral: " + neutralBalance);
        
        System.out.println("[TEST SETUP] Setup completed successfully");
    }
    
    /**
     * UC10 - Main Flow: Tentativo di rimozione membro con debiti.
     * 
     * REGOLA BUSINESS: Un membro con balance < 0 NON può essere rimosso.
     *
     * MOTIVAZIONE:
     * Previene che membri con debiti lascino il gruppo senza saldare.
     */
    @Test
    void UC10_removeMember_withDebt_shouldThrowBusinessException() throws Exception {
        System.out.println("[TEST] UC10_removeMember_withDebt_shouldThrowBusinessException - START");
        System.out.println("[TEST] Attempting to remove debtor member ID: " + debtorMembershipId);
        
        // Verifica il balance prima del test
        BigDecimal balanceBefore = getBalance(debtorMembershipId);
        System.out.println("[TEST] Debtor balance before removal attempt: " + balanceBefore);
        
        // WHEN/THEN: Tentativo di rimuovere un membro con debito
        DomainException exception = assertThrows(DomainException.class, () -> {
            System.out.println("[TEST] Calling groupService.removeMember()...");
            groupService.removeMember(groupId, debtorMembershipId, adminMembershipId);
            System.out.println("[TEST] ERROR: removeMember() completed without exception!");
        });
        
        System.out.println("[TEST] DomainException thrown as expected: " + exception.getMessage());
        assertTrue(exception.getMessage().contains("pending debts") || 
                   exception.getMessage().contains("Cannot remove") ||
                   exception.getMessage().contains("non-zero balance"));
        
        // VERIFICA: Il membro è ancora nel gruppo
        boolean isStillActive = isMemberActive(groupId, memberWithDebtId);
        System.out.println("[TEST] Debtor is still active in group: " + isStillActive);
        assertTrue(isStillActive, "Debtor should still be in the group");
        
        System.out.println("[TEST] UC10_removeMember_withDebt_shouldThrowBusinessException - PASSED");
    }
    
    /**
     * UC10 - Alternative Flow: Rimozione membro con credito.
     * 
     * REGOLA BUSINESS: Un membro con balance >= 0 PUÒ essere rimosso.
     * Il credito rimane nel sistema per essere recuperato.
     * 
     */
    @Test
    void UC10_removeMember_inCredit_shouldSucceed() throws Exception {
        System.out.println("[TEST] UC10_removeMember_inCredit_shouldSucceed - START");
        System.out.println("[TEST] Attempting to remove creditor member ID: " + creditorMembershipId);
        
        // Creditor ha credito (+50), secondo UC10 può essere rimosso
        // Rimuovo il membro con credito
        groupService.removeMember(groupId, creditorMembershipId, adminMembershipId);
        
        // Il membro viene rimosso (status = REMOVED)
        boolean isActive = isMemberActive(groupId, memberInCreditId);
        System.out.println("[TEST] Creditor is still active: " + isActive);
        assertFalse(isActive, "Creditor should be removed");
        
        // E il credito rimane nel sistema
        BigDecimal creditorBalance = getBalance(creditorMembershipId);
        System.out.println("[TEST] Creditor balance after removal: " + creditorBalance);
        assertEquals(new BigDecimal("50.00"), creditorBalance);
        
        System.out.println("[TEST] UC10_removeMember_inCredit_shouldSucceed - PASSED");
    }
    
    /**
     * UC10 - Main Flow: Rimozione membro con saldo zero.
     * 
     */
    @Test
    void UC10_removeMember_zeroBalance_shouldSucceed() throws Exception {
        System.out.println("[TEST] UC10_removeMember_zeroBalance_shouldSucceed - START");
        
        // Rimuovo il membro con saldo zero
        groupService.removeMember(groupId, neutralMembershipId, adminMembershipId);
        
        // Operazione riuscita
        assertFalse(isMemberActive(groupId, neutralUserId));
        
        System.out.println("[TEST] UC10_removeMember_zeroBalance_shouldSucceed - PASSED");
    }
    
    /**
     * UC10 - Security: Tentativo di rimozione da parte di non-admin.
     * 
     * MOTIVAZIONE:
     * Solo gli admin possono rimuovere membri dal gruppo.
     */
    @Test
    void UC10_removeMember_byNonAdmin_shouldThrowUnauthorizedException() throws Exception {
        System.out.println("[TEST] UC10_removeMember_byNonAdmin_shouldThrowUnauthorizedException - START");
        
        // Creo un membro normale
        Long regularUserId = createUser("regular@test.com", "Regular", "password123");
        Long regularMembershipId = createMembership(regularUserId, groupId, "MEMBER", "ACTIVE");
        
        System.out.println("[TEST] Created regular member ID: " + regularMembershipId);
        
        // Tentativo di rimuovere un membro senza essere admin
        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () -> {
            groupService.removeMember(groupId, debtorMembershipId, regularMembershipId);
        });
        
        System.out.println("[TEST] UnauthorizedException thrown: " + exception.getMessage());
        assertTrue(exception.getMessage().contains("permission") || 
                   exception.getMessage().contains("authorized"));
        
        System.out.println("[TEST] UC10_removeMember_byNonAdmin_shouldThrowUnauthorizedException - PASSED");
    }
    
    /**
     * UC10 - Edge Case: Admin con debiti non può auto-rimuoversi.
     * 
     * REGOLA APPLICATA UNIVERSALMENTE:
     * Anche gli admin sono soggetti alla regola "no debiti per rimozione".
     * 
     */
    @Test
    void UC10_removeMember_adminWithDebt_shouldAlsoBeBlocked() throws Exception {
        System.out.println("[TEST] UC10_removeMember_adminWithDebt_shouldAlsoBeBlocked - START");
        
        // Creo un secondo admin con debito
        Long adminWithDebtId = createUser("admin2@test.com", "Admin2", "password123");
        Long admin2MembershipId = createMembership(adminWithDebtId, groupId, "ADMIN", "ACTIVE");
        updateBalance(admin2MembershipId, new BigDecimal("-30.00"));
        
        System.out.println("[TEST] Created admin with debt ID: " + admin2MembershipId);
        System.out.println("[TEST] Admin with debt balance: " + getBalance(admin2MembershipId));
        
        // Anche un admin non può essere rimosso se ha debiti
        DomainException exception = assertThrows(DomainException.class, () -> {
            System.out.println("[TEST] Attempting to remove admin with debt...");
            groupService.removeMember(groupId, admin2MembershipId, adminMembershipId);
            System.out.println("[TEST] ERROR: Admin with debt was removed without exception!");
        });
        
        System.out.println("[TEST] DomainException thrown: " + exception.getMessage());
        assertTrue(exception.getMessage().contains("pending debts") || 
                   exception.getMessage().contains("Cannot remove") ||
                   exception.getMessage().contains("non-zero balance"));
        
        // Verifica che l'admin sia ancora nel gruppo
        boolean isStillActive = isMemberActive(groupId, adminWithDebtId);
        System.out.println("[TEST] Admin with debt is still active: " + isStillActive);
        assertTrue(isStillActive, "Admin with debt should still be in the group");
        
        System.out.println("[TEST] UC10_removeMember_adminWithDebt_shouldAlsoBeBlocked - PASSED");
    }
    
    /**
     * UC10: Approvazione membro in attesa.
     * 
     * FLUSSO:
     * 1. Un nuovo utente si unisce al gruppo con codice invito
     * 2. La sua membership è WAITING_ACCEPTANCE
     * 3. Un admin approva la membership
     * 4. La membership diventa ACTIVE
     * 
     */
    @Test
    void UC10_approveMember_waitingAcceptance_shouldActivate() throws Exception {
        System.out.println("[TEST] UC10_approveMember_waitingAcceptance_shouldActivate - START");
        
        // Creo un nuovo membro in attesa
        Long waitingUserId = createUser("waiting@test.com", "Waiting", "password123");
        Long waitingMembershipId = createMembership(waitingUserId, groupId, "MEMBER", "WAITING_ACCEPTANCE");
        
        System.out.println("[TEST] Created waiting member ID: " + waitingMembershipId);
        
        // L'admin approva il membro
        groupService.approveMember(waitingMembershipId, adminMembershipId);
        
        // Il membro diventa ACTIVE
        assertTrue(isMemberActive(groupId, waitingUserId));
        
        System.out.println("[TEST] UC10_approveMember_waitingAcceptance_shouldActivate - PASSED");
    }
    
    /**
     * UC10: Generazione codice invito da parte di admin.
     * 
     */
    @Test
    void UC10_inviteMember_byAdmin_shouldGenerateNewCode() throws Exception {
        System.out.println("[TEST] UC10_inviteMember_byAdmin_shouldGenerateNewCode - START");
        
        // L'admin genera un nuovo codice invito
        String newInviteCode = groupService.inviteMember(groupId, adminMembershipId);
        
        // Il codice non è nullo
        assertNotNull(newInviteCode);
        assertFalse(newInviteCode.isEmpty());
        
        System.out.println("[TEST] Generated invite code: " + newInviteCode);
        
        // E il codice è stato salvato nel gruppo
        String groupInviteCode = getGroupInviteCode(groupId);
        assertEquals(newInviteCode, groupInviteCode);
        
        System.out.println("[TEST] UC10_inviteMember_byAdmin_shouldGenerateNewCode - PASSED");
    }
    
    /**
     * UC10 - Security: Generazione codice invito da parte di non-admin.
     * 
     * MOTIVAZIONE:
     * Solo gli admin possono generare codici invito.
     */
    @Test
    void UC10_inviteMember_byNonAdmin_shouldThrowException() throws Exception {
        System.out.println("[TEST] UC10_inviteMember_byNonAdmin_shouldThrowException - START");
        
        // Tentativo di generare codice invito senza permessi
        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () -> {
            groupService.inviteMember(groupId, debtorMembershipId);
        });
        
        System.out.println("[TEST] UnauthorizedException thrown: " + exception.getMessage());
        assertTrue(exception.getMessage().contains("Only admins") || 
                   exception.getMessage().contains("authorized"));
        
        System.out.println("[TEST] UC10_inviteMember_byNonAdmin_shouldThrowException - PASSED");
    }
    
    /**
     * Helper method: Verifica se un membro è attivo nel gruppo.
     * 
     * @param groupId ID del gruppo
     * @param userId ID dell'utente
     * @return true se il membro ha status ACTIVE
     */
    private boolean isMemberActive(Long groupId, Long userId) throws Exception {
        String sql = "SELECT m.status FROM memberships m " +
                    "WHERE m.user_id = ? AND m.group_id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setLong(2, groupId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return "ACTIVE".equals(rs.getString("status"));
                }
            }
        }
        return false;
    }
    
    /**
     * Helper method: Recupera il codice invito di un gruppo.
     * 
     * @param groupId ID del gruppo
     * @return codice invito del gruppo
     */
    private String getGroupInviteCode(Long groupId) throws Exception {
        String sql = "SELECT invite_code FROM groups WHERE group_id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, groupId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("invite_code");
                }
            }
        }
        return null;
    }
}