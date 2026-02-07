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
        
        // Inizializza il Service con DAO reali
        groupService = new GroupService(groupDAO, membershipDAO, balanceDAO);
        
        // Crea utenti: Admin, Debtor, Creditor, Neutral
        adminId = createUser("admin@test.com", "Admin", "password123");
        memberWithDebtId = createUser("debtor@test.com", "Debtor", "password123");
        memberInCreditId = createUser("creditor@test.com", "Creditor", "password123");
        neutralUserId = createUser("neutral@test.com", "Neutral", "password123");
        
        // Crea gruppo
        groupId = createGroup("Progetto", "EUR", adminId);
        
        // Crea membership con ruoli diversi
        adminMembershipId = createMembership(adminId, groupId, "ADMIN", "ACTIVE");
        debtorMembershipId = createMembership(memberWithDebtId, groupId, "MEMBER", "ACTIVE");
        creditorMembershipId = createMembership(memberInCreditId, groupId, "MEMBER", "ACTIVE");
        neutralMembershipId = createMembership(neutralUserId, groupId, "MEMBER", "ACTIVE");
        
        // Imposta saldi: Debtor ha debito, Creditor ha credito, Neutral ha saldo zero
        updateBalance(debtorMembershipId, new BigDecimal("-50.00"));
        updateBalance(creditorMembershipId, new BigDecimal("50.00"));
        updateBalance(neutralMembershipId, BigDecimal.ZERO);
        
        // Verifica che i balance siano stati salvati
        BigDecimal debtorBalance = getBalance(debtorMembershipId);
        BigDecimal creditorBalance = getBalance(creditorMembershipId);
        BigDecimal neutralBalance = getBalance(neutralMembershipId);
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
        // Verifica il balance prima del test
        BigDecimal balanceBefore = getBalance(debtorMembershipId);
        
        // WHEN/THEN: Tentativo di rimuovere un membro con debito
        DomainException exception = assertThrows(DomainException.class, () -> {
            groupService.removeMember(groupId, debtorMembershipId, adminMembershipId);
        });

        assertTrue(exception.getMessage().contains("pending debts") || 
                   exception.getMessage().contains("Cannot remove") ||
                   exception.getMessage().contains("non-zero balance"));
        
        // VERIFICA: Il membro è ancora nel gruppo
        boolean isStillActive = isMemberActive(groupId, memberWithDebtId);
        assertTrue(isStillActive, "Debtor should still be in the group");
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
        // Creditor ha credito (+50), secondo UC10 può essere rimosso
        // Rimuovo il membro con credito
        groupService.removeMember(groupId, creditorMembershipId, adminMembershipId);
        
        // Il membro viene rimosso (status = REMOVED)
        boolean isActive = isMemberActive(groupId, memberInCreditId);
        assertFalse(isActive, "Creditor should be removed");
        
        // E il credito rimane nel sistema
        BigDecimal creditorBalance = getBalance(creditorMembershipId);
        assertEquals(new BigDecimal("50.00"), creditorBalance);
    }
    
    /**
     * UC10 - Main Flow: Rimozione membro con saldo zero.
     * 
     */
    @Test
    void UC10_removeMember_zeroBalance_shouldSucceed() throws Exception {
        // Rimuovo il membro con saldo zero
        groupService.removeMember(groupId, neutralMembershipId, adminMembershipId);
        
        // Operazione riuscita
        assertFalse(isMemberActive(groupId, neutralUserId));
    }
    
    /**
     * UC10 - Security: Tentativo di rimozione da parte di non-admin.
     * 
     * MOTIVAZIONE:
     * Solo gli admin possono rimuovere membri dal gruppo.
     */
    @Test
    void UC10_removeMember_byNonAdmin_shouldThrowUnauthorizedException() throws Exception {
        // Creo un membro normale
        Long regularUserId = createUser("regular@test.com", "Regular", "password123");
        Long regularMembershipId = createMembership(regularUserId, groupId, "MEMBER", "ACTIVE");

        // Tentativo di rimuovere un membro senza essere admin
        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () -> {
            groupService.removeMember(groupId, debtorMembershipId, regularMembershipId);
        });

        assertTrue(exception.getMessage().contains("permission") || 
                   exception.getMessage().contains("authorized"));
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
        // Creo un secondo admin con debito
        Long adminWithDebtId = createUser("admin2@test.com", "Admin2", "password123");
        Long admin2MembershipId = createMembership(adminWithDebtId, groupId, "ADMIN", "ACTIVE");
        updateBalance(admin2MembershipId, new BigDecimal("-30.00"));

        // Anche un admin non può essere rimosso se ha debiti
        DomainException exception = assertThrows(DomainException.class, () -> {
            groupService.removeMember(groupId, admin2MembershipId, adminMembershipId);
        });

        assertTrue(exception.getMessage().contains("pending debts") || 
                   exception.getMessage().contains("Cannot remove") ||
                   exception.getMessage().contains("non-zero balance"));
        
        // Verifica che l'admin sia ancora nel gruppo
        boolean isStillActive = isMemberActive(groupId, adminWithDebtId);
        assertTrue(isStillActive, "Admin with debt should still be in the group");
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
        // Creo un nuovo membro in attesa
        Long waitingUserId = createUser("waiting@test.com", "Waiting", "password123");
        Long waitingMembershipId = createMembership(waitingUserId, groupId, "MEMBER", "WAITING_ACCEPTANCE");

        // L'admin approva il membro
        groupService.approveMember(waitingMembershipId, adminMembershipId);
        
        // Il membro diventa ACTIVE
        assertTrue(isMemberActive(groupId, waitingUserId));
    }
    
    /**
     * UC10: Generazione codice invito da parte di admin.
     * 
     */
    @Test
    void UC10_inviteMember_byAdmin_shouldGenerateNewCode() throws Exception {
        // L'admin genera un nuovo codice invito
        String newInviteCode = groupService.inviteMember(groupId, adminMembershipId);
        
        // Il codice non è nullo
        assertNotNull(newInviteCode);
        assertFalse(newInviteCode.isEmpty());

        // E il codice è stato salvato nel gruppo
        String groupInviteCode = getGroupInviteCode(groupId);
        assertEquals(newInviteCode, groupInviteCode);
    }
    
    /**
     * UC10 - Security: Generazione codice invito da parte di non-admin.
     * 
     * MOTIVAZIONE:
     * Solo gli admin possono generare codici invito.
     */
    @Test
    void UC10_inviteMember_byNonAdmin_shouldThrowException() throws Exception {
        // Tentativo di generare codice invito senza permessi
        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () -> {
            groupService.inviteMember(groupId, debtorMembershipId);
        });

        assertTrue(exception.getMessage().contains("Only admins") || 
                   exception.getMessage().contains("authorized"));
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