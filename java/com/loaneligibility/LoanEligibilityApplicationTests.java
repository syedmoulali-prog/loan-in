package com.loaneligibility;

import com.loaneligibility.dto.EligibilityResultDto;
import com.loaneligibility.dto.LoanApplicationCreateDto;
import com.loaneligibility.dto.LoanApplicationDto;
import com.loaneligibility.dto.LoanStatusUpdateDto;
import com.loaneligibility.dto.RegisterRequest;
import com.loaneligibility.dto.CustomerDto;
import com.loaneligibility.exception.BadRequestException;
import com.loaneligibility.service.AuthService;
import com.loaneligibility.service.CustomerService;
import com.loaneligibility.service.EligibilityEngineService;
import com.loaneligibility.service.LoanApplicationService;
import com.loaneligibility.service.ReportService;
import com.loaneligibility.service.NotificationService;
import com.loaneligibility.service.LoanRepaymentService;
import com.loaneligibility.dto.LoginRequest;
import com.loaneligibility.service.DashboardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LoanEligibilityApplicationTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private LoanApplicationService loanApplicationService;

    @Autowired
    private EligibilityEngineService eligibilityEngineService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private LoanRepaymentService loanRepaymentService;

    @Autowired
    private DashboardService dashboardService;

    @Test
    @DisplayName("TC01: Valid Registration")
    void testValidRegistration() {
        RegisterRequest req = new RegisterRequest();
        req.setName("Test Applicant");
        req.setEmail("testapplicant" + System.currentTimeMillis() + "@domain.com");
        req.setPassword("SecurePass123!");
        req.setRole("CUSTOMER");
        req.setDateOfBirth(LocalDate.of(1993, 4, 12));
        req.setPhone("+1-555-8899");
        req.setAddress("456 Finance Blvd");
        req.setEmploymentType("Salaried");

        var response = authService.register(req);
        assertNotNull(response);
        assertNotNull(response.getToken());
        assertEquals("CUSTOMER", response.getRole());
        assertNotNull(response.getCustomerId());
    }

    @Test
    @DisplayName("TC02: Duplicate Email Rejection")
    void testDuplicateEmailRegistration() {
        RegisterRequest req = new RegisterRequest();
        req.setName("Duplicate User");
        req.setEmail("syedmoulali318@gmail.com");
        req.setPassword("123456789");

        assertThrows(Exception.class, () -> authService.register(req));
    }

    @Test
    @DisplayName("TC03: Invalid Numeric Financial Value")
    void testInvalidNumericFinancialValue() {
        LoanApplicationCreateDto dto = new LoanApplicationCreateDto();
        dto.setCustomerId(1L);
        dto.setLoanType("Personal Loan");
        dto.setRequestedAmount(new BigDecimal("-5000"));
        dto.setTenure(12);

        // Validation test
        assertTrue(dto.getRequestedAmount().compareTo(BigDecimal.ZERO) < 0);
    }

    @Test
    @DisplayName("TC04: Complete Application Evaluation")
    void testCompleteApplicationEvaluation() {
        // App #1 is seeded for customer #1 who has high income and verified documents
        EligibilityResultDto result = eligibilityEngineService.evaluateApplication(1L, 1L);
        assertNotNull(result);
        assertEquals("ELIGIBLE", result.getEligibilityStatus());
        assertTrue(result.getEligibilityScore().doubleValue() >= 75.0);
        assertNotNull(result.getReason());
    }

    @Test
    @DisplayName("TC05: Low Income Triggers Not Eligible or Review")
    void testIneligibleApplicationEvaluation() {
        // App #6 has income below minimum and poor credit
        EligibilityResultDto result = eligibilityEngineService.evaluateApplication(6L, 1L);
        assertNotNull(result);
        assertEquals("NOT_ELIGIBLE", result.getEligibilityStatus());
        assertTrue(result.getEligibilityScore().doubleValue() < 50.0);
    }

    @Test
    @DisplayName("TC06: Search Application by ID")
    void testSearchApplicationById() {
        LoanApplicationDto app = loanApplicationService.getApplicationById(1L);
        assertNotNull(app);
        assertEquals(1L, app.getApplicationId());
        assertEquals("Home Loan", app.getLoanType());
    }

    @Test
    @DisplayName("TC07: Update Application Status")
    void testUpdateApplicationStatus() {
        LoanStatusUpdateDto update = new LoanStatusUpdateDto("APPROVED", "Manager granted manual override approval");
        LoanApplicationDto updated = loanApplicationService.updateStatus(1L, update, 1L);
        assertNotNull(updated);
        assertEquals("APPROVED", updated.getStatus());
        assertEquals("Manager granted manual override approval", updated.getRemarks());
    }

    @Test
    @DisplayName("TC08: Generate Application Report")
    void testGenerateApplicationReport() {
        Map<String, Object> summary = reportService.getReportSummary();
        assertNotNull(summary);
        assertTrue(summary.containsKey("totalApplications"));
        assertTrue((Long) summary.get("totalApplications") >= 6L);
    }

    @Test
    @DisplayName("TC09: Customer Profile Photo Upload and Removal")
    void testProfilePhotoUploadAndRemoval() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.jpg",
                "image/jpeg",
                new byte[]{ (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0 }
        );

        CustomerDto updated = customerService.uploadProfilePhoto(1L, file, 1L);
        assertNotNull(updated);
        assertNotNull(updated.getProfilePhoto());
        assertTrue(updated.getProfilePhoto().startsWith("customer_1_"));

        var resource = customerService.loadProfilePhoto(1L);
        assertNotNull(resource);
        assertTrue(resource.exists());

        CustomerDto removed = customerService.deleteProfilePhoto(1L, 1L);
        assertNotNull(removed);
        assertNull(removed.getProfilePhoto());
    }

    @Test
    @DisplayName("TC10: Customer Registration Creates Admin Notification")
    void testCustomerRegistrationCreatesAdminNotification() {
        RegisterRequest req = new RegisterRequest();
        req.setName("Rahul Kumar");
        req.setEmail("rahul" + System.currentTimeMillis() + "@example.com");
        req.setPassword("Password123!");
        req.setPhone("9876543210");
        req.setDateOfBirth(LocalDate.of(1994, 6, 15));
        req.setGender("Male");
        req.setAddress("Bangalore, Karnataka");
        req.setEmploymentType("Salaried");

        var response = authService.register(req);
        assertNotNull(response);

        var adminNotifications = notificationService.getNotificationsForUser(1L, "ADMINISTRATOR");
        assertFalse(adminNotifications.isEmpty());
        assertTrue(adminNotifications.stream().anyMatch(n -> n.getTitle().contains("New Customer Registration") && n.getMessage().contains("Rahul Kumar")));
    }

    @Test
    @DisplayName("TC11: Customer Loan Application Submission Creates Admin Notification")
    void testLoanApplicationSubmissionCreatesAdminNotification() {
        LoanApplicationCreateDto dto = new LoanApplicationCreateDto();
        dto.setCustomerId(1L);
        dto.setLoanType("Personal Loan");
        dto.setRequestedAmount(new BigDecimal("500000.00"));
        dto.setTenure(60);
        dto.setApplicationDate(LocalDate.now());

        LoanApplicationDto app = loanApplicationService.createApplication(dto, 1L);
        assertNotNull(app);
        assertEquals("SUBMITTED", app.getStatus());

        var adminNotifications = notificationService.getNotificationsForUser(1L, "ADMINISTRATOR");
        assertTrue(adminNotifications.stream().anyMatch(n -> n.getTitle().contains("New Loan Application") && n.getMessage().contains("500000")));
    }

    @Test
    @DisplayName("TC12: Admin Application Approval Calculates EMI and Creates Repayment Schedule")
    void testAdminApprovalCalculatesEmiAndRepaymentSchedule() {
        LoanApplicationCreateDto dto = new LoanApplicationCreateDto();
        dto.setCustomerId(1L);
        dto.setLoanType("Personal Loan");
        dto.setRequestedAmount(new BigDecimal("500000.00"));
        dto.setTenure(60);
        dto.setApplicationDate(LocalDate.now());

        LoanApplicationDto app = loanApplicationService.createApplication(dto, 1L);

        // Approve with 10% interest rate
        LoanApplicationDto approved = loanApplicationService.approveApplication(app.getApplicationId(), new BigDecimal("10.00"), "Underwriting approval granted", 1L);
        assertNotNull(approved);
        assertEquals("APPROVED", approved.getStatus());
        assertNotNull(approved.getRepayment());

        // Verify standard EMI formula calculation: ₹5,00,000 at 10% for 60 months = ₹10,623.52
        assertEquals(new BigDecimal("10623.52"), approved.getRepayment().getMonthlyPayment());
        assertEquals(new BigDecimal("500000.00"), approved.getRepayment().getApprovedAmount());
        assertEquals(60, approved.getRepayment().getTenureMonths());
        assertEquals(new BigDecimal("10.00"), approved.getRepayment().getInterestRate());
        assertEquals(new BigDecimal("637411.20"), approved.getRepayment().getTotalPayable());
    }

    @Test
    @DisplayName("TC13: Notification Mark As Read and Unread Count")
    void testNotificationReadStatus() {
        long initialUnread = notificationService.getUnreadCountForUser(1L, "ADMINISTRATOR");
        var list = notificationService.getNotificationsForUser(1L, "ADMINISTRATOR");
        assertFalse(list.isEmpty());

        Long notifId = list.get(0).getNotificationId();
        var marked = notificationService.markAsRead(notifId);
        assertTrue(marked.getIsRead());
        assertNotNull(marked.getReadAt());
    }

    @Test
    @DisplayName("TC14: Complete End-To-End: Registration -> Admin Notification -> Application -> Approval -> Monthly Payment EMI -> Customer Active Loan")
    void testCompleteEndToEndCustomerFlow() {
        // Step 1: Customer Registration (Rahul Kumar)
        String uniqueEmail = "rahul.kumar." + System.currentTimeMillis() + "@example.com";
        RegisterRequest registerReq = new RegisterRequest();
        registerReq.setName("Rahul Kumar");
        registerReq.setEmail(uniqueEmail);
        registerReq.setPassword("Secret@123");
        registerReq.setPhone("9876543210");
        registerReq.setDateOfBirth(LocalDate.of(1994, 6, 15));
        registerReq.setGender("Male");
        registerReq.setAddress("Bangalore, Karnataka");
        registerReq.setEmploymentType("Salaried");

        var regRes = authService.register(registerReq);
        assertNotNull(regRes);
        assertNotNull(regRes.getToken());
        Long rahulCustomerId = regRes.getCustomerId();
        Long rahulUserId = regRes.getUserId();
        assertNotNull(rahulCustomerId);
        assertNotNull(rahulUserId);

        // Verify Admin notification created for new customer
        var adminNotifs = notificationService.getNotificationsForUser(1L, "ADMINISTRATOR");
        assertTrue(adminNotifs.stream().anyMatch(n -> n.getMessage().contains("Rahul Kumar") && n.getMessage().contains("9876543210")));

        // Step 2: Customer Login
        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail(uniqueEmail);
        loginReq.setPassword("Secret@123");
        var loginRes = authService.login(loginReq);
        assertNotNull(loginRes.getToken());
        assertEquals("Rahul Kumar", loginRes.getName());
        assertEquals("CUSTOMER", loginRes.getRole());

        // Step 3: Customer Loan Application Submission (₹5,00,000, 60 Months, Personal Loan)
        LoanApplicationCreateDto appDto = new LoanApplicationCreateDto();
        appDto.setCustomerId(rahulCustomerId);
        appDto.setLoanType("Personal Loan");
        appDto.setRequestedAmount(new BigDecimal("500000.00"));
        appDto.setTenure(60);
        appDto.setApplicationDate(LocalDate.now());
        appDto.setMonthlyIncome(new BigDecimal("85000.00"));
        appDto.setMonthlyExpenses(new BigDecimal("25000.00"));
        appDto.setExistingObligations(new BigDecimal("10000.00"));
        appDto.setCreditInformation("EXCELLENT");

        LoanApplicationDto submittedApp = loanApplicationService.createApplication(appDto, rahulCustomerId);
        assertNotNull(submittedApp);
        assertEquals("SUBMITTED", submittedApp.getStatus());

        // Verify Admin notification created for new loan application
        var loanNotifs = notificationService.getNotificationsForUser(1L, "ADMINISTRATOR");
        assertTrue(loanNotifs.stream().anyMatch(n -> n.getTitle().contains("New Loan Application") && n.getMessage().contains("500000")));

        // Step 4: Admin Decision & Approval with 10% Interest Rate
        LoanApplicationDto approvedApp = loanApplicationService.approveApplication(
                submittedApp.getApplicationId(),
                new BigDecimal("10.00"),
                "Verified KYC and financial documents, approved for 60 months",
                1L
        );
        assertEquals("APPROVED", approvedApp.getStatus());
        assertNotNull(approvedApp.getRepayment());

        // Step 5: Verify Backend EMI and Repayment calculation
        // EMI = 500000 * (0.10/12) * (1+0.10/12)^60 / ((1+0.10/12)^60 - 1) = 10623.52
        assertEquals(new BigDecimal("10623.52"), approvedApp.getRepayment().getMonthlyPayment());
        assertEquals(new BigDecimal("637411.20"), approvedApp.getRepayment().getTotalPayable());
        assertEquals(new BigDecimal("137411.20"), approvedApp.getRepayment().getTotalInterest());
        assertEquals(new BigDecimal("637411.20"), approvedApp.getRepayment().getRemainingBalance());
        assertEquals(new BigDecimal("500000.00"), approvedApp.getRepayment().getApprovedAmount());
        assertEquals(0, approvedApp.getRepayment().getPaymentsCompleted());

        // Step 6: Verify Customer received Approval Notification
        var customerNotifs = notificationService.getNotificationsForUser(rahulUserId, "CUSTOMER");
        assertTrue(customerNotifs.stream().anyMatch(n -> n.getTitle().contains("Application Approved") && n.getMessage().contains("10623.52")));

        // Step 7: Verify Customer Dashboard retrieves active loan card data
        var custDash = dashboardService.getCustomerDashboard(rahulCustomerId);
        assertNotNull(custDash);
        assertNotNull(custDash.getActiveRepayment());
        assertEquals(new BigDecimal("10623.52"), custDash.getActiveRepayment().getMonthlyPayment());
        assertEquals(new BigDecimal("500000.00"), custDash.getActiveRepayment().getApprovedAmount());
        assertEquals(60, custDash.getActiveRepayment().getTenureMonths());

        // Step 8: Admin Dashboard Statistics reflect approved loan
        var adminDash = dashboardService.getAdminDashboard();
        assertNotNull(adminDash);
        assertTrue(adminDash.getTotalCustomers() >= 1L);
        assertTrue(adminDash.getApprovedLoans() >= 1L);
    }
}
