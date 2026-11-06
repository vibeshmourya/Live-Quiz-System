package com.quizlive.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.quizlive.model.*;
import com.quizlive.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ReportExportService {

    private final LiveSessionRepository liveSessionRepository;
    private final ParticipantRepository participantRepository;
    private final SubmissionRepository submissionRepository;
    private final ExamIncidentRepository incidentRepository;
    private final AuthService authService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ReportExportService(
            LiveSessionRepository liveSessionRepository,
            ParticipantRepository participantRepository,
            SubmissionRepository submissionRepository,
            ExamIncidentRepository incidentRepository,
            AuthService authService
    ) {
        this.liveSessionRepository = liveSessionRepository;
        this.participantRepository = participantRepository;
        this.submissionRepository = submissionRepository;
        this.incidentRepository = incidentRepository;
        this.authService = authService;
    }

    private LiveSession getAuthorizedSession(String pin, String authHeader) {
        Faculty faculty = authService.validateFaculty(authHeader);
        LiveSession session = liveSessionRepository.findByPin(pin)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found with PIN: " + pin));

        String owner = session.getQuiz().getFacultyUsername();
        if (owner != null && !owner.trim().isEmpty() && !owner.equalsIgnoreCase(faculty.getUsername())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: You are not authorized to access reports for this examination.");
        }
        return session;
    }

    // 1. INDIVIDUAL STUDENT REPORT (PDF)
    @Transactional(readOnly = true)
    public byte[] generateStudentReportPdf(String pin, String candidateId, String authHeader) {
        LiveSession session = getAuthorizedSession(pin, authHeader);
        Participant participant = participantRepository.findBySessionPinAndCandidateId(pin, candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Participant not found with ID: " + candidateId));

        Quiz quiz = session.getQuiz();
        int totalMarks = quiz.calculateTotalMarks();
        int studentScore = participant.getScore() != null ? participant.getScore() : 0;
        double percentage = totalMarks > 0 ? ((double) studentScore / totalMarks) * 100.0 : 0.0;
        String grade = calculateGrade(percentage);

        long correctCount = submissionRepository.countByLiveSessionIdAndParticipantIdAndIsCorrectTrue(session.getId(), participant.getId());
        long incorrectCount = submissionRepository.countByLiveSessionIdAndParticipantIdAndIsCorrectFalse(session.getId(), participant.getId());
        List<ExamIncident> incidents = incidentRepository.findByLiveSessionIdAndParticipantIdOrderByServerTimestampDesc(session.getId(), participant.getId());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 40, 40);

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // Header Banner
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, new Color(30, 41, 59));
            Font subTitleFont = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(100, 116, 139));
            Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(79, 70, 229));
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY);

            Paragraph header = new Paragraph("QUIZORA SECURE EXAMINATION SYSTEM", titleFont);
            header.setAlignment(Element.ALIGN_CENTER);
            document.add(header);

            Paragraph docType = new Paragraph("Individual Student Performance & Proctoring Report", subTitleFont);
            docType.setAlignment(Element.ALIGN_CENTER);
            docType.setSpacingAfter(15);
            document.add(docType);

            // Metadata Table
            PdfPTable metaTable = new PdfPTable(2);
            metaTable.setWidthPercentage(100);
            metaTable.setSpacingAfter(15);

            addTableCell(metaTable, "Examination Title:", boldFont, true);
            addTableCell(metaTable, sanitizeText(quiz.getTitle()), normalFont, false);

            addTableCell(metaTable, "Session PIN:", boldFont, true);
            addTableCell(metaTable, session.getPin(), normalFont, false);

            addTableCell(metaTable, "Faculty Examiner:", boldFont, true);
            addTableCell(metaTable, sanitizeText(quiz.getFacultyUsername() != null ? quiz.getFacultyUsername() : "Faculty"), normalFont, false);

            addTableCell(metaTable, "Student Name / ID:", boldFont, true);
            addTableCell(metaTable, sanitizeText(participant.getNickname()) + " (" + participant.getCandidateId() + ")", normalFont, false);

            addTableCell(metaTable, "Joined Timestamp:", boldFont, true);
            addTableCell(metaTable, participant.getJoinedAt() != null ? participant.getJoinedAt().format(DATE_FORMATTER) : "N/A", normalFont, false);

            document.add(metaTable);

            // Performance Summary
            Paragraph perfHeader = new Paragraph("EXAMINATION SCORE & EVALUATION", sectionFont);
            perfHeader.setSpacingAfter(8);
            document.add(perfHeader);

            PdfPTable perfTable = new PdfPTable(4);
            perfTable.setWidthPercentage(100);
            perfTable.setSpacingAfter(15);

            addHeaderCell(perfTable, "Score Obtained");
            addHeaderCell(perfTable, "Maximum Marks");
            addHeaderCell(perfTable, "Percentage");
            addHeaderCell(perfTable, "Grade / Status");

            addCenterCell(perfTable, String.valueOf(studentScore) + " pts", boldFont);
            addCenterCell(perfTable, String.valueOf(totalMarks) + " pts", normalFont);
            addCenterCell(perfTable, String.format("%.1f%%", percentage), boldFont);
            addCenterCell(perfTable, grade + (percentage >= 40.0 ? " (PASS)" : " (FAIL)"), boldFont);

            document.add(perfTable);

            // Question Metrics Breakdown
            Paragraph qHeader = new Paragraph("QUESTION BREAKDOWN", sectionFont);
            qHeader.setSpacingAfter(8);
            document.add(qHeader);

            PdfPTable qTable = new PdfPTable(3);
            qTable.setWidthPercentage(100);
            qTable.setSpacingAfter(15);

            addHeaderCell(qTable, "Total Questions");
            addHeaderCell(qTable, "Correct Answers");
            addHeaderCell(qTable, "Incorrect / Unanswered");

            addCenterCell(qTable, String.valueOf(quiz.getQuestions().size()), normalFont);
            addCenterCell(qTable, String.valueOf(correctCount), normalFont);
            addCenterCell(qTable, String.valueOf(incorrectCount), normalFont);

            document.add(qTable);

            // Examination Incidents
            Paragraph incHeader = new Paragraph("PROCTORING & INTEGRITY AUDIT", sectionFont);
            incHeader.setSpacingAfter(8);
            document.add(incHeader);

            if (incidents.isEmpty()) {
                Paragraph noInc = new Paragraph("✓ Clean Record: No security violations or technical disruptions recorded during this examination session.", normalFont);
                noInc.setSpacingAfter(15);
                document.add(noInc);
            } else {
                PdfPTable incTable = new PdfPTable(4);
                incTable.setWidthPercentage(100);
                incTable.setWidths(new float[]{25, 35, 20, 20});
                incTable.setSpacingAfter(15);

                addHeaderCell(incTable, "Incident Type");
                addHeaderCell(incTable, "Details");
                addHeaderCell(incTable, "Timestamp");
                addHeaderCell(incTable, "Review Status");

                for (ExamIncident inc : incidents) {
                    addTableCell(incTable, inc.getEventType().name(), normalFont, false);
                    addTableCell(incTable, sanitizeText(inc.getIncidentDetails()), normalFont, false);
                    addTableCell(incTable, inc.getServerTimestamp().format(DATE_FORMATTER), normalFont, false);
                    addTableCell(incTable, inc.getReviewStatus().name(), boldFont, false);
                }
                document.add(incTable);
            }

            // Footer note
            Paragraph footer = new Paragraph("Generated by Quizora Secure Examination System • Authoritative Academic Record", subTitleFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.setSpacingBefore(20);
            document.add(footer);

            document.close();
        } catch (DocumentException e) {
            throw new RuntimeException("Error generating individual student PDF", e);
        }

        return out.toByteArray();
    }

    // 2. COMPLETE EXAMINATION RESULTS (PDF)
    @Transactional(readOnly = true)
    public byte[] generateCompleteExamReportPdf(String pin, String authHeader) {
        LiveSession session = getAuthorizedSession(pin, authHeader);
        Quiz quiz = session.getQuiz();
        List<Participant> participants = participantRepository.findByLiveSessionIdOrderByScoreDesc(session.getId());
        int totalMarks = quiz.calculateTotalMarks();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 40, 40);

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, new Color(30, 41, 59));
            Font subTitleFont = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(100, 116, 139));
            Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(79, 70, 229));
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.BLACK);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);

            Paragraph header = new Paragraph("QUIZORA SECURE EXAMINATION SYSTEM", titleFont);
            header.setAlignment(Element.ALIGN_CENTER);
            document.add(header);

            Paragraph docType = new Paragraph("Official Examination Results Roster", subTitleFont);
            docType.setAlignment(Element.ALIGN_CENTER);
            docType.setSpacingAfter(15);
            document.add(docType);

            // Overview Table
            PdfPTable metaTable = new PdfPTable(2);
            metaTable.setWidthPercentage(100);
            metaTable.setSpacingAfter(15);

            addTableCell(metaTable, "Examination Title:", boldFont, true);
            addTableCell(metaTable, sanitizeText(quiz.getTitle()), normalFont, false);

            addTableCell(metaTable, "Session PIN / Date:", boldFont, true);
            addTableCell(metaTable, session.getPin() + " | " + session.getCreatedAt().format(DATE_FORMATTER), normalFont, false);

            addTableCell(metaTable, "Faculty Controller:", boldFont, true);
            addTableCell(metaTable, sanitizeText(quiz.getFacultyUsername() != null ? quiz.getFacultyUsername() : "Faculty"), normalFont, false);

            addTableCell(metaTable, "Total Candidates Enrolled:", boldFont, true);
            addTableCell(metaTable, String.valueOf(participants.size()), normalFont, false);

            document.add(metaTable);

            // Ranked Roster Table
            Paragraph rosterTitle = new Paragraph("STUDENT SCORECARD & RANKINGS", sectionFont);
            rosterTitle.setSpacingAfter(8);
            document.add(rosterTitle);

            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{10, 30, 15, 15, 15, 15});
            table.setSpacingAfter(15);

            addHeaderCell(table, "Rank");
            addHeaderCell(table, "Student Display Name");
            addHeaderCell(table, "Marks");
            addHeaderCell(table, "Percentage");
            addHeaderCell(table, "Grade");
            addHeaderCell(table, "Incidents");

            int rank = 1;
            for (Participant p : participants) {
                int score = p.getScore() != null ? p.getScore() : 0;
                double pct = totalMarks > 0 ? ((double) score / totalMarks) * 100.0 : 0.0;
                long incidentCount = incidentRepository.countByLiveSessionIdAndParticipantId(session.getId(), p.getId());

                addCenterCell(table, "#" + rank++, boldFont);
                addTableCell(table, sanitizeText(p.getNickname()), normalFont, false);
                addCenterCell(table, score + " / " + totalMarks, normalFont);
                addCenterCell(table, String.format("%.1f%%", pct), normalFont);
                addCenterCell(table, calculateGrade(pct), boldFont);
                addCenterCell(table, String.valueOf(incidentCount), incidentCount > 0 ? boldFont : normalFont);
            }

            document.add(table);

            Paragraph footer = new Paragraph("Generated by Quizora Secure Examination System • Confidential", subTitleFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.setSpacingBefore(15);
            document.add(footer);

            document.close();
        } catch (DocumentException e) {
            throw new RuntimeException("Error generating complete exam PDF", e);
        }

        return out.toByteArray();
    }

    // 3. COMPLETE EXAMINATION RESULTS (CSV)
    @Transactional(readOnly = true)
    public String generateCompleteExamCsv(String pin, String authHeader) {
        LiveSession session = getAuthorizedSession(pin, authHeader);
        Quiz quiz = session.getQuiz();
        List<Participant> participants = participantRepository.findByLiveSessionIdOrderByScoreDesc(session.getId());
        int totalMarks = quiz.calculateTotalMarks();

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        // Header line
        pw.println("Rank,Candidate ID,Nickname,Marks Obtained,Maximum Marks,Percentage,Grade,Correct Count,Incorrect Count,Incident Count,Status");

        int rank = 1;
        for (Participant p : participants) {
            int score = p.getScore() != null ? p.getScore() : 0;
            double pct = totalMarks > 0 ? ((double) score / totalMarks) * 100.0 : 0.0;
            long correct = submissionRepository.countByLiveSessionIdAndParticipantIdAndIsCorrectTrue(session.getId(), p.getId());
            long incorrect = submissionRepository.countByLiveSessionIdAndParticipantIdAndIsCorrectFalse(session.getId(), p.getId());
            long incidents = incidentRepository.countByLiveSessionIdAndParticipantId(session.getId(), p.getId());

            pw.printf("%d,%s,%s,%d,%d,%.1f%%,%s,%d,%d,%d,%s%n",
                    rank++,
                    escapeCsv(p.getCandidateId()),
                    escapeCsv(p.getNickname()),
                    score,
                    totalMarks,
                    pct,
                    calculateGrade(pct),
                    correct,
                    incorrect,
                    incidents,
                    pct >= 40.0 ? "PASS" : "FAIL"
            );
        }

        pw.flush();
        return sw.toString();
    }

    // 4. INCIDENT SUMMARY REPORT (PDF)
    @Transactional(readOnly = true)
    public byte[] generateIncidentReportPdf(String pin, String authHeader) {
        LiveSession session = getAuthorizedSession(pin, authHeader);
        Quiz quiz = session.getQuiz();
        List<ExamIncident> incidents = incidentRepository.findByLiveSessionIdOrderByServerTimestampDesc(session.getId());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 40, 40);

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, new Color(30, 41, 59));
            Font subTitleFont = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(100, 116, 139));
            Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(79, 70, 229));
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.BLACK);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);

            Paragraph header = new Paragraph("QUIZORA SECURE EXAMINATION SYSTEM", titleFont);
            header.setAlignment(Element.ALIGN_CENTER);
            document.add(header);

            Paragraph docType = new Paragraph("Comprehensive Examination Incident & Proctoring Report", subTitleFont);
            docType.setAlignment(Element.ALIGN_CENTER);
            docType.setSpacingAfter(15);
            document.add(docType);

            // Metadata
            PdfPTable metaTable = new PdfPTable(2);
            metaTable.setWidthPercentage(100);
            metaTable.setSpacingAfter(15);

            addTableCell(metaTable, "Examination Title:", boldFont, true);
            addTableCell(metaTable, sanitizeText(quiz.getTitle()), normalFont, false);

            addTableCell(metaTable, "Session PIN:", boldFont, true);
            addTableCell(metaTable, session.getPin(), normalFont, false);

            addTableCell(metaTable, "Total Recorded Incidents:", boldFont, true);
            addTableCell(metaTable, String.valueOf(incidents.size()), boldFont, false);

            document.add(metaTable);

            Paragraph incTitle = new Paragraph("LOGGED SECURITY & TECHNICAL INCIDENTS", sectionFont);
            incTitle.setSpacingAfter(8);
            document.add(incTitle);

            if (incidents.isEmpty()) {
                Paragraph noInc = new Paragraph("✓ No incidents recorded for this examination session.", normalFont);
                noInc.setSpacingAfter(15);
                document.add(noInc);
            } else {
                PdfPTable table = new PdfPTable(5);
                table.setWidthPercentage(100);
                table.setWidths(new float[]{20, 20, 30, 15, 15});
                table.setSpacingAfter(15);

                addHeaderCell(table, "Candidate");
                addHeaderCell(table, "Event Type");
                addHeaderCell(table, "Details");
                addHeaderCell(table, "Timestamp");
                addHeaderCell(table, "Review Status");

                for (ExamIncident inc : incidents) {
                    addTableCell(table, sanitizeText(inc.getParticipant().getNickname()), normalFont, false);
                    addTableCell(table, inc.getEventType().name(), boldFont, false);
                    addTableCell(table, sanitizeText(inc.getIncidentDetails()), normalFont, false);
                    addTableCell(table, inc.getServerTimestamp().format(DATE_FORMATTER), normalFont, false);
                    addTableCell(table, inc.getReviewStatus().name(), boldFont, false);
                }
                document.add(table);
            }

            Paragraph footer = new Paragraph("Generated by Quizora Secure Examination System • Confidential Incident Audit", subTitleFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.setSpacingBefore(15);
            document.add(footer);

            document.close();
        } catch (DocumentException e) {
            throw new RuntimeException("Error generating incident report PDF", e);
        }

        return out.toByteArray();
    }

    // Table Helper Methods
    private void addHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE)));
        cell.setBackgroundColor(new Color(79, 70, 229)); // Indigo primary
        cell.setPadding(6);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private void addTableCell(PdfPTable table, String text, Font font, boolean isHeader) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(5);
        if (isHeader) {
            cell.setBackgroundColor(new Color(248, 250, 252));
        }
        table.addCell(cell);
    }

    private void addCenterCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(5);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private String calculateGrade(double percentage) {
        if (percentage >= 90.0) return "A+";
        if (percentage >= 80.0) return "A";
        if (percentage >= 70.0) return "B";
        if (percentage >= 60.0) return "C";
        if (percentage >= 50.0) return "D";
        if (percentage >= 40.0) return "E";
        return "F";
    }

    private String sanitizeText(String input) {
        if (input == null) return "";
        return input.replaceAll("[\\r\\n]+", " ").trim();
    }

    private String escapeCsv(String val) {
        if (val == null) return "\"\"";
        String s = val.trim();
        // Prevent CSV Formula Injection
        if (s.startsWith("=") || s.startsWith("+") || s.startsWith("-") || s.startsWith("@")) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
