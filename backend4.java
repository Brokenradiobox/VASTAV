import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Scanner;
import java.util.UUID;

/**
 * Urban Infrastructure Priority Engine
 * A beginner-friendly, file-backed Java hackathon prototype.
 *
 * Save as backend4.java and run:
 *   javac backend4.java
 *   java backend4
 *
 * Data is stored in the "urban_data" folder beside this file / working directory.
 */
public class backend5 {
    private static final Scanner SC = new Scanner(System.in);
    private static final Path DATA_DIR = Path.of("urban_data");
    private static final Path REPORTS_FILE = DATA_DIR.resolve("reports.tsv");
    private static final Path RECEIPT_DIR = DATA_DIR.resolve("receipts");
    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private static final DateTimeFormatter FILE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) {
        try {
            Files.createDirectories(DATA_DIR);
            Files.createDirectories(RECEIPT_DIR);
        } catch (IOException ex) {
            System.out.println("Could not create data folders: " + ex.getMessage());
            return;
        }

        ReportRepository repository = new FileReportRepository(REPORTS_FILE);
        ReportWorkflowEngine engine = new ReportWorkflowEngine(repository);

        System.out.println("==============================================");
        System.out.println("   URBAN INFRASTRUCTURE PRIORITY ENGINE");
        System.out.println("==============================================");
        System.out.println("A local demo for reporting and prioritising city issues.");

        boolean running = true;
        while (running) {
            System.out.println("\nMAIN MENU");
            System.out.println("1. Submit a new report");
            System.out.println("2. View priority dashboard");
            System.out.println("3. Track a report / update its status (admin demo)");
            System.out.println("4. Verify and view a receipt");
            System.out.println("5. Exit");
            int choice = readInt(SC, "Choose an option: ", 1, 5);

            switch (choice) {
                case 1 -> submitReport(engine, repository);
                case 2 -> showDashboard(repository);
                case 3 -> trackOrUpdateReport(repository);
                case 4 -> verifyReceipt();
                case 5 -> running = false;
                default -> System.out.println("Invalid option.");
            }
        }
        SC.close();
        System.out.println("Thank you for using the Urban Infrastructure Priority Engine.");
    }

    private static void submitReport(ReportWorkflowEngine engine, ReportRepository repository) {
        System.out.println("\n--- NEW INFRASTRUCTURE REPORT ---");
        String name = required(SC, "Citizen full name: ");
        String phone = required(SC, "Contact number: ");
        String email = required(SC, "Email address: ");
        String description = required(SC, "Describe the infrastructure problem: ");
        String location = required(SC, "Location (area, city, state, PIN): ");
        String landmark = required(SC, "Nearby landmark: ");

        int severity = readInt(SC, "Severity (1 = low, 10 = very high): ", 1, 10);
        int population = readInt(SC, "Estimated people affected: ", 1, 10000000);
        int days = readInt(SC, "How many days has the issue existed? ", 0, 36500);
        int recurrence = readInt(SC, "How often does it recur? (1 = once, 5 = very frequent): ", 1, 5);
        int safetyRisk = readInt(SC, "Safety risk (1 = low, 10 = immediate/high risk): ", 1, 10);
        int estimatedCost = readInt(SC, "Estimated repair cost in INR (0 if unknown): ", 0, 1000000000);

        String mapUrl;
        System.out.println("\nGoogle Maps option:");
        System.out.println("1. Paste a Google Maps share link");
        System.out.println("2. Create a Google Maps search from the location and landmark");
        int mapChoice = readInt(SC, "Choose 1 or 2: ", 1, 2);
        if (mapChoice == 1) {
            String enteredUrl = required(SC, "Paste Google Maps link: ");
            if (isGoogleMapsUrl(enteredUrl)) {
                mapUrl = enteredUrl;
            } else {
                mapUrl = createMapUrl(location, landmark);
                System.out.println("Link not recognised; generated a Maps search link instead.");
            }
        } else {
            mapUrl = createMapUrl(location, landmark);
        }

        System.out.print("Photo URL (optional; press Enter to skip): ");
        String photoUrl = SC.nextLine().trim();
        if (photoUrl.isEmpty()) photoUrl = "Not provided";

        RawSubmission submission = new RawSubmission(
                name, phone, email, description, location, landmark, mapUrl, photoUrl,
                severity, population, days, recurrence, safetyRisk, estimatedCost);

        try {
            Report report = engine.processNewReport(submission);
            String receipt = buildReceipt(report);
            saveReceipt(report.getReceiptId(), receipt);

            System.out.println("\n=============== REPORT RECEIPT ===============");
            System.out.println(receipt);
            System.out.println("Receipt file: " + receiptPath(report.getReceiptId()).toAbsolutePath());
            System.out.println("Keep this Receipt ID to track the report: " + report.getReceiptId());
        } catch (IOException ex) {
            System.out.println("Report could not be saved: " + ex.getMessage());
        }
    }

    private static void showDashboard(ReportRepository repository) {
        List<Report> reports;
        try {
            reports = repository.findAll();
        } catch (IOException ex) {
            System.out.println("Could not load reports: " + ex.getMessage());
            return;
        }

        System.out.println("\n================ CITY PRIORITY DASHBOARD ================");
        if (reports.isEmpty()) {
            System.out.println("No reports have been submitted yet.");
            return;
        }

        long pending = reports.stream().filter(r -> !r.getStatus().equals("RESOLVED")
                && !r.getStatus().equals("DUPLICATE") && !r.getStatus().equals("REJECTED")).count();
        long resolved = reports.stream().filter(r -> r.getStatus().equals("RESOLVED")).count();
        long duplicates = reports.stream().filter(r -> r.getStatus().equals("DUPLICATE")).count();

        System.out.println("Total reports: " + reports.size()
                + " | Active: " + pending
                + " | Resolved: " + resolved
                + " | Duplicates: " + duplicates);
        System.out.println("\nReports ranked by priority (highest first):");
        reports.sort(Comparator.comparingInt(Report::getPriority).reversed()
                .thenComparing(Report::getCreatedAt));

        int rank = 1;
        for (Report r : reports) {
            System.out.println("\n" + rank++ + ". [" + r.getPriorityBand() + "] "
                    + r.getCategory() + " | Score: " + r.getPriority() + "/100");
            System.out.println("   Report ID: " + r.getId());
            System.out.println("   Receipt ID: " + r.getReceiptId());
            System.out.println("   Location: " + r.getLocation());
            System.out.println("   Status: " + r.getStatus());
            System.out.println("   Department: " + r.getDepartment());
            System.out.println("   Reason: " + r.getPriorityExplanation());
        }
        System.out.println("\nNote: rankings are decision-support suggestions, not official decisions.");
    }

    private static void trackOrUpdateReport(ReportRepository repository) {
        List<Report> reports;
        try {
            reports = repository.findAll();
        } catch (IOException ex) {
            System.out.println("Could not load reports: " + ex.getMessage());
            return;
        }
        if (reports.isEmpty()) {
            System.out.println("No reports are available.");
            return;
        }

        String id = required(SC, "Enter Report ID or Receipt ID: ");
        Optional<Report> found = reports.stream()
                .filter(r -> r.getId().equalsIgnoreCase(id)
                        || r.getReceiptId().equalsIgnoreCase(id))
                .findFirst();

        if (found.isEmpty()) {
            System.out.println("No report found with that ID.");
            return;
        }

        Report report = found.get();
        System.out.println("\nReport details:");
        System.out.println("Description: " + report.getDescription());
        System.out.println("Category: " + report.getCategory());
        System.out.println("Location: " + report.getLocation());
        System.out.println("Priority: " + report.getPriority() + "/100 (" + report.getPriorityBand() + ")");
        System.out.println("Status: " + report.getStatus());
        System.out.println("Department: " + report.getDepartment());
        System.out.println("Created: " + report.getCreatedAt().format(FORMAT));

        System.out.println("\nStatus options (admin demo):");
        System.out.println("1. SUBMITTED");
        System.out.println("2. UNDER_REVIEW");
        System.out.println("3. APPROVED");
        System.out.println("4. IN_PROGRESS");
        System.out.println("5. RESOLVED");
        System.out.println("6. REJECTED");
        System.out.println("7. Leave unchanged");
        int choice = readInt(SC, "Choose new status: ", 1, 7);
        if (choice == 7) return;

        String[] statuses = {"SUBMITTED", "UNDER_REVIEW", "APPROVED", "IN_PROGRESS",
                "RESOLVED", "REJECTED"};
        report.setStatus(statuses[choice - 1]);
        if (report.getStatus().equals("RESOLVED")) {
            report.setResolutionNote(required(SC, "Resolution note: "));
            report.setResolvedAt(LocalDateTime.now());
        }
        try {
            repository.save(report);
            saveReceipt(report.getReceiptId(), buildReceipt(report));
            System.out.println("Status updated and saved.");
        } catch (IOException ex) {
            System.out.println("Could not save status: " + ex.getMessage());
        }
    }

    private static void verifyReceipt() {
        String id = required(SC, "Enter Receipt ID (e.g. URB-ABC12345): ");
        if (!id.matches("URB-[A-Fa-f0-9-]+")) {
            System.out.println("Receipt ID format is not valid.");
            return;
        }
        Path file = receiptPath(id);
        if (!Files.isRegularFile(file)) {
            System.out.println("Receipt not found in this project's urban_data/receipts folder.");
            return;
        }
        try {
            System.out.println("\nSaved receipt:");
            System.out.println(Files.readString(file, StandardCharsets.UTF_8));
            System.out.println("Verification: a receipt file with this ID exists on this computer.");
        } catch (IOException ex) {
            System.out.println("Could not read receipt: " + ex.getMessage());
        }
    }

    private static String buildReceipt(Report r) {
        StringBuilder s = new StringBuilder();
        s.append("URBAN INFRASTRUCTURE REPORT RECEIPT\n");
        s.append("========================================\n");
        s.append("Receipt ID: ").append(r.getReceiptId()).append('\n');
        s.append("Report ID: ").append(r.getId()).append('\n');
        s.append("Submitted at: ").append(r.getCreatedAt().format(FORMAT)).append('\n');
        s.append("Citizen name: ").append(r.getName()).append('\n');
        s.append("Contact number: ").append(r.getPhone()).append('\n');
        s.append("Email: ").append(r.getEmail()).append('\n');
        s.append("Description: ").append(r.getDescription()).append('\n');
        s.append("Category: ").append(r.getCategory()).append('\n');
        s.append("Location: ").append(r.getLocation()).append('\n');
        s.append("Nearby landmark: ").append(r.getLandmark()).append('\n');
        s.append("Google Maps link: ").append(r.getMapUrl()).append('\n');
        s.append("Photo URL: ").append(r.getPhotoUrl()).append('\n');
        s.append("Severity: ").append(r.getSeverity()).append("/10\n");
        s.append("People affected: ").append(r.getPopulationAffected()).append('\n');
        s.append("Duration: ").append(r.getDurationDays()).append(" day(s)\n");
        s.append("Recurrence: ").append(r.getRecurrence()).append("/5\n");
        s.append("Safety risk: ").append(r.getSafetyRisk()).append("/10\n");
        s.append("Estimated repair cost: ").append(r.getEstimatedCost() == 0
                ? "Unknown" : "INR " + r.getEstimatedCost()).append('\n');
        s.append("Priority score: ").append(r.getPriority()).append("/100 (")
                .append(r.getPriorityBand()).append(")\n");
        s.append("Priority explanation: ").append(r.getPriorityExplanation()).append('\n');
        s.append("Assigned department (demo): ").append(r.getDepartment()).append('\n');
        s.append("Status: ").append(r.getStatus()).append('\n');
        if (!r.getResolutionNote().isBlank()) {
            s.append("Resolution note: ").append(r.getResolutionNote()).append('\n');
        }
        if (r.getResolvedAt() != null) {
            s.append("Resolved at: ").append(r.getResolvedAt().format(FORMAT)).append('\n');
        }
        s.append("----------------------------------------\n");
        s.append("Demo receipt only; not an official government acknowledgement.\n");
        return s.toString();
    }

    private static void saveReceipt(String id, String receipt) throws IOException {
        Files.createDirectories(RECEIPT_DIR);
        Files.writeString(receiptPath(id), receipt, StandardCharsets.UTF_8);
    }

    private static Path receiptPath(String id) {
        String safe = id.replaceAll("[^A-Za-z0-9-]", "");
        return RECEIPT_DIR.resolve(safe + ".txt");
    }

    private static String required(Scanner sc, String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = sc.nextLine().trim();
            if (!value.isEmpty()) return value;
            System.out.println("This field cannot be empty.");
        }
    }

    private static int readInt(Scanner sc, String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            try {
                int n = Integer.parseInt(sc.nextLine().trim());
                if (n >= min && n <= max) return n;
            } catch (NumberFormatException ignored) {
                // Ask again if the input is not an integer.
            }
            System.out.println("Enter a whole number from " + min + " to " + max + ".");
        }
    }

    private static String createMapUrl(String location, String landmark) {
        String query = URLEncoder.encode(location + ", near " + landmark, StandardCharsets.UTF_8);
        return "https://www.google.com/maps/search/?api=1&query=" + query;
    }

    private static boolean isGoogleMapsUrl(String value) {
        String v = value.toLowerCase(Locale.ROOT);
        return v.startsWith("https://maps.app.goo.gl/")
                || v.startsWith("https://goo.gl/maps/")
                || v.startsWith("https://www.google.com/maps/")
                || v.startsWith("https://maps.google.com/");
    }
}

record RawSubmission(String name, String phone, String email, String description,
                     String location, String landmark, String mapUrl, String photoUrl,
                     int severity, int populationAffected, int durationDays,
                     int recurrence, int safetyRisk, int estimatedCost) {}

class Report {
    private final String id;
    private final String receiptId;
    private final LocalDateTime createdAt;
    private final String name, phone, email, location, landmark, mapUrl, photoUrl;
    private final int severity, populationAffected, durationDays, recurrence, safetyRisk, estimatedCost;
    private final String description, category, department, priorityExplanation;
    private final int priority;
    private String status;
    private String resolutionNote;
    private LocalDateTime resolvedAt;

    Report(RawSubmission s, String category, String department, int priority, String explanation) {
        this.id = UUID.randomUUID().toString();
        this.receiptId = "URB-" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        this.createdAt = LocalDateTime.now();
        this.name = s.name();
        this.phone = s.phone();
        this.email = s.email();
        this.location = s.location();
        this.landmark = s.landmark();
        this.mapUrl = s.mapUrl();
        this.photoUrl = s.photoUrl();
        this.severity = s.severity();
        this.populationAffected = s.populationAffected();
        this.durationDays = s.durationDays();
        this.recurrence = s.recurrence();
        this.safetyRisk = s.safetyRisk();
        this.estimatedCost = s.estimatedCost();
        this.description = normalizeDescription(s.description());
        this.category = category;
        this.department = department;
        this.priority = priority;
        this.priorityExplanation = explanation;
        this.status = "SUBMITTED";
        this.resolutionNote = "";
    }

    // Constructor used to reload a saved report from disk.
    Report(String id, String receiptId, LocalDateTime createdAt, String name, String phone, String email,
           String location, String landmark, String mapUrl, String photoUrl, int severity,
           int populationAffected, int durationDays, int recurrence, int safetyRisk, int estimatedCost,
           String description, String category, String department, int priority, String priorityExplanation,
           String status, String resolutionNote, LocalDateTime resolvedAt) {
        this.id = id; this.receiptId = receiptId; this.createdAt = createdAt;
        this.name = name; this.phone = phone; this.email = email; this.location = location;
        this.landmark = landmark; this.mapUrl = mapUrl; this.photoUrl = photoUrl;
        this.severity = severity; this.populationAffected = populationAffected;
        this.durationDays = durationDays; this.recurrence = recurrence;
        this.safetyRisk = safetyRisk; this.estimatedCost = estimatedCost;
        this.description = description; this.category = category; this.department = department;
        this.priority = priority; this.priorityExplanation = priorityExplanation;
        this.status = status; this.resolutionNote = resolutionNote;
        this.resolvedAt = resolvedAt;
    }

    private static String normalizeDescription(String input) {
        String value = input.trim();
        if (value.isEmpty()) return value;
        value = Character.toUpperCase(value.charAt(0)) + value.substring(1);
        if (!value.endsWith(".") && !value.endsWith("!") && !value.endsWith("?")) value += ".";
        return value;
    }

    int getPriority() { return priority; }
    String getPriorityBand() {
        if (priority >= 80) return "CRITICAL";
        if (priority >= 60) return "HIGH";
        if (priority >= 40) return "MEDIUM";
        return "LOW";
    }
    String getId() { return id; }
    String getReceiptId() { return receiptId; }
    LocalDateTime getCreatedAt() { return createdAt; }
    String getName() { return name; }
    String getPhone() { return phone; }
    String getEmail() { return email; }
    String getLocation() { return location; }
    String getLandmark() { return landmark; }
    String getMapUrl() { return mapUrl; }
    String getPhotoUrl() { return photoUrl; }
    int getSeverity() { return severity; }
    int getPopulationAffected() { return populationAffected; }
    int getDurationDays() { return durationDays; }
    int getRecurrence() { return recurrence; }
    int getSafetyRisk() { return safetyRisk; }
    int getEstimatedCost() { return estimatedCost; }
    String getDescription() { return description; }
    String getCategory() { return category; }
    String getDepartment() { return department; }
    String getPriorityExplanation() { return priorityExplanation; }
    String getStatus() { return status; }
    String getResolutionNote() { return resolutionNote == null ? "" : resolutionNote; }
    LocalDateTime getResolvedAt() { return resolvedAt; }
    void setStatus(String status) { this.status = status; }
    void setResolutionNote(String note) { this.resolutionNote = note; }
    void setResolvedAt(LocalDateTime time) { this.resolvedAt = time; }
}

class ReportWorkflowEngine {
    private final ReportRepository repository;
    ReportWorkflowEngine(ReportRepository repository) { this.repository = repository; }

    Report processNewReport(RawSubmission s) throws IOException {
        if (s.severity() < 1 || s.severity() > 10) {
            throw new IllegalArgumentException("Severity must be between 1 and 10.");
        }

        String category = categorize(s.description());
        Optional<Report> duplicate = repository.findAll().stream()
                .filter(r -> r.getStatus().equals("DUPLICATE") == false)
                .filter(r -> r.getCategory().equals(category))
                .filter(r -> normalise(r.getLocation()).equals(normalise(s.location()))
                        || normalise(r.getLandmark()).equals(normalise(s.landmark())))
                .findFirst();

        if (duplicate.isPresent()) {
            Report old = duplicate.get();
            String explanation = "Likely duplicate of report " + old.getId()
                    + " because the category and location/landmark match.";
            Report report = new Report(s, category, departmentFor(category), 0, explanation);
            report.setStatus("DUPLICATE");
            repository.save(report);
            System.out.println("Possible duplicate detected. Existing report: " + old.getId());
            return report;
        }

        String department = departmentFor(category);
        PriorityAssessment assessment = calculatePriority(s);
        Report report = new Report(s, category, department, assessment.score(), assessment.explanation());
        if (assessment.score() >= 80) {
            System.out.println("\n*** URGENT REVIEW FLAG: this report has a high priority/safety score. ***");
        }
        repository.save(report);
        return report;
    }

    private static String categorize(String description) {
        String text = description.toLowerCase(Locale.ROOT);
        if (text.contains("pothole") || text.contains("road") || text.contains("street surface")
                || text.contains("pavement")) return "Roads & Pavements";
        if (text.contains("water") || text.contains("leak") || text.contains("pipe")) return "Water Supply";
        if (text.contains("light") || text.contains("lamp") || text.contains("electricity")
                || text.contains("electric wire")) return "Street Lighting / Electricity";
        if (text.contains("garbage") || text.contains("waste") || text.contains("rubbish")
                || text.contains("trash")) return "Waste Management";
        if (text.contains("drain") || text.contains("sewer") || text.contains("flood")) return "Drainage";
        return "General Maintenance";
    }

    private static String departmentFor(String category) {
        return switch (category) {
            case "Roads & Pavements" -> "Department of Public Works (demo)";
            case "Water Supply" -> "Water Supply Department (demo)";
            case "Street Lighting / Electricity" -> "Electrical Maintenance Department (demo)";
            case "Waste Management" -> "Sanitation Department (demo)";
            case "Drainage" -> "Drainage Department (demo)";
            default -> "General Maintenance Department (demo)";
        };
    }

    private static PriorityAssessment calculatePriority(RawSubmission s) {
        // Weighted, transparent scoring model. Each normalised factor is 0-100.
        double severity = s.severity() * 10.0;
        double population = Math.min(100.0, Math.log10(s.populationAffected() + 1) / 5.0 * 100.0);
        double duration = Math.min(100.0, s.durationDays() / 30.0 * 100.0);
        double recurrence = (s.recurrence() - 1) / 4.0 * 100.0;
        double safety = s.safetyRisk() * 10.0;

        int score = (int) Math.round(
                severity * 0.25
                + population * 0.20
                + duration * 0.15
                + recurrence * 0.15
                + safety * 0.25);
        score = Math.max(0, Math.min(100, score));

        String explanation = String.format(Locale.ROOT,
                "Weighted factors: severity %.0f/100 (25%%), population impact %.0f/100 (20%%), "
                        + "duration %.0f/100 (15%%), recurrence %.0f/100 (15%%), safety risk %.0f/100 (25%%). "
                        + "Estimated cost is recorded for planning but does not lower urgency.",
                severity, population, duration, recurrence, safety);
        return new PriorityAssessment(score, explanation);
    }

    private static String normalise(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}

record PriorityAssessment(int score, String explanation) {}

interface ReportRepository {
    void save(Report report) throws IOException;
    List<Report> findAll() throws IOException;
}

class FileReportRepository implements ReportRepository {
    private static final DateTimeFormatter FILE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final Path file;
    FileReportRepository(Path file) { this.file = file; }

    @Override
    public synchronized void save(Report report) throws IOException {
        List<Report> reports = findAll();
        boolean replaced = false;
        for (int i = 0; i < reports.size(); i++) {
            if (reports.get(i).getId().equals(report.getId())) {
                reports.set(i, report);
                replaced = true;
                break;
            }
        }
        if (!replaced) reports.add(report);

        Files.createDirectories(file.getParent());
        List<String> lines = new ArrayList<>();
        lines.add("URBAN_INFRASTRUCTURE_REPORTS_V1");
        for (Report r : reports) lines.add(encode(r));
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    @Override
    public synchronized List<Report> findAll() throws IOException {
        List<Report> reports = new ArrayList<>();
        if (!Files.exists(file)) return reports;
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) continue;
            try {
                reports.add(decode(lines.get(i)));
            } catch (RuntimeException ex) {
                System.out.println("Warning: skipped a damaged saved report on line " + (i + 1));
            }
        }
        return reports;
    }

    private static String encode(Report r) {
        String[] values = {
            r.getId(), r.getReceiptId(), r.getCreatedAt().format(FILE_FORMAT),
            r.getName(), r.getPhone(), r.getEmail(), r.getLocation(), r.getLandmark(),
            r.getMapUrl(), r.getPhotoUrl(), Integer.toString(r.getSeverity()),
            Integer.toString(r.getPopulationAffected()), Integer.toString(r.getDurationDays()),
            Integer.toString(r.getRecurrence()), Integer.toString(r.getSafetyRisk()),
            Integer.toString(r.getEstimatedCost()), r.getDescription(), r.getCategory(),
            r.getDepartment(), Integer.toString(r.getPriority()), r.getPriorityExplanation(),
            r.getStatus(), r.getResolutionNote(),
            r.getResolvedAt() == null ? "" : r.getResolvedAt().format(FILE_FORMAT)
        };
        for (int i = 0; i < values.length; i++) values[i] = safe(values[i]);
        return String.join("\t", values);
    }

    private static Report decode(String line) {
        String[] v = line.split("\t", -1);
        if (v.length != 24) throw new IllegalArgumentException("Unexpected report field count");
        for (int i = 0; i < v.length; i++) v[i] = unsafe(v[i]);
        LocalDateTime resolved = v[23].isBlank() ? null : LocalDateTime.parse(v[23], FILE_FORMAT);
        return new Report(v[0], v[1], LocalDateTime.parse(v[2], FILE_FORMAT),
                v[3], v[4], v[5], v[6], v[7], v[8], v[9],
                Integer.parseInt(v[10]), Integer.parseInt(v[11]), Integer.parseInt(v[12]),
                Integer.parseInt(v[13]), Integer.parseInt(v[14]), Integer.parseInt(v[15]),
                v[16], v[17], v[18], Integer.parseInt(v[19]), v[20], v[21], v[22], resolved);
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace("\\", "\\\\")
                .replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String unsafe(String value) {
        StringBuilder out = new StringBuilder();
        boolean escape = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escape) {
                out.append(switch (c) {
                    case 't' -> '\t';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case '\\' -> '\\';
                    default -> c;
                });
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else {
                out.append(c);
            }
        }
        if (escape) out.append('\\');
        return out.toString();
    }
}
