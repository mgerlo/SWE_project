package com.splitmanager.integration;

import com.splitmanager.businesslogic.service.ExpenseService;
import com.splitmanager.businesslogic.service.GroupService;
import com.splitmanager.businesslogic.service.UserService;
import com.splitmanager.domain.accounting.Balance;
import com.splitmanager.domain.accounting.Category;
import com.splitmanager.domain.accounting.Expense;
import com.splitmanager.domain.registry.Group;
import com.splitmanager.domain.registry.Membership;
import com.splitmanager.domain.registry.User;
import com.splitmanager.exception.DomainException;
import com.splitmanager.exception.UnauthorizedException;

import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration Test (Grey-Box) for ExpenseService.
 *
 * Tests UC5 (Add Expense) and UC11 (Edit/Delete Expense) with REAL database.
 *
 * CRITICAL: Tests Observer Pattern - verifies that creating/modifying expenses
 * automatically updates balances through the Observer mechanism.
 *
 * NO MOCKS - Uses real DAOs and H2 database.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExpenseServiceTest_UC5_UC11 extends BaseIntegrationTest {

    // Services
    private UserService userService;
    private GroupService groupService;
    private ExpenseService expenseService;

    // Test data (created in setupTestData)
    private User alice;
    private User bob;
    private Group group;
    private Membership aliceMembership;
    private Membership bobMembership;

    @BeforeEach
    @Override
    void setUp() throws Exception {
        // Call parent setup (initializes DAOs and cleans database)
        super.setUp();

        // Initialize Services using inherited DAOs
        userService = new UserService(this.userDAO);
        groupService = new GroupService(this.groupDAO, this.membershipDAO, this.balanceDAO);
        expenseService = new ExpenseService(this.expenseDAO, this.membershipDAO, this.balanceDAO, this.groupDAO);

        // Setup test data: 2 users in 1 group
        setupTestData();
    }

    /**
     * Creates test data: Alice and Bob in a group called "Vacation"
     */
    private void setupTestData() {
        // Create users
        alice = userService.signUp("alice@test.com", "password123", "Alice");
        bob = userService.signUp("bob@test.com", "password456", "Bob");

        // Alice creates group
        group = groupService.createGroup(alice.getUserId(), "Vacation", "EUR");

        // Get Alice's membership (she's the creator/admin)
        List<Membership> members = groupService.getGroupMembers(group.getGroupId());
        aliceMembership = members.stream()
                .filter(m -> m.getUser().getUserId().equals(alice.getUserId()))
                .findFirst().orElseThrow();

        // Bob joins and gets approved
        groupService.joinByCode(bob.getUserId(), group.getInviteCode());

        // Get Bob's membership
        members = groupService.getGroupMembers(group.getGroupId());
        bobMembership = members.stream()
                .filter(m -> m.getUser().getUserId().equals(bob.getUserId()))
                .findFirst().orElseThrow();

        // Approve Bob
        groupService.approveMember(bobMembership.getMembershipId(), aliceMembership.getMembershipId());

        // Reload memberships to get updated state
        aliceMembership = membershipDAO.findById(aliceMembership.getMembershipId()).orElseThrow();
        bobMembership = membershipDAO.findById(bobMembership.getMembershipId()).orElseThrow();
    }

    // ==========================================
    // UC5 - ADD NEW EXPENSE
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("UC5: Add expense saves to database correctly")
    void UC5_addExpense_savesToDatabase() {
        // Act
        Expense expense = expenseService.addExpense(
                group.getGroupId(),
                aliceMembership.getMembershipId(), // Alice pays
                new BigDecimal("100.00"),
                "Hotel",
                Category.ACCOMMODATION,
                List.of(aliceMembership.getMembershipId(), bobMembership.getMembershipId())
        );

        // Assert
        assertNotNull(expense);
        assertNotNull(expense.getExpenseId(), "Expense ID should be generated");
        assertEquals("Hotel", expense.getDescription());
        assertEquals(new BigDecimal("100.00"), expense.getAmount());
        assertEquals(Category.ACCOMMODATION, expense.getCategory());

        // Verify it's in database
        Expense fromDB = expenseDAO.findById(expense.getExpenseId()).orElse(null);
        assertNotNull(fromDB, "Expense should be persisted in database");
        assertEquals(expense.getExpenseId(), fromDB.getExpenseId());
    }

    @Test
    @Order(2)
    @DisplayName("UC5: Add expense automatically updates balances (Observer Pattern)")
    void UC5_addExpense_updatesBalancesAutomatically() {
        // CRITICAL TEST: Verifies Observer Pattern works!

        // Arrange - verify initial balances are zero
        Balance aliceBalanceBefore = balanceDAO.findByMembershipId(aliceMembership.getMembershipId()).orElseThrow();
        Balance bobBalanceBefore = balanceDAO.findByMembershipId(bobMembership.getMembershipId()).orElseThrow();
        assertEquals(BigDecimal.ZERO.setScale(2), aliceBalanceBefore.getAmount());
        assertEquals(BigDecimal.ZERO.setScale(2), bobBalanceBefore.getAmount());

        // Act - Alice pays 100 EUR for both
        expenseService.addExpense(
                group.getGroupId(),
                aliceMembership.getMembershipId(),
                new BigDecimal("100.00"),
                "Dinner",
                Category.FOOD,
                List.of(aliceMembership.getMembershipId(), bobMembership.getMembershipId())
        );

        // Assert - balances updated automatically
        Balance aliceBalanceAfter = balanceDAO.findByMembershipId(aliceMembership.getMembershipId()).orElseThrow();
        Balance bobBalanceAfter = balanceDAO.findByMembershipId(bobMembership.getMembershipId()).orElseThrow();

        // Alice paid 100 but owes only 50 -> gets back 50
        assertEquals(new BigDecimal("50.00"), aliceBalanceAfter.getAmount(),
                "Alice should have +50 credit (paid 100, owes 50)");

        // Bob owes 50
        assertEquals(new BigDecimal("-50.00"), bobBalanceAfter.getAmount(),
                "Bob should have -50 debt");

        // Verify sum is zero (closed system)
        BigDecimal total = aliceBalanceAfter.getAmount().add(bobBalanceAfter.getAmount());
        assertEquals(BigDecimal.ZERO.setScale(2), total.setScale(2),
                "Sum of all balances should be zero");
    }

    @Test
    @Order(3)
    @DisplayName("UC5: Add expense with multiple participants splits correctly")
    void UC5_addExpense_splitsAmountCorrectly() {
        // Create third user
        User charlie = userService.signUp("charlie@test.com", "password789", "Charlie");
        groupService.joinByCode(charlie.getUserId(), group.getInviteCode());

        List<Membership> members = groupService.getGroupMembers(group.getGroupId());
        Membership charlieMembership = members.stream()
                .filter(m -> m.getUser().getUserId().equals(charlie.getUserId()))
                .findFirst().orElseThrow();

        groupService.approveMember(charlieMembership.getMembershipId(), aliceMembership.getMembershipId());
        charlieMembership = membershipDAO.findById(charlieMembership.getMembershipId()).orElseThrow();

        // Alice pays 90 EUR for 3 people
        expenseService.addExpense(
                group.getGroupId(),
                aliceMembership.getMembershipId(),
                new BigDecimal("90.00"),
                "Taxi",
                Category.TRANSPORT,
                List.of(aliceMembership.getMembershipId(), bobMembership.getMembershipId(), charlieMembership.getMembershipId())
        );

        // Each owes 30 EUR
        Balance aliceBalance = balanceDAO.findByMembershipId(aliceMembership.getMembershipId()).orElseThrow();
        Balance bobBalance = balanceDAO.findByMembershipId(bobMembership.getMembershipId()).orElseThrow();
        Balance charlieBalance = balanceDAO.findByMembershipId(charlieMembership.getMembershipId()).orElseThrow();

        assertEquals(new BigDecimal("60.00"), aliceBalance.getAmount(),
                "Alice should have +60 (paid 90, owes 30)");
        assertEquals(new BigDecimal("-30.00"), bobBalance.getAmount());
        assertEquals(new BigDecimal("-30.00"), charlieBalance.getAmount());
    }

    @Test
    @Order(4)
    @DisplayName("UC5 Alternative 4a: Invalid amount throws exception")
    void UC5_addExpense_withInvalidAmount_throwsException() {
        // Negative amount
        assertThrows(DomainException.class, () ->
                expenseService.addExpense(
                        group.getGroupId(),
                        aliceMembership.getMembershipId(),
                        new BigDecimal("-10.00"),
                        "Invalid",
                        Category.OTHER,
                        List.of(aliceMembership.getMembershipId())
                )
        );

        // Zero amount
        assertThrows(DomainException.class, () ->
                expenseService.addExpense(
                        group.getGroupId(),
                        aliceMembership.getMembershipId(),
                        BigDecimal.ZERO,
                        "Invalid",
                        Category.OTHER,
                        List.of(aliceMembership.getMembershipId())
                )
        );
    }

    @Test
    @Order(5)
    @DisplayName("UC5 Alternative 5a: Empty description throws exception")
    void UC5_addExpense_withEmptyDescription_throwsException() {
        assertThrows(DomainException.class, () ->
                expenseService.addExpense(
                        group.getGroupId(),
                        aliceMembership.getMembershipId(),
                        new BigDecimal("50.00"),
                        "", // Empty description
                        Category.FOOD,
                        List.of(aliceMembership.getMembershipId())
                )
        );
    }

    @Test
    @Order(6)
    @DisplayName("UC5 Alternative 5a: No participants throws exception")
    void UC5_addExpense_withNoParticipants_throwsException() {
        assertThrows(DomainException.class, () ->
                expenseService.addExpense(
                        group.getGroupId(),
                        aliceMembership.getMembershipId(),
                        new BigDecimal("50.00"),
                        "Test",
                        Category.FOOD,
                        List.of() // No participants
                )
        );
    }

    // ==========================================
    // UC11 - EDIT EXPENSE
    // ==========================================

    @Test
    @Order(7)
    @DisplayName("UC11: Edit expense updates database")
    void UC11_editExpense_updatesDatabase() {
        // Arrange - create expense
        Expense expense = expenseService.addExpense(
                group.getGroupId(),
                aliceMembership.getMembershipId(),
                new BigDecimal("100.00"),
                "Original Description",
                Category.FOOD,
                List.of(aliceMembership.getMembershipId(), bobMembership.getMembershipId())
        );

        // Act - edit description
        expenseService.editExpense(
                expense.getExpenseId(),
                aliceMembership.getMembershipId(),
                null, // Keep same amount
                "Modified Description",
                null  // Keep same category
        );

        // Assert
        Expense updated = expenseDAO.findById(expense.getExpenseId()).orElseThrow();
        assertEquals("Modified Description", updated.getDescription());
        assertEquals(new BigDecimal("100.00"), updated.getAmount());
    }

    @Test
    @Order(8)
    @DisplayName("UC11: Edit expense amount recalculates balances (Observer Pattern)")
    void UC11_editExpense_recalculatesBalances() {
        // CRITICAL TEST: Verifies Observer Pattern on modification!

        // Arrange - create expense (100 EUR)
        Expense expense = expenseService.addExpense(
                group.getGroupId(),
                aliceMembership.getMembershipId(),
                new BigDecimal("100.00"),
                "Hotel",
                Category.ACCOMMODATION,
                List.of(aliceMembership.getMembershipId(), bobMembership.getMembershipId())
        );

        // Verify initial balances
        Balance aliceBalance1 = balanceDAO.findByMembershipId(aliceMembership.getMembershipId()).orElseThrow();
        Balance bobBalance1 = balanceDAO.findByMembershipId(bobMembership.getMembershipId()).orElseThrow();
        assertEquals(new BigDecimal("50.00"), aliceBalance1.getAmount());
        assertEquals(new BigDecimal("-50.00"), bobBalance1.getAmount());

        // Act - change amount to 200 EUR
        expenseService.editExpense(
                expense.getExpenseId(),
                aliceMembership.getMembershipId(),
                new BigDecimal("200.00"), // Changed!
                null,
                null
        );

        // Assert - balances updated automatically
        Balance aliceBalance2 = balanceDAO.findByMembershipId(aliceMembership.getMembershipId()).orElseThrow();
        Balance bobBalance2 = balanceDAO.findByMembershipId(bobMembership.getMembershipId()).orElseThrow();

        assertEquals(new BigDecimal("100.00"), aliceBalance2.getAmount(),
                "Alice should have +100 (paid 200, owes 100)");
        assertEquals(new BigDecimal("-100.00"), bobBalance2.getAmount(),
                "Bob should owe 100");
    }

    @Test
    @Order(9)
    @DisplayName("UC11 Alternative 2a: Non-creator cannot edit expense")
    void UC11_editExpense_byNonCreator_throwsException() {
        // Arrange - Alice creates expense
        Expense expense = expenseService.addExpense(
                group.getGroupId(),
                aliceMembership.getMembershipId(),
                new BigDecimal("100.00"),
                "Alice's Expense",
                Category.FOOD,
                List.of(aliceMembership.getMembershipId(), bobMembership.getMembershipId())
        );

        // Act & Assert - Bob tries to edit
        assertThrows(UnauthorizedException.class, () ->
                expenseService.editExpense(
                        expense.getExpenseId(),
                        bobMembership.getMembershipId(), // Bob is not creator
                        new BigDecimal("200.00"),
                        "Hacked",
                        Category.OTHER
                )
        );
    }

    // ==========================================
    // UC11 - DELETE EXPENSE
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("UC11: Delete expense reverses balances (Observer Pattern)")
    void UC11_deleteExpense_reversesBalances() {
        // CRITICAL TEST: Verifies Observer Pattern on deletion!

        // Arrange - create expense
        Expense expense = expenseService.addExpense(
                group.getGroupId(),
                aliceMembership.getMembershipId(),
                new BigDecimal("100.00"),
                "To Delete",
                Category.FOOD,
                List.of(aliceMembership.getMembershipId(), bobMembership.getMembershipId())
        );

        // Verify balances changed
        Balance aliceBalance1 = balanceDAO.findByMembershipId(aliceMembership.getMembershipId()).orElseThrow();
        Balance bobBalance1 = balanceDAO.findByMembershipId(bobMembership.getMembershipId()).orElseThrow();
        assertEquals(new BigDecimal("50.00"), aliceBalance1.getAmount());
        assertEquals(new BigDecimal("-50.00"), bobBalance1.getAmount());

        // Act - delete expense
        expenseService.deleteExpense(expense.getExpenseId(), aliceMembership.getMembershipId());

        // Assert - balances reversed to zero
        Balance aliceBalance2 = balanceDAO.findByMembershipId(aliceMembership.getMembershipId()).orElseThrow();
        Balance bobBalance2 = balanceDAO.findByMembershipId(bobMembership.getMembershipId()).orElseThrow();

        assertEquals(BigDecimal.ZERO.setScale(2), aliceBalance2.getAmount(),
                "Alice balance should return to zero after deletion");
        assertEquals(BigDecimal.ZERO.setScale(2), bobBalance2.getAmount(),
                "Bob balance should return to zero after deletion");

        // Verify expense is soft-deleted
        Expense deleted = expenseDAO.findById(expense.getExpenseId()).orElseThrow();
        assertTrue(deleted.isDeleted(), "Expense should be marked as deleted");
    }

    @Test
    @Order(11)
    @DisplayName("UC11 Alternative 2b: Non-creator cannot delete expense")
    void UC11_deleteExpense_byNonCreator_throwsException() {
        // Arrange - Alice creates expense
        Expense expense = expenseService.addExpense(
                group.getGroupId(),
                aliceMembership.getMembershipId(),
                new BigDecimal("100.00"),
                "Alice's Expense",
                Category.FOOD,
                List.of(aliceMembership.getMembershipId(), bobMembership.getMembershipId())
        );

        // Act & Assert - Bob tries to delete
        assertThrows(UnauthorizedException.class, () ->
                expenseService.deleteExpense(
                        expense.getExpenseId(),
                        bobMembership.getMembershipId() // Bob is not creator
                )
        );
    }

    // ==========================================
    // INTEGRATION TESTS
    // ==========================================

    @Test
    @Order(12)
    @DisplayName("Integration: Multiple expenses accumulate balances correctly")
    void integration_multipleExpenses_accumulateBalances() {
        // Expense 1: Alice pays 60
        expenseService.addExpense(
                group.getGroupId(),
                aliceMembership.getMembershipId(),
                new BigDecimal("60.00"),
                "Lunch",
                Category.FOOD,
                List.of(aliceMembership.getMembershipId(), bobMembership.getMembershipId())
        );

        // Expense 2: Bob pays 40
        expenseService.addExpense(
                group.getGroupId(),
                bobMembership.getMembershipId(),
                new BigDecimal("40.00"),
                "Coffee",
                Category.FOOD,
                List.of(aliceMembership.getMembershipId(), bobMembership.getMembershipId())
        );

        // Check balances
        Balance aliceBalance = balanceDAO.findByMembershipId(aliceMembership.getMembershipId()).orElseThrow();
        Balance bobBalance = balanceDAO.findByMembershipId(bobMembership.getMembershipId()).orElseThrow();

        // Alice: paid 60, owes 50 (30+20) -> +10
        // Bob: paid 40, owes 50 (30+20) -> -10
        assertEquals(new BigDecimal("10.00"), aliceBalance.getAmount());
        assertEquals(new BigDecimal("-10.00"), bobBalance.getAmount());
    }

}
