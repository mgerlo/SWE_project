package com.splitmanager.structural;

import com.splitmanager.domain.accounting.Balance;
import com.splitmanager.domain.registry.Group;
import com.splitmanager.domain.registry.Membership;
import com.splitmanager.domain.registry.Role;
import com.splitmanager.domain.registry.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Test strutturali (White Box) per Membership.
 *
 * Verifiche principali:
 * - validazioni del costruttore (user, group, role non null);
 * - comportamento di isAdmin, isActive, activate, terminate;
 * - interazione con Balance per hasPendingDebts e canBeRemoved;
 * - set/get di Balance;
 * - equals/hashCode basati su membershipId;
 * - toString contiene informazioni utili.
 */

class MembershipTest {

    private Group group;
    private User userCreator;
    private User userAdmin;
    private User userOther;

    private Membership creator;
    private Membership admin;
    private Membership otherMember;

    @BeforeEach
    void setUp() {
        group = new Group(1L, "Test Group", "EUR");

        userCreator = new User(1L, "creator@test.com", "Creator Name", "hash1");
        userAdmin = new User(2L, "admin@test.com", "Admin Name", "hash2");
        userOther = new User(3L, "other@test.com", "Other Name", "hash3");

        creator = new Membership(1L, userCreator, group, Role.MEMBER);
        admin = new Membership(2L, userAdmin, group, Role.ADMIN);
        otherMember = new Membership(3L, userOther, group, Role.MEMBER);

        // attiviamo per alcuni test che richiedono stato ACTIVE
        creator.activate();
        admin.activate();
        otherMember.activate();
    }

    // ==========================================
    // COSTRUTTORE / VALIDAZIONI
    // ==========================================
    @Test
    void constructor_withNullUser_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new Membership(10L, null, group, Role.MEMBER));
    }

    @Test
    void constructor_withNullGroup_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new Membership(10L, userCreator, null, Role.MEMBER));
    }

    @Test
    void constructor_withNullRole_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new Membership(10L, userCreator, group, null));
    }

    // ==========================================
    // ROLE / ADMIN
    // ==========================================
    @Test
    void isAdmin_returnsTrueForAdmin_falseForMember() {
        assertTrue(admin.isAdmin());
        assertFalse(creator.isAdmin());
    }

    // ==========================================
    // STATO / ATTIVAZIONE / TERMINAZIONE
    // ==========================================
    @Test
    void isActive_reflectsStatus_afterActivateAndTerminate() {
        Membership m = new Membership(null, userOther, group, Role.MEMBER);
        assertFalse(m.isActive()); // default WAITING_ACCEPTANCE

        m.activate();
        assertTrue(m.isActive());

        m.terminate();
        assertFalse(m.isActive());
    }

    // ==========================================
    // BALANCE / DEBITI PENDENTI / RIMOZIONE
    // ==========================================
    @Test
    void hasPendingDebts_falseWhenNoBalance() {
        Membership m = new Membership(null, userOther, group, Role.MEMBER);
        assertFalse(m.hasPendingDebts());
    }

    @Test
    void hasPendingDebts_trueWhenBalanceNotSettled_falseWhenSettled() {
        Membership m = new Membership(null, userOther, group, Role.MEMBER);
        Balance b = new Balance(null, m);

        // Creiamo un debito
        b.increment(new BigDecimal("25.00"));
        m.setBalance(b);

        assertTrue(m.hasPendingDebts()); // Ha debito, quindi true

        // Saldiamo il debito
        b.settle();

        // Dopo settle(), il saldo è 0, quindi isSettled() è TRUE
        assertTrue(b.isSettled());

        // E di conseguenza non deve avere debiti pendenti (FALSE)
        assertFalse(m.hasPendingDebts());
    }

    @Test
    void canBeRemoved_reflectsPendingDebts() {
        Membership m = new Membership(null, userOther, group, Role.MEMBER);
        assertTrue(m.canBeRemoved()); // senza balance

        Balance b = new Balance(null, m);
        b.increment(new BigDecimal("1.00")); // crea debito
        m.setBalance(b);

        assertFalse(m.canBeRemoved()); // ha debiti, non può essere rimossa
    }

    @Test
    void setBalance_and_getBalance_wiring() {
        Membership m = new Membership(null, userOther, group, Role.MEMBER);
        Balance b = new Balance(5L, m, BigDecimal.ZERO, null);
        m.setBalance(b);
        assertSame(b, m.getBalance());
    }

    // ==========================================
    // EQUALS / HASHCODE / TOSTRING
    // ==========================================
    @Test
    void equalsAndHashCode_basedOnId_whenPresent() {
        Membership m1 = new Membership(100L, userCreator, group, Role.MEMBER);
        Membership m2 = new Membership(100L, userAdmin, group, Role.ADMIN);
        assertEquals(m1, m2);
        assertEquals(m1.hashCode(), m2.hashCode());
    }

    @Test
    void equals_whenBothIdsNull() {
        Membership m1 = new Membership(null, userCreator, group, Role.MEMBER);
        Membership m2 = new Membership(null, userAdmin, group, Role.ADMIN);
        // l'implementazione usa Objects.equals(membershipId, that.membershipId)
        // quindi due membership con id null risultano uguali
        assertEquals(m1, m2);
        assertEquals(m1.hashCode(), m2.hashCode());
    }

    @Test
    void toString_containsUserRoleAndStatus() {
        String s = creator.toString();
        assertTrue(s.contains("Creator Name"));
        assertTrue(s.contains("MEMBER"));
        assertTrue(s.contains("ACTIVE") || s.contains("WAITING_ACCEPTANCE") || s.contains("REMOVED"));
    }
}
