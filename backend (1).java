import java.time.LocalDateTime;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.UUID;

public class backend {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.println("========================================");
        System.out.println(" URBAN INFRASTRUCTURE PRIORITY ENGINE");
        System.out.println("========================================");
        System.out.println("You do not need to know GPS coordinates.");
        System.out.println("Enter an address and landmark, and the program can create a Google Maps search link.\n");

        String userId = readRequiredText(sc, "Enter your user ID: ");
        String description = readRequiredText(sc, "Describe the infrastructure problem: ");
        String location = readRequiredText(sc,
                "Enter problem location (area/colony, city, state, PIN code): ");
        String landmark = readRequiredText(sc, "Enter a nearby landmark (or a nearby shop/building): ");

        System.out.println("\nGoogle Maps location option:");
        System.out.println("1. I have a Google Maps share link");
        System.out.println("2. Create a Google Maps link from my problem location and landmark");
        int mapChoice = readIntInRange(sc, "Choose 1 or 2: ", 1, 2);

        String googleMapsUrl;
        if (mapChoice == 1) {
            googleMapsUrl = readRequiredText(sc,
                    "Paste the Google Maps share link (open Google Maps, choose Share, then Copy link): ");
            if (!isGoogleMapsUrl(googleMapsUrl)) {
                System.out.println("That does not look like a Google Maps link.");
                System.out.println("A map search link will be created from the address and landmark instead.");
                googleMapsUrl = createGoogleMapsUrl(location, landmark);
            }
        } else {
            googleMapsUrl = createGoogleMapsUrl(location, landmark);
        }

        int severity = readIntInRange(sc, "Enter severity (1-10): ", 1, 10);
        System.out.print("Enter photo URL (or press Enter to skip): ");
        String photoUrl = sc.nextLine().trim();
        if (photoUrl.isEmpty()) photoUrl = null;

        RawSubmission submission = new RawSubmission(
                userId, description, location, landmark, googleMapsUrl, severity, photoUrl);

        LlmReportAssistant llmAssistant = new MockLlmAssistant();
        DuplicateChecker duplicateChecker = new MockDuplicateChecker();
        RoutingEngine routingEngine = new MockRoutingEngine();
        PriorityCalculator priorityCalculator = new MockPriorityCalculator();
        ReportRepository repository = new MockReportRepository();
        ReportWorkflowEngine engine = new ReportWorkflowEngine(
                llmAssistant, duplicateChecker, routingEngine, priorityCalculator, repository);

        try {
            System.out.println("\nProcessing your report...\n");
            ProcessResult result = engine.processNewReport(submission);

            System.out.println("============= PROCESSING RESULT =============");
            if (result.isSuccess()) {
                Report report = result.report();
                System.out.println("Status: SUCCESS");
                System.out.println("Report ID: " + report.getId());
                System.out.println("Reporter: " + report.getReporterId());
                System.out.println("Description: " + report.getDescription());
                System.out.println("Category: " + report.getCategory());
                System.out.println("Problem Location: " + report.getLocation());
                System.out.println("Nearby Landmark: " + report.getLandmark());
                System.out.println("Google Maps Location: " + report.getGoogleMapsUrl());
                System.out.println("Photo URL: " + (report.getPhotoUrl() == null ? "Not provided" : report.getPhotoUrl()));
                System.out.println("Created At: " + report.getCreatedAt());
                System.out.println("Final Status: " + report.getStatus());

                if ("DUPLICATE".equals(report.getStatus())) {
                    System.out.println("Duplicate of report: " + report.getDuplicateOfId());
                } else {
                    System.out.println("Assigned Department: " + report.getDepartment());
                    System.out.println("Priority Score: " + report.getPriorityScore() + "/10");
                    System.out.println("Priority Explanation: " + report.getPriorityExplanation());
                }
                System.out.println("\nOpen the Google Maps link above to check the location.");
            } else {
                System.out.println("Status: NEEDS CLARIFICATION");
                for (String question : result.clarificationsNeeded()) {
                    System.out.println("- " + question);
                }
            }
        } catch (IllegalArgumentException ex) {
            System.out.println("Could not process report: " + ex.getMessage());
        } finally {
            sc.close();
        }
    }

    private static String readRequiredText(Scanner sc, String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = sc.nextLine().trim();
            if (!value.isEmpty()) return value;
            System.out.println("This field cannot be empty. Please try again.");
        }
    }

    private static int readIntInRange(Scanner sc, String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            String input = sc.nextLine().trim();
            try {
                int value = Integer.parseInt(input);
                if (value >= min && value <= max) return value;
                System.out.println("Please enter a number from " + min + " to " + max + ".");
            } catch (NumberFormatException ex) {
                System.out.println("Please enter a whole number.");
            }
        }
    }

    // Google Maps URLs support searching for an address or place name in the query parameter.
    private static String createGoogleMapsUrl(String location, String landmark) {
        String query = location + ", near " + landmark;
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        return "https://www.google.com/maps/search/?api=1&query=" + encodedQuery;
    }

    private static boolean isGoogleMapsUrl(String value) {
        String lower = value.toLowerCase();
        return lower.startsWith("https://maps.app.goo.gl/")
                || lower.startsWith("https://goo.gl/maps/")
                || lower.startsWith("https://www.google.com/maps/")
                || lower.startsWith("https://maps.google.com/");
    }
}

record RawSubmission(
        String userId,
        String rawDescription,
        String location,
        String landmark,
        String googleMapsUrl,
        int userSeverityScore,
        String photoUrl) {}

record AiAssistedDetails(
        String rewrittenDescription,
        java.util.List<String> missingInformationQuestions,
        String suggestedCategory,
        boolean requiresUserClarification) {}

record PriorityAssessment(int finalScore, String explanation, String assignedDepartment) {}

class Report {
    private final String id;
    private final String reporterId;
    private final String location;
    private final String landmark;
    private final String googleMapsUrl;
    private final String photoUrl;
    private String description;
    private String category;
    private int priorityScore;
    private String priorityExplanation;
    private String department;
    private String status;
    private String duplicateOfId;
    private final LocalDateTime createdAt;

    public Report(RawSubmission submission) {
        this.id = UUID.randomUUID().toString();
        this.reporterId = submission.userId();
        this.location = submission.location();
        this.landmark = submission.landmark();
        this.googleMapsUrl = submission.googleMapsUrl();
        this.photoUrl = submission.photoUrl();
        this.status = "DRAFT";
        this.createdAt = LocalDateTime.now();
    }

    public void applyAiDetails(AiAssistedDetails details) {
        this.description = details.rewrittenDescription();
        this.category = details.suggestedCategory();
    }
    public void applyAssessment(PriorityAssessment assessment) {
        this.priorityScore = assessment.finalScore();
        this.priorityExplanation = assessment.explanation();
        this.department = assessment.assignedDepartment();
    }
    public void markAsDuplicate(String originalReportId) {
        this.status = "DUPLICATE";
        this.duplicateOfId = originalReportId;
    }
    public void markPendingReview() { this.status = "PENDING_REVIEW"; }

    public String getId() { return id; }
    public String getReporterId() { return reporterId; }
    public String getLocation() { return location; }
    public String getLandmark() { return landmark; }
    public String getGoogleMapsUrl() { return googleMapsUrl; }
    public String getPhotoUrl() { return photoUrl; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public String getDepartment() { return department; }
    public int getPriorityScore() { return priorityScore; }
    public String getPriorityExplanation() { return priorityExplanation; }
    public String getStatus() { return status; }
    public String getDuplicateOfId() { return duplicateOfId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

class ProcessResult {
    private final boolean success;
    private final Report report;
    private final java.util.List<String> clarificationsNeeded;

    private ProcessResult(boolean success, Report report, java.util.List<String> questions) {
        this.success = success;
        this.report = report;
        this.clarificationsNeeded = questions;
    }
    public static ProcessResult success(Report report) {
        return new ProcessResult(true, report, java.util.List.of());
    }
    public static ProcessResult needsClarification(java.util.List<String> questions) {
        return new ProcessResult(false, null, questions);
    }
    public boolean isSuccess() { return success; }
    public Report report() { return report; }
    public java.util.List<String> clarificationsNeeded() { return clarificationsNeeded; }
}

class ReportWorkflowEngine {
    private final LlmReportAssistant llmAssistant;
    private final DuplicateChecker duplicateChecker;
    private final RoutingEngine routingEngine;
    private final PriorityCalculator priorityCalculator;
    private final ReportRepository repository;

    public ReportWorkflowEngine(LlmReportAssistant llmAssistant, DuplicateChecker duplicateChecker,
            RoutingEngine routingEngine, PriorityCalculator priorityCalculator, ReportRepository repository) {
        this.llmAssistant = llmAssistant;
        this.duplicateChecker = duplicateChecker;
        this.routingEngine = routingEngine;
        this.priorityCalculator = priorityCalculator;
        this.repository = repository;
    }

    public ProcessResult processNewReport(RawSubmission submission) {
        if (submission.userSeverityScore() < 1 || submission.userSeverityScore() > 10) {
            throw new IllegalArgumentException("Severity must be between 1 and 10.");
        }
        Report report = new Report(submission);
        AiAssistedDetails aiDetails = llmAssistant.analyzeSubmission(submission);
        if (aiDetails.requiresUserClarification()) {
            return ProcessResult.needsClarification(aiDetails.missingInformationQuestions());
        }
        report.applyAiDetails(aiDetails);

        Optional<String> duplicateId = duplicateChecker.findLikelyDuplicate(
                submission.location(), submission.landmark(), aiDetails.suggestedCategory());
        if (duplicateId.isPresent()) {
            report.markAsDuplicate(duplicateId.get());
        } else {
            String department = routingEngine.determineDepartment(
                    aiDetails.suggestedCategory(), submission.location(), submission.landmark());
            PriorityAssessment assessment = priorityCalculator.calculate(
                    submission.userSeverityScore(), aiDetails.suggestedCategory(), department);
            report.applyAssessment(assessment);
            report.markPendingReview();
        }
        repository.save(report);
        return ProcessResult.success(report);
    }
}

interface LlmReportAssistant { AiAssistedDetails analyzeSubmission(RawSubmission submission); }
interface DuplicateChecker { Optional<String> findLikelyDuplicate(String location, String landmark, String category); }
interface RoutingEngine { String determineDepartment(String category, String location, String landmark); }
interface PriorityCalculator { PriorityAssessment calculate(int userSeverity, String category, String department); }
interface ReportRepository { void save(Report report); Report findById(String id); }

class MockLlmAssistant implements LlmReportAssistant {
    @Override
    public AiAssistedDetails analyzeSubmission(RawSubmission submission) {
        System.out.println("[Demo analyzer] Analyzing: '" + submission.rawDescription() + "'");
        String lower = submission.rawDescription().toLowerCase();
        String category;
        if (lower.contains("pothole") || lower.contains("road") || lower.contains("street")) {
            category = "Roads & Pavements";
        } else if (lower.contains("water") || lower.contains("leak") || lower.contains("pipe")) {
            category = "Water Supply";
        } else if (lower.contains("light") || lower.contains("lamp") || lower.contains("electricity")) {
            category = "Street Lighting / Electricity";
        } else if (lower.contains("garbage") || lower.contains("waste") || lower.contains("rubbish")) {
            category = "Waste Management";
        } else if (lower.contains("drain") || lower.contains("sewer") || lower.contains("flood")) {
            category = "Drainage";
        } else {
            category = "General Maintenance";
        }
        String rewritten = submission.rawDescription().trim();
        rewritten = Character.toUpperCase(rewritten.charAt(0)) + rewritten.substring(1);
        if (!rewritten.endsWith(".") && !rewritten.endsWith("!") && !rewritten.endsWith("?")) rewritten += ".";
        return new AiAssistedDetails(rewritten, java.util.List.of(), category, false);
    }
}

class MockDuplicateChecker implements DuplicateChecker {
    @Override
    public Optional<String> findLikelyDuplicate(String location, String landmark, String category) {
        System.out.println("[Demo duplicate checker] Duplicate detection is simulated; no existing reports are checked.");
        return Optional.empty();
    }
}

class MockRoutingEngine implements RoutingEngine {
    @Override
    public String determineDepartment(String category, String location, String landmark) {
        System.out.println("[Demo routing] Choosing a sample department for: " + category);
        switch (category) {
            case "Roads & Pavements": return "Department of Public Works (sample routing)";
            case "Water Supply": return "Water Supply Department (sample routing)";
            case "Street Lighting / Electricity": return "Electrical Maintenance Department (sample routing)";
            case "Waste Management": return "Sanitation Department (sample routing)";
            case "Drainage": return "Drainage Department (sample routing)";
            default: return "General Maintenance Department (sample routing)";
        }
    }
}

class MockPriorityCalculator implements PriorityCalculator {
    @Override
    public PriorityAssessment calculate(int userSeverity, String category, String department) {
        String explanation = "Demo score based on the citizen's severity rating. An official system should also verify risk and impact.";
        return new PriorityAssessment(userSeverity, explanation, department);
    }
}

class MockReportRepository implements ReportRepository {
    private final Map<String, Report> db = new HashMap<>();
    @Override
    public void save(Report report) {
        db.put(report.getId(), report);
        System.out.println("[Demo repository] Report saved in memory only; it will not persist after the program exits.");
    }
    @Override
    public Report findById(String id) { return db.get(id); }
}
