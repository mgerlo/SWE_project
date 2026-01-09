// language: java
package com.splitmanager.service;

import com.splitmanager.dao.ConnectionManager;
import com.splitmanager.dao.GroupDAO;
import com.splitmanager.dao.MembershipDAO;
import com.splitmanager.dao.BalanceDAO;
import com.splitmanager.domain.accounting.Balance;
import com.splitmanager.domain.registry.*;
import com.splitmanager.exception.DomainException;
import com.splitmanager.exception.EntityNotFoundException;
import com.splitmanager.exception.UnauthorizedException;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Service per la gestione dei gruppi.
 *
 * Responsabilità:
 * - Creazione gruppi (UC3: Create New Group)
 * - Join tramite invito (UC4: Join Group)
 * - Gestione membri (UC9, UC10: Invite/Manage Members)
 * - Configurazione (UC12: Configure Settings)
 */
public class GroupService {

    private final GroupDAO groupDAO;
    private final MembershipDAO membershipDAO;
    private final BalanceDAO balanceDAO;

    public GroupService() {
        this.groupDAO = new GroupDAO();
        this.membershipDAO = new MembershipDAO();
        this.balanceDAO = new BalanceDAO();
    }

    // Costruttore per dependency injection nei test
    public GroupService(GroupDAO groupDAO, MembershipDAO membershipDAO, BalanceDAO balanceDAO) {
        this.groupDAO = groupDAO;
        this.membershipDAO = membershipDAO;
        this.balanceDAO = balanceDAO;
    }

    /**
     * UC3 - Create New Group
     * Crea un nuovo gruppo e imposta il creatore come ADMIN.
     *
     * @param userId ID dell'utente creatore
     * @param name nome del gruppo
     * @param description descrizione (opzionale)
     * @param currency valuta (es. "EUR", "USD")
     * @return Group creato con ID generato
     * @throws DomainException se dati non validi (UC3 Alternative 5a)
     */
    public Group createGroup(Long userId, String name, String description, String currency) {
        // Validazioni (UC3 Alternative 5a)
        if (name == null || name.trim().isEmpty()) {
            throw new DomainException("Group name is required");
        }

        if (currency == null || currency.trim().isEmpty()) {
            throw new DomainException("Currency is required");
        }

        // Validazione valuta (deve essere codice ISO 4217)
        if (!currency.matches("[A-Z]{3}")) {
            throw new DomainException("Invalid currency (use ISO code: EUR, USD, GBP, ...)");
        }

        ConnectionManager connMgr = ConnectionManager.getInstance();

        try {
            connMgr.beginTransaction();

            // 1. Crea il gruppo
            Group group = new Group(null, name, currency);
            if (description != null && !description.trim().isEmpty()) {
                group.setDescription(description);
            }

            // Genera codice invito
            String inviteCode = generateInviteCode();
            group.updateInviteCode(inviteCode, null); // null perché non c'è ancora un admin

            group = groupDAO.save(group);

            // 2. Crea Membership per il creatore come ADMIN
            Membership adminMembership = membershipDAO.createMembership(
                    userId,
                    group.getGroupId(),
                    Role.ADMIN
            );
            adminMembership.activate(); // Subito attivo
            membershipDAO.update(adminMembership);

            // 3. Crea Balance per l'admin
            Balance adminBalance = new Balance(adminMembership);
            balanceDAO.save(adminBalance);
            adminMembership.setBalance(adminBalance);

            connMgr.commit();
            return group;

        } catch (Exception e) {
            try {
                connMgr.rollback();
            } catch (SQLException ex) {
                throw new DomainException("Error during transaction rollback", ex);
            }

            if (e instanceof DomainException) {
                throw (DomainException) e;
            }
            throw new DomainException("Error creating group: " + e.getMessage(), e);
        }
    }

    // Overload per comodità (description opzionale)
    public Group createGroup(Long userId, String name, String currency) {
        return createGroup(userId, name, null, currency);
    }

    /**
     * UC4 - Join Group by Invitation
     * Un utente si unisce a un gruppo tramite codice invito.
     *
     * @param userId ID dell'utente che vuole unirsi
     * @param inviteCode codice invito ricevuto
     * @throws EntityNotFoundException se codice non valido (UC4 Alternative 3a)
     * @throws DomainException se utente già membro (UC4 Alternative 3b)
     */
    public void joinByCode(Long userId, String inviteCode) {
        if (inviteCode == null || inviteCode.trim().isEmpty()) {
            throw new DomainException("Invalid invite code");
        }

        // Trova gruppo per codice invito
        Group group = groupDAO.findByInviteCode(inviteCode)
                .orElseThrow(() -> new EntityNotFoundException("Group", inviteCode, "inviteCode"));

        // Verifica validità codice (UC4 Alternative 3a)
        if (!group.isInviteCodeValid(inviteCode)) {
            throw new DomainException("Invite code expired or invalid");
        }

        // Verifica che l'utente non sia già membro (UC4 Alternative 3b)
        List<Membership> existingMemberships = membershipDAO.findByUserAndGroup(userId, group.getGroupId());
        if (!existingMemberships.isEmpty()) {
            throw new DomainException("You are already a member of this group");
        }

        ConnectionManager connMgr = ConnectionManager.getInstance();

        try {
            connMgr.beginTransaction();

            // Crea Membership in stato WAITING_ACCEPTANCE
            Membership membership = membershipDAO.createMembership(
                    userId,
                    group.getGroupId(),
                    Role.MEMBER
            );
            // Lo stato è WAITING_ACCEPTANCE per default

            // Crea Balance per il nuovo membro
            Balance balance = new Balance(membership);
            balanceDAO.save(balance);
            membership.setBalance(balance);

            connMgr.commit();

        } catch (Exception e) {
            try {
                connMgr.rollback();
            } catch (SQLException ex) {
                throw new DomainException("Error during rollback", ex);
            }
            throw new DomainException("Error joining the group: " + e.getMessage(), e);
        }
    }

    /**
     * UC9 - Invite New Member
     * Genera un nuovo codice invito per il gruppo.
     * Solo gli ADMIN possono farlo.
     *
     * @param groupId ID del gruppo
     * @param adminMembershipId ID del membership dell'admin
     * @return nuovo codice invito generato
     * @throws UnauthorizedException se non è admin (UC9 Alternative 2a)
     */
    public String inviteMember(Long groupId, Long adminMembershipId) {
        Group group = groupDAO.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group", groupId));

        Membership admin = membershipDAO.findById(adminMembershipId)
                .orElseThrow(() -> new EntityNotFoundException("Membership", adminMembershipId));

        // Verifica permessi
        if (!group.canInviteMember(admin)) {
            throw new UnauthorizedException("Only admins can generate invite codes");
        }

        // Genera nuovo codice
        String newInviteCode = generateInviteCode();

        // Aggiorna gruppo
        group.updateInviteCode(newInviteCode, admin);
        groupDAO.update(group);

        return newInviteCode;
    }

    /**
     * UC10 - Approve Member
     * L'admin approva un membro in attesa.
     *
     * @param membershipId ID del membership da approvare
     * @param adminMembershipId ID del membership dell'admin
     * @throws UnauthorizedException se non è admin
     */
    public void approveMember(Long membershipId, Long adminMembershipId) {
        Membership memberToApprove = membershipDAO.findById(membershipId)
                .orElseThrow(() -> new EntityNotFoundException("Membership", membershipId));

        Membership admin = membershipDAO.findById(adminMembershipId)
                .orElseThrow(() -> new EntityNotFoundException("Membership", adminMembershipId));

        // Verifica che siano dello stesso gruppo
        if (!memberToApprove.getGroup().getGroupId().equals(admin.getGroup().getGroupId())) {
            throw new DomainException("Members must belong to the same group");
        }

        // Verifica permessi
        if (!admin.isAdmin()) {
            throw new UnauthorizedException("Only admins can approve members");
        }

        // Approva
        memberToApprove.activate();
        membershipDAO.update(memberToApprove);

        // Notifica evento sul gruppo
        Group group = admin.getGroup();
        group.addMembership(memberToApprove, admin);
        groupDAO.update(group);
    }

    /**
     * UC10 - Remove Member
     * L'admin rimuove un membro dal gruppo.
     *
     * @param groupId ID del gruppo
     * @param membershipIdToRemove ID del membership da rimuovere
     * @param adminMembershipId ID del membership dell'admin
     * @throws UnauthorizedException se non autorizzato
     * @throws DomainException se il membro ha debiti (UC10 Alternative 3a)
     */
    public void removeMember(Long groupId, Long membershipIdToRemove, Long adminMembershipId) {
        Group group = groupDAO.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group", groupId));

        Membership memberToRemove = membershipDAO.findById(membershipIdToRemove)
                .orElseThrow(() -> new EntityNotFoundException("Membership", membershipIdToRemove));

        Membership admin = membershipDAO.findById(adminMembershipId)
                .orElseThrow(() -> new EntityNotFoundException("Membership", adminMembershipId));

        // Verifica permessi
        if (!group.canRemoveMember(admin, memberToRemove)) {
            throw new UnauthorizedException("You don't have permission to remove this member");
        }

        // Verifica debiti pendenti (UC10 Alternative 3a)
        if (!memberToRemove.canBeRemoved()) {
            throw new DomainException("The member has pending debts and cannot be removed");
        }

        // Rimuovi
        memberToRemove.terminate();
        membershipDAO.update(memberToRemove);

        // Notifica evento
        group.removeMembership(memberToRemove, admin);
        groupDAO.update(group);
    }

    /**
     * UC12 - Configure Group Settings
     * Aggiorna le impostazioni del gruppo.
     *
     * @param groupId ID del gruppo
     * @param adminMembershipId ID del membership dell'admin
     * @param newName nuovo nome (opzionale)
     * @param newDescription nuova descrizione (opzionale)
     * @param newCurrency nuova valuta (opzionale)
     * @throws UnauthorizedException se non è admin (UC12 Alternative 3a)
     */
    public void updateSettings(Long groupId, Long adminMembershipId,
                               String newName, String newDescription, String newCurrency) {

        Group group = groupDAO.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group", groupId));

        Membership admin = membershipDAO.findById(adminMembershipId)
                .orElseThrow(() -> new EntityNotFoundException("Membership", adminMembershipId));

        // Usa i valori attuali se non forniti
        String name = (newName != null && !newName.trim().isEmpty()) ? newName : group.getName();
        String description = (newDescription != null) ? newDescription : group.getDescription();
        String currency = (newCurrency != null && !newCurrency.trim().isEmpty()) ? newCurrency : group.getCurrency();

        // Aggiorna gruppo (verifica permessi all'interno)
        group.updateGroupInfo(name, description, currency, admin);

        groupDAO.update(group);
    }

    /**
     * Ottiene tutti i membri di un gruppo.
     *
     * @param groupId ID del gruppo
     * @return lista di Membership
     */
    public List<Membership> getGroupMembers(Long groupId) {
        return membershipDAO.findByGroup(groupId);
    }

    // --- Helper privati ---

    private String generateInviteCode() {
        // Genera codice alfanumerico di 8 caratteri
        return UUID.randomUUID().toString()
                .replaceAll("-", "")
                .substring(0, 8)
                .toUpperCase();
    }
}
