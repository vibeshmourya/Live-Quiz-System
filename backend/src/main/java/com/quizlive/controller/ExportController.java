package com.quizlive.controller;

import com.quizlive.service.ReportExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
public class ExportController {

    private final ReportExportService reportExportService;

    public ExportController(ReportExportService reportExportService) {
        this.reportExportService = reportExportService;
    }

    @GetMapping({"/exam/{pin}/student/{candidateId}/pdf", "/session/{pin}/student/{candidateId}/pdf"})
    public ResponseEntity<byte[]> exportStudentReportPdf(
            @PathVariable String pin,
            @PathVariable String candidateId,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        byte[] pdfBytes = reportExportService.generateStudentReportPdf(pin, candidateId, authHeader);
        String filename = String.format("Quizora_Student_Report_%s_%s.pdf", pin, candidateId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(pdfBytes);
    }

    @GetMapping({"/exam/{pin}/pdf", "/session/{pin}/pdf"})
    public ResponseEntity<byte[]> exportCompleteReportPdf(
            @PathVariable String pin,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        byte[] pdfBytes = reportExportService.generateCompleteExamReportPdf(pin, authHeader);
        String filename = String.format("Quizora_Exam_Results_%s.pdf", pin);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(pdfBytes);
    }

    @GetMapping({"/exam/{pin}/csv", "/session/{pin}/csv"})
    public ResponseEntity<String> exportCompleteReportCsv(
            @PathVariable String pin,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        String csvContent = reportExportService.generateCompleteExamCsv(pin, authHeader);
        String filename = String.format("Quizora_Exam_Results_%s.csv", pin);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(csvContent);
    }

    @GetMapping({"/exam/{pin}/incidents/pdf", "/session/{pin}/incidents/pdf"})
    public ResponseEntity<byte[]> exportIncidentReportPdf(
            @PathVariable String pin,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        byte[] pdfBytes = reportExportService.generateIncidentReportPdf(pin, authHeader);
        String filename = String.format("Quizora_Incident_Report_%s.pdf", pin);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(pdfBytes);
    }
}
