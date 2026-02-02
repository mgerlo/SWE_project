package com.splitmanager.structural;

import com.splitmanager.domain.accounting.Category;
import com.splitmanager.domain.accounting.Expense;
import com.splitmanager.domain.registry.*;
import com.splitmanager.exception.DomainException;
import com.splitmanager.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test strutturali (White Box) per Expense.
 * Verifica le INVARIANTI del domain model:
 * - Importo deve essere positivo
 * - Autorizzazioni (isEditableBy, canBeDeletedBy)
 * - Validazioni costruttore
 */
class ExpenseTest {

    private Group group;
    private Membership creator;
    private Membership admin;
    private Membership otherMember;

    @BeforeEach
    void setUp() {
        group = new Group(1L, "Test Group", "EUR");

        User user1 = new User(1L, "creator@test.com", "Creator", "hash1");
        User user2 = new User(2L, "admin@test.com", "Admin", "hash2");
        User user3 = new User(3L, "other@test.com", "Other", "hash3");

        creator = new Membership(1L, user1, group, Role.MEMBER);
        admin = new Membership(2L, user2, group, Role.ADMIN);
        otherMember = new Membership(3L, user3, group, Role.MEMBER);

        creator.activate();
        admin.activate();
        otherMember.activate();
    }

    // ==========================================
    // TEST VALIDAZIONI COSTRUTTORE (Invarianti)
    // ==========================================

    @Test
    void testConstructor_WithNegativeAmount_ThrowsException() {
        // Verifica che l'entità si auto-protegga
        assertThrows(
                DomainException.class,
                () -> new Expense(
                        1L, group, creator, creator,
                        new BigDecimal("-100.00"), // ← Importo negativo
                        "Invalid Expense",
                        Category.FOOD,
                        LocalDateTime.now()
                )
        );
    }

    @Test
    void testConstructor_WithZeroAmount_ThrowsException() {
        // Zero non è valido per una spesa
        assertThrows(
                DomainException.class,
                () -> new Expense(
                        group, creator, creator,
                        BigDecimal.ZERO, // ← Zero
                        "Invalid",
                        Category.OTHER,
                        LocalDateTime.now()
                )
        );
    }

    @Test
    void testConstructor_WithNullAmount_ThrowsException() {
        assertThrows(
                DomainException.class,
                () -> new Expense(
                        group, creator, creator,
                        null, // ← Null
                        "Invalid",
                        Category.OTHER,
                        LocalDateTime.now()
                )
        );
    }

    @Test
    void testConstructor_WithValidAmount_Success() {
        // Verifica che importi validi vengano accettati
        Expense expense = new Expense(
                group, creator, creator,
                new BigDecimal("100.00"),
                "Valid Expense",
                Category.FOOD,
                LocalDateTime.now()
        );

        assertNotNull(expense);
        assertEquals(new BigDecimal("100.00"), expense.getAmount());
    }

    // ==========================================
    // TEST AUTORIZZAZIONI (Business Logic)
    // ==========================================

    @Test
    void testIsEditableBy_Creator_ReturnsTrue() {
        Expense expense = createTestExpense();

        // Il creatore può modificare
        assertTrue(expense.isEditableBy(creator));
    }

    @Test
    void testIsEditableBy_Admin_ReturnsTrue() {
        Expense expense = createTestExpense();

        // L'admin può modificare
        assertTrue(expense.isEditableBy(admin));
    }

    @Test
    void testIsEditableBy_OtherMember_ReturnsFalse() {
        Expense expense = createTestExpense();

        // Altri membri non possono modificare
        assertFalse(expense.isEditableBy(otherMember));
    }

    @Test
    void testCanBeDeletedBy_Creator_ReturnsTrue() {
        Expense expense = createTestExpense();

        assertTrue(expense.canBeDeletedBy(creator));
    }

    @Test
    void testCanBeDeletedBy_Admin_ReturnsTrue() {
        Expense expense = createTestExpense();

        assertTrue(expense.canBeDeletedBy(admin));
    }

    @Test
    void testCanBeDeletedBy_OtherMember_ReturnsFalse() {
        Expense expense = createTestExpense();

        assertFalse(expense.canBeDeletedBy(otherMember));
    }

    // ==========================================
    // TEST SOFT DELETE
    // ==========================================

    @Test
    void testMarkAsDeleted_ByCreator_Success() {
        Expense expense = createTestExpense();

        expense.markAsDeleted(creator);

        assertTrue(expense.isDeleted());
    }

    @Test
    void testMarkAsDeleted_ByUnauthorized_ThrowsException() {
        Expense expense = createTestExpense();

        assertThrows(
                UnauthorizedException.class,
                () -> expense.markAsDeleted(otherMember)
        );
    }

    @Test
    void testMarkAsDeleted_Twice_ThrowsException() {
        Expense expense = createTestExpense();

        expense.markAsDeleted(creator);

        // Non può essere eliminata due volte
        assertThrows(
                IllegalStateException.class,
                () -> expense.markAsDeleted(creator)
        );
    }

    // ==========================================
    // TEST MODIFICA DETTAGLI
    // ==========================================

    @Test
    void testModifyDetails_ByCreator_Success() {
        Expense expense = createTestExpense();

        expense.modifyDetails(
                new BigDecimal("200.00"),
                "Modified Description",
                Category.TRANSPORT,
                creator
        );

        assertEquals(new BigDecimal("200.00"), expense.getAmount());
        assertEquals("Modified Description", expense.getDescription());
        assertEquals(Category.TRANSPORT, expense.getCategory());
    }

    @Test
    void testModifyDetails_ByUnauthorized_ThrowsException() {
        Expense expense = createTestExpense();

        assertThrows(
                UnauthorizedException.class,
                () -> expense.modifyDetails(
                        new BigDecimal("200.00"),
                        "Hacked",
                        Category.OTHER,
                        otherMember
                )
        );
    }

    // ==========================================
    // HELPER
    // ==========================================

    private Expense createTestExpense() {
        return new Expense(
                1L, group, creator, creator,
                new BigDecimal("100.00"),
                "Test Expense",
                Category.FOOD,
                LocalDateTime.now()
        );
    }
}
