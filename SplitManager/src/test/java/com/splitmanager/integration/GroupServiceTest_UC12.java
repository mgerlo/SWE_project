package com.splitmanager.integration;

import com.splitmanager.businesslogic.service.GroupService;
import com.splitmanager.dao.*;
import com.splitmanager.domain.registry.Group;
import com.splitmanager.exception.DomainException;
import com.splitmanager.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test di integrazione per la configurazione delle impostazioni del gruppo.
 * 
 * CASI D'USO TESTATI:
 * - UC12: Configure group settings
 *   - Aggiornamento nome, descrizione, valuta
 *   - Rigenerazione codice invito
 *   - Creazione nuovo gruppo
 *   - Join tramite codice invito
 * 
 * REGOLE DI SICUREZZA:
 * - Solo gli ADMIN possono modificare le impostazioni del gruppo
 * - I membri normali possono solo visualizzare
 * 
 * SCENARIO DI TEST:
 * Gruppo "Casa" con 2 membri:
 * - Admin: può modificare le impostazioni
 * - Member: può solo visualizzare
 */
public class GroupServiceTest_UC12 extends BaseIntegrationTest {
    
    private GroupService groupService;
    
    // ID delle entità di test
    private Long groupId;
    private Long adminId, memberId;
    private Long adminMembershipId, memberMembershipId;
    
    /**
     * Setup del test: crea gruppo con admin e membro normale.
     * 
     * MEMBRI CREATI:
     * - Admin (ADMIN): può modificare impostazioni
     * - Member (MEMBER): accesso read-only
     */
    @BeforeEach
    void setUp() throws Exception {
        super.setUp();
        
        // Inizializza il Service
        groupService = new GroupService(groupDAO, membershipDAO, balanceDAO);
        
        // Crea utenti
        adminId = createUser("admin@test.com", "Admin", "password123");
        memberId = createUser("member@test.com", "Member", "password123");
        
        // Crea gruppo
        groupId = createGroup("Casa", "EUR", adminId);
        
        // Crea membership
        adminMembershipId = createMembership(adminId, groupId, "ADMIN", "ACTIVE");
        memberMembershipId = createMembership(memberId, groupId, "MEMBER", "ACTIVE");
    }
    
    /**
     * UC12 - Main Flow: Admin aggiorna nome del gruppo.
     *
     */
    @Test
    void UC12_updateGroupName_admin_shouldSucceed() throws Exception {
        // Nuovo nome
        String newName = "Casa Vacanze";
        
        // Admin cambia il nome
        groupService.updateSettings(groupId, adminMembershipId, newName, null, null);
        
        // Il nome viene aggiornato
        String updatedName = getGroupName(groupId);
        assertEquals(newName, updatedName);
    }
    
    /**
     * UC12 - Security: Membro normale prova ad aggiornare nome.
     * 
     */
    @Test
    void UC12_updateGroupName_nonAdmin_shouldThrowUnauthorizedException() throws Exception {
        // Membro normale non può cambiare nome
        UnauthorizedException exception = assertThrows(UnauthorizedException.class, () -> {
            groupService.updateSettings(groupId, memberMembershipId, "Nuovo Nome", null, null);
        });
        
        assertTrue(exception.getMessage().contains("permission") || 
                   exception.getMessage().contains("admin") ||
                   exception.getMessage().contains("authorized"));
        
        // VERIFICA: Il nome non è cambiato
        assertEquals("Casa", getGroupName(groupId));
    }
    
    /**
     * UC12: Admin aggiorna valuta del gruppo.
     * 
     * PRECONDIZIONE: Tutti i balance devono essere a zero per cambiare valuta.
     * 
     */
    @Test
    void UC12_updateCurrency_noBalances_shouldSucceed() throws Exception {
        // Admin cambia valuta
        groupService.updateSettings(groupId, adminMembershipId, null, null, "USD");
        
        // Valuta aggiornata
        String updatedCurrency = getGroupCurrency(groupId);
        assertEquals("USD", updatedCurrency);
    }
    
    /**
     * UC12: Admin aggiorna descrizione del gruppo.
     * 
     */
    @Test
    void UC12_updateGroupDescription_admin_shouldSucceed() throws Exception {
        // Nuova descrizione
        String newDescription = "Gruppo per gestire spese della casa vacanze";
        
        // Admin aggiorna descrizione
        groupService.updateSettings(groupId, adminMembershipId, null, newDescription, null);
        
        // Descrizione aggiornata
        String updatedDescription = getGroupDescription(groupId);
        assertEquals(newDescription, updatedDescription);
    }
    
    /**
     * UC12: Admin rigenera codice invito.
     * 
     * MOTIVAZIONE:
     * L'admin può voler rigenerare il codice se:
     * - Il vecchio codice è stato condiviso con persone non desiderate
     * - Il codice è scaduto
     * 
     */
    @Test
    void UC12_regenerateInviteCode_admin_shouldGenerateNewCode() throws Exception {
        // Codice invito esistente
        String oldCode = getGroupInviteCode(groupId);
        assertNotNull(oldCode);
        
        // Admin rigenera il codice
        String newCode = groupService.inviteMember(groupId, adminMembershipId);
        
        // Nuovo codice generato
        assertNotNull(newCode);
        assertNotEquals(oldCode, newCode);
    }
    
    /**
     * UC12: Aggiornamento atomico di tutte le impostazioni.
     * 
     * SCENARIO: Admin vuole aggiornare più impostazioni contemporaneamente.
     * 
     */
    @Test
    void UC12_updateMultipleSettings_atomicUpdate() throws Exception {
        // Tutte le impostazioni nuove
        String newName = "Nuovo Nome";
        String newDescription = "Nuova Descrizione";
        String newCurrency = "USD";
        
        // Admin aggiorna tutto insieme
        groupService.updateSettings(groupId, adminMembershipId, newName, newDescription, newCurrency);
        
        // Tutte le modifiche sono applicate
        Group updatedGroup = groupDAO.findById(groupId).orElseThrow();
        
        assertEquals(newName, updatedGroup.getName());
        assertEquals(newDescription, updatedGroup.getDescription());
        assertEquals(newCurrency, updatedGroup.getCurrency());
    }
    
    /**
     * UC12: Creazione nuovo gruppo con dati validi.
     * 
     * FLUSSO:
     * 1. Utente fornisce nome, descrizione, valuta
     * 2. Sistema crea gruppo
     * 3. Utente creatore diventa ADMIN
     * 4. Viene generato codice invito
     * 
     */
    @Test
    void UC12_createGroup_withValidData_shouldSucceed() throws Exception {
        // Creo un nuovo gruppo
        Group newGroup = groupService.createGroup(adminId, "Viaggio", "Vacanza a Roma", "GBP");
        
        // Il gruppo viene creato correttamente
        assertNotNull(newGroup);
        assertNotNull(newGroup.getGroupId());
        assertEquals("Viaggio", newGroup.getName());
        assertEquals("GBP", newGroup.getCurrency());
        assertEquals("Vacanza a Roma", newGroup.getDescription());
        
        // E il creatore è ADMIN
        Long creatorMembershipId = getMembershipId(adminId, newGroup.getGroupId());
        String creatorRole = getMembershipRole(creatorMembershipId);
        assertEquals("ADMIN", creatorRole);
    }
    
    /**
     * UC12 - Validation: Creazione gruppo con valuta non valida.
     * 
     * REGOLA: La valuta deve essere un codice ISO a 3 caratteri (EUR, USD, GBP, etc.).
     * 
     */
    @Test
    void UC12_createGroup_invalidCurrency_shouldThrowException() {
        // Tentativo di creare gruppo con valuta non valida
        DomainException exception = assertThrows(DomainException.class, () -> {
            groupService.createGroup(adminId, "Gruppo", "Descrizione", "EURO"); // 4 caratteri
        });
        
        assertTrue(exception.getMessage().contains("currency") || 
                   exception.getMessage().contains("ISO code") ||
                   exception.getMessage().contains("Invalid currency"));
    }
    
    /**
     * UC12 - Validation: Creazione gruppo senza nome.
     * 
     * REGOLA: Il nome del gruppo è obbligatorio.
     * 
     */
    @Test
    void UC12_createGroup_emptyName_shouldThrowException() {
        // Tentativo di creare gruppo senza nome
        DomainException exception = assertThrows(DomainException.class, () -> {
            groupService.createGroup(adminId, "", "Descrizione", "EUR");
        });
        
        assertTrue(exception.getMessage().contains("name") || 
                   exception.getMessage().contains("required") ||
                   exception.getMessage().contains("Group name"));
    }
    
    /**
     * UC12: Join al gruppo tramite codice invito.
     * 
     * FLUSSO:
     * 1. Utente ottiene codice invito da un membro
     * 2. Utente si registra con il codice
     * 3. Viene creata membership in WAITING_ACCEPTANCE
     * 4. Admin approva la membership (UC10)
     * 
     */
    @Test
    void UC12_joinGroup_byInviteCode_shouldAddMembership() throws Exception {
        // Ottengo il codice invito del gruppo
        String inviteCode = getGroupInviteCode(groupId);
        
        // Creo un nuovo utente
        Long newUserId = createUser("newuser@test.com", "New User", "password123");
        
        // L'utente si unisce con il codice
        groupService.joinByCode(newUserId, inviteCode);
        
        // Viene creata una membership in WAITING_ACCEPTANCE
        Long newMembershipId = getMembershipId(newUserId, groupId);
        assertNotNull(newMembershipId);
        
        String status = getMembershipStatus(newMembershipId);
        assertEquals("WAITING_ACCEPTANCE", status);
    }
    
    /**
     * UC12 - Alternative Flow: Join con codice invito non valido.
     * 
     */
    @Test
    void UC12_joinGroup_invalidCode_shouldThrowException() throws Exception {
        Long newUserId = createUser("newuser@test.com", "New User", "password123");
        
        DomainException exception = assertThrows(DomainException.class, () -> {
            groupService.joinByCode(newUserId, "CODICEINVALIDO");
        });
        
        assertTrue(exception.getMessage().contains("not found") || 
                   exception.getMessage().contains("Invalid invite code"));
    }
    
   // ==================== HELPER METHODS ====================
    
    /**
     * Recupera il nome del gruppo dal database.
     */
    private String getGroupName(Long groupId) throws Exception {
        return executeScalarQuery("SELECT name FROM groups WHERE group_id = ?", groupId);
    }
    
    /**
     * Recupera la valuta del gruppo dal database.
     */
    private String getGroupCurrency(Long groupId) throws Exception {
        return executeScalarQuery("SELECT currency FROM groups WHERE group_id = ?", groupId);
    }
    
    /**
     * Recupera la descrizione del gruppo dal database.
     */
    private String getGroupDescription(Long groupId) throws Exception {
        return executeScalarQuery("SELECT description FROM groups WHERE group_id = ?", groupId);
    }
    
    /**
     * Recupera il codice invito del gruppo dal database.
     */
    private String getGroupInviteCode(Long groupId) throws Exception {
        return executeScalarQuery("SELECT invite_code FROM groups WHERE group_id = ?", groupId);
    }
    
    /**
     * Recupera il ruolo di una membership dal database.
     */
    private String getMembershipRole(Long membershipId) throws Exception {
        return executeScalarQuery("SELECT role FROM memberships WHERE membership_id = ?", membershipId);
    }
    
    /**
     * Recupera lo status di una membership dal database.
     */
    private String getMembershipStatus(Long membershipId) throws Exception {
        return executeScalarQuery("SELECT status FROM memberships WHERE membership_id = ?", membershipId);
    }
    
    /**
     * Utility per eseguire query scalari (singolo valore).
     */
    private String executeScalarQuery(String sql, Long id) throws Exception {
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        return null;
    }
}