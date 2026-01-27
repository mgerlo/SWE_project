package com.splitmanager.functional;

import com.splitmanager.businesslogic.controller.*;
import com.splitmanager.businesslogic.service.*;
import com.splitmanager.businesslogic.service.MinTransactionsStrategy;
import com.splitmanager.dao.*;
import com.splitmanager.domain.accounting.Category;
import com.splitmanager.domain.accounting.Expense;
import com.splitmanager.domain.accounting.Settlement;
import com.splitmanager.domain.registry.Group;
import com.splitmanager.domain.registry.Membership;

import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
/**
 * Test Funzionale End-to-End (Black-Box).
 *
 * FLUSSO TESTATO:
 * Create Group → Invite → Join → Approve → Add Expense → View Balances → Settle Debt
 *
 * NOTA ARCHITETTURALE:
 * I Controller dipendono dall'interfaccia Navigator, non dalla classe concreta NavigationManager (Dipendency Inversion Principle).
 * Questo permette di iniettare uno Stub nei test mantenendo il Singleton in produzione per la gestione centralizzata della UI.
 */
class E2ETest {

    // Infrastruttura
    private UserSession session;
    private StubNavigator navStub;
    private ConnectionManager connMgr;

    // Controllers (testati come Black-Box API)
    private AuthController authController;
    private GroupController groupController;
    private ExpenseController expenseController;
    private BalanceController balanceController;

    // Services (per verifiche interne)
    private UserService userService;
    private GroupService groupService;

    @BeforeEach
    void setUp() throws SQLException {
        // 1. Setup DB e Pulizia
        connMgr = ConnectionManager.getInstance();
        cleanDb();

        // 2. Setup DAO
        UserDAO userDAO = new UserDAO();
        GroupDAO groupDAO = new GroupDAO();
        MembershipDAO membershipDAO = new MembershipDAO();
        BalanceDAO balanceDAO = new BalanceDAO();
        ExpenseDAO expenseDAO = new ExpenseDAO();
        SettlementDAO settlementDAO = new SettlementDAO();

        // 3. Setup Services
        userService = new UserService(userDAO);
        groupService = new GroupService(groupDAO, membershipDAO, balanceDAO);
        ExpenseService expenseService = new ExpenseService(expenseDAO, membershipDAO, balanceDAO, groupDAO);

        // BalanceService: inizializzato con le dipendenze corrette
        BalanceService balanceService = new BalanceService(balanceDAO, membershipDAO, groupDAO, new MinTransactionsStrategy());

        SettlementService settlementService = new SettlementService(settlementDAO, membershipDAO, balanceDAO, groupDAO);

        // 4. Setup Sessione
        session = UserSession.getInstance();
        if (session.isLoggedIn()) session.logout();

        // 5. Stub UI: Implementa interfaccia Navigator
        navStub = new StubNavigator();

        // 6. Dependency Injection nei Controller
        authController = new AuthController(userService, session, navStub);
        groupController = new GroupController(groupService, session, navStub);
        expenseController = new ExpenseController(expenseService, session, navStub);
        balanceController = new BalanceController(balanceService, settlementService, session, navStub);
    }

    @AfterEach
    void tearDown() {
        if (session != null && session.isLoggedIn()) {
            session.logout();
        }
    }

    @Test
    @DisplayName("Complete Flow: Create Group → Invite → Join → Approve → Add Expense → View Balances → Settle Debt")
    void testCompleteUserFlow() {

        // ===== 1. Alice crea gruppo =====
        authController.handleSignUp("Alice", "alice@test.com", "password123");
        authController.handleLogin("alice@test.com", "password123");
        groupController.createGroup("Vacation", "EUR");
        assertFalse(navStub.hasError);

        Group group = session.getCurrentGroup();
        Long groupId = group.getGroupId();
        // Recuperiamo l'ID della Membership di Alice (necessario per le operazioni Admin)
        Long aliceMembershipId = groupService.getGroupMembers(groupId).get(0).getMembershipId();

        // L'admin genera il codice di invito
        groupController.generateNewInviteCode(aliceMembershipId);
        assertFalse(navStub.hasError, "Invite code generation should succeed");

        // Estraiamo il codice invito dal messaggio di successo
        String inviteCode = extractInviteCodeFromMessage(navStub.lastMessage);
        assertNotNull(inviteCode, "Invite code should be generated");
        System.out.println("Invite Code Generated: " + inviteCode);

        // ===== 2. Bob si unisce al gruppo =====
        authController.handleLogout();

        authController.handleSignUp("Bob", "bob@test.com", "password456");
        authController.handleLogin("bob@test.com", "password456");

        groupController.joinGroup(inviteCode);
        assertFalse(navStub.hasError, "Join group should succeed");

        // ===== 3. Alice approva Bob =====
        authController.handleLogout();
        authController.handleLogin("alice@test.com", "password123");
        groupController.openGroup(group);

        Long bobMembershipId = groupService.getGroupMembers(groupId).stream()
                .filter(m -> m.getUser().getFullName().equals("Bob"))
                .findFirst().orElseThrow().getMembershipId();

        groupController.approveMember(bobMembershipId, aliceMembershipId);
        assertFalse(navStub.hasError, "Approval should succeed");

        // ===== 4. Alice aggiunge spesa =====
        // Recuperiamo i membership ID aggiornati dopo i logout/login
        List<Membership> members = groupService.getGroupMembers(groupId);
        Long currentAliceMembershipId = members.stream()
                .filter(m -> m.getUser().getFullName().equals("Alice"))
                .findFirst().orElseThrow().getMembershipId();
        Long currentBobMembershipId = members.stream()
                .filter(m -> m.getUser().getFullName().equals("Bob"))
                .findFirst().orElseThrow().getMembershipId();

        System.out.println("Alice Membership ID: " + currentAliceMembershipId);
        System.out.println("Bob Membership ID: " + currentBobMembershipId);

        expenseController.createExpense(
                currentAliceMembershipId,
                new BigDecimal("100.00"),
                "Hotel",
                Category.ACCOMMODATION,
                List.of(currentAliceMembershipId, currentBobMembershipId)
        );
        assertFalse(navStub.hasError);

        // Assicuriamoci che il gruppo sia selezionato nella sessione
        groupController.openGroup(group);

        // ===== 5. Verifica saldi =====
        Map<Membership, BigDecimal> balances = balanceController.viewBalances();
        System.out.println("Number of balances: " + balances.size());

        // Debug: stampiamo tutti i membri e i loro saldi
        balances.forEach((membership, balance) -> {
            System.out.println("Member: " + membership.getUser().getFullName() +
                    " (ID: " + membership.getMembershipId() + ") -> Balance: " + balance);
        });

        BigDecimal aliceBalance = balances.entrySet().stream()
                .filter(e -> e.getKey().getMembershipId().equals(currentAliceMembershipId))
                .map(Map.Entry::getValue).findFirst().orElseThrow();

        BigDecimal bobBalance = balances.entrySet().stream()
                .filter(e -> e.getKey().getMembershipId().equals(currentBobMembershipId))
                .map(Map.Entry::getValue).findFirst().orElseThrow();

        assertEquals(new BigDecimal("50.00"), aliceBalance);
        assertEquals(new BigDecimal("-50.00"), bobBalance);

        // ===== 6. Bob salda il debito =====
        authController.handleLogout();
        authController.handleLogin("bob@test.com", "password456");
        groupController.openGroup(group);

        // Bob controlla i debiti ottimizzati
        List<Settlement> optimized = balanceController.viewOptimizedDebts();
        assertEquals(1, optimized.size());

        balanceController.settleDebt(currentBobMembershipId, currentAliceMembershipId, new BigDecimal("50.00"));

        // ===== 7. Alice conferma =====
        authController.handleLogout();
        authController.handleLogin("alice@test.com", "password123");
        groupController.openGroup(group);

        List<Settlement> pending = balanceController.getPendingSettlements();
        assertEquals(1, pending.size());

        balanceController.confirmSettlement(pending.get(0).getSettlementId(), currentAliceMembershipId);

        // ===== 8. Verifica Finale =====
        assertTrue(balanceController.isGroupSettled(), "Group should be fully settled");

        System.out.println("✓ Test E2E completed successfully!");
    }

    private void cleanDb() throws SQLException {
        Connection conn = connMgr.getConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SET REFERENTIAL_INTEGRITY FALSE");
            stmt.execute("TRUNCATE TABLE settlements");
            stmt.execute("TRUNCATE TABLE balances");
            stmt.execute("TRUNCATE TABLE expense_participants");
            stmt.execute("TRUNCATE TABLE expenses");
            stmt.execute("TRUNCATE TABLE memberships");
            stmt.execute("TRUNCATE TABLE groups");
            stmt.execute("TRUNCATE TABLE users");
            stmt.execute("SET REFERENTIAL_INTEGRITY TRUE");
            conn.commit();
        }
    }

    /**
     * Estrae il codice invito dal messaggio di successo.
     * Messaggio formato: "New invite code generated: XXXXXXXX"
     */
    private String extractInviteCodeFromMessage(String message) {
        if (message == null || !message.contains(":")) {
            return null;
        }
        String[] parts = message.split(":");
        return parts.length > 1 ? parts[1].trim() : null;
    }

    /**
     * Stub che implementa l'interfaccia Navigator.
     * Sostituisce NavigationManager nei test per evitare di aprire finestre Swing (Headless Testing).
     */
    static class StubNavigator implements Navigator {
        public boolean hasError = false;
        public String lastMessage = "";

        @Override
        public void showSuccess(String message) {
            this.lastMessage = message;
            this.hasError = false;
            System.out.println("[UI STUB] Success: " + message);
        }

        @Override
        public void showError(String message) {
            this.lastMessage = message;
            this.hasError = true;
            System.err.println("[UI STUB] Error: " + message);
        }

        @Override
        public boolean showConfirmation(String message) {
            System.out.println("[UI STUB] Auto-confirming: " + message);
            return true;
        }

        @Override public void navigateToLogin() { System.out.println("[UI STUB] Go Login"); }
        @Override public void navigateToRegister() { System.out.println("[UI STUB] Go Register"); }
        @Override public void navigateToHome() { System.out.println("[UI STUB] Go Home"); }
        @Override public void navigateToGroupDetails(Group group) { System.out.println("[UI STUB] Go Group: " + group.getName()); }
    }
}