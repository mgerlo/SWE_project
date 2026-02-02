package com.splitmanager.structural;

import com.splitmanager.domain.accounting.Balance;
import com.splitmanager.domain.registry.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Test strutturali (White Box) per Balance.
 * Verificano le seguenti invarianti di dominio:
 * - le precondizioni del costruttore (es. membership non nulla);
 * - il comportamento delle operazioni che modificano il saldo (increment, decrement, apply, settle);
 * - l'uguaglianza/hashCode in base a id o membership;
 * - l'inizializzazione dei valori di default (amount, lastUpdated).
 */

class BalanceTest {

    private Group group;
    private User userA;
    private User userB;
    private Membership memberA;
    private Membership memberB;

    /**
     * Impostazioni comuni per ogni test:
     * crea un gruppo e due membership attivate.
     */
    @BeforeEach
    void setUp() {
        group = new Group(1L, "Test Group", "EUR");
        userA = new User(1L, "a@test.com", "A", "h1");
        userB = new User(2L, "b@test.com", "B", "h2");

        memberA = new Membership(1L, userA, group, Role.MEMBER);
        memberB = new Membership(2L, userB, group, Role.MEMBER);

        memberA.activate();
        memberB.activate();
    }

    /**
     * Il costruttore richiede una membership non nulla:
     * se viene passata null deve esserci una NullPointerException.
     */
    @Test
    void constructor_shouldRequireMembership() {
        assertThrows(NullPointerException.class, () -> new Balance(1L, null));
    }

    /**
     * Il costruttore di default (id null) deve inizializzare il saldo a zero
     * e impostare lastUpdated.
     */
    @Test
    void defaultConstructor_initializesZeroBalance() {
        Balance b = new Balance(null, memberA);
        assertNotNull(b);
        assertEquals(BigDecimal.ZERO.setScale(2), b.getAmount());
        assertNotNull(b.getLastUpdated());
    }

    /**
     * Se il costruttore completo riceve netBalance null,
     * il valore di netBalance deve defaultare a zero.
     */
    @Test
    void fullConstructor_withNullNetBalance_defaultsToZero() {
        Balance b = new Balance(5L, memberA, null, null);
        assertEquals(BigDecimal.ZERO.setScale(2), b.getNetBalance());
        assertNotNull(b.getLastUpdated());
    }

    /**
     * increment con valore positivo deve aumentare l'amount e aggiornare lastUpdated.
     */
    @Test
    void increment_withPositive_increasesAmount() {
        Balance b = new Balance(null, memberA);
        b.increment(new BigDecimal("10.00"));
        assertEquals(new BigDecimal("10.00"), b.getAmount());
        assertNotNull(b.getLastUpdated());
    }

    /**
     * increment con input non valido (null, zero, negativo) genera IllegalArgumentException.
     */
    @Test
    void increment_withInvalid_throwsException() {
        Balance b = new Balance(null, memberA);
        assertThrows(IllegalArgumentException.class, () -> b.increment(null));
        assertThrows(IllegalArgumentException.class, () -> b.increment(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> b.increment(new BigDecimal("-1.00")));
    }

    /**
     * decrement corretto dopo un incremento deve ridurre l'amount di conseguenza.
     */
    @Test
    void decrement_withPositive_decreasesAmount() {
        Balance b = new Balance(null, memberA);
        b.increment(new BigDecimal("50.00"));
        b.decrement(new BigDecimal("20.00"));
        assertEquals(new BigDecimal("30.00"), b.getAmount());
    }

    /**
     * decrement con input non valido (null, zero, negativo) genera IllegalArgumentException.
     */
    @Test
    void decrement_withInvalid_throwsException() {
        Balance b = new Balance(null, memberA);
        assertThrows(IllegalArgumentException.class, () -> b.decrement(null));
        assertThrows(IllegalArgumentException.class, () -> b.decrement(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> b.decrement(new BigDecimal("-5.00")));
    }

    /**
     * apply con null o zero non modifica il saldo.
     */
    @Test
    void apply_withNullOrZero_noChange() {
        Balance b = new Balance(null, memberA);
        b.apply(null);
        assertEquals(BigDecimal.ZERO.setScale(2), b.getAmount());
        b.apply(BigDecimal.ZERO);
        assertEquals(BigDecimal.ZERO.setScale(2), b.getAmount());
    }

    /**
     * apply accetta valori positivi e negativi e aggiorna l'amount di conseguenza.
     */
    @Test
    void apply_withPositiveAndNegative_changesAmount() {
        Balance b = new Balance(null, memberA);
        b.apply(new BigDecimal("15.50"));
        assertEquals(new BigDecimal("15.50"), b.getAmount());
        b.apply(new BigDecimal("-5.25"));
        assertEquals(new BigDecimal("10.25"), b.getAmount());
    }

    /**
     * settle azzera il saldo e imposta lo stato a "settled".
     */
    @Test
    void settle_resetsToZero_andIsSettled() {
        Balance b = new Balance(null, memberA);
        b.increment(new BigDecimal("9.99"));
        assertFalse(b.isSettled());
        b.settle();
        assertTrue(b.isSettled());
        assertEquals(BigDecimal.ZERO.setScale(2), b.getAmount());
    }

    /**
     * equals/hashCode: quando l'id è presente, l'uguaglianza si basa sull'id.
     */
    @Test
    void equals_and_hashCode_byId_whenPresent() {
        Balance b1 = new Balance(100L, memberA, BigDecimal.ZERO, LocalDateTime.now());
        Balance b2 = new Balance(100L, memberB, new BigDecimal("5.00"), LocalDateTime.now());
        assertEquals(b1, b2);
        assertEquals(b1.hashCode(), b2.hashCode());
    }

    /**
     * equals/hashCode: quando l'id è null, l'uguaglianza si basa sulla membership.
     */
    @Test
    void equals_byMembership_whenIdNull() {
        Balance b1 = new Balance(null, memberA);
        Balance b2 = new Balance(null, memberA);
        assertEquals(b1, b2);
        assertEquals(b1.hashCode(), b2.hashCode());
    }

    /**
     * Oggetti con membership diverse e senza id non devono essere uguali.
     */
    @Test
    void notEquals_whenDifferentMembershipAndNoId() {
        Balance b1 = new Balance(null, memberA);
        Balance b2 = new Balance(null, memberB);
        assertNotEquals(b1, b2);
    }
}
