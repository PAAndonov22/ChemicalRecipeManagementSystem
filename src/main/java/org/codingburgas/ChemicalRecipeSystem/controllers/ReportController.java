package org.codingburgas.ChemicalRecipeSystem.controllers;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import java.util.stream.Collectors;

import org.codingburgas.ChemicalRecipeSystem.models.Event;
import org.codingburgas.ChemicalRecipeSystem.models.PeriodReport;
import org.codingburgas.ChemicalRecipeSystem.services.PdfService;
import org.codingburgas.ChemicalRecipeSystem.services.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ReportController {
    private final ReportService reportService;
    private final PdfService pdfService;

    public ReportController(ReportService reportService, PdfService pdfService) {
        this.reportService = reportService;
        this.pdfService = pdfService;
    }

    /** Statistics for any period (from - to), grouped by day / week / month / year. */
    @GetMapping("/reports")
    public String statistics(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                             @RequestParam(defaultValue = "month") String groupBy,
                             Model model) {
        if (to == null) {
            to = LocalDate.now();
        }
        if (from == null) {
            from = to.minusMonths(6);
        }
        model.addAttribute("report", reportService.build(from, to, groupBy));
        model.addAttribute("groups", ReportService.GROUPS);
        return "reports/statistics";
    }

    /** Reports for 1, 6 or 12 months with a calendar to pick the end date. */
    @GetMapping("/reports/monthly")
    public String monthly(@RequestParam(defaultValue = "1") int months,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                          Model model) {
        LocalDate today = LocalDate.now();
        LocalDate selected = date == null ? today : date;
        PeriodReport report = reportService.monthly(months, selected);
        YearMonth calendarMonth = YearMonth.from(report.to());
        Set<LocalDate> eventDays = report.events().stream().map(Event::getDate).collect(Collectors.toSet());

        model.addAttribute("report", report);
        model.addAttribute("months", report.rows().size());
        model.addAttribute("monthOptions", ReportService.MONTH_OPTIONS);
        model.addAttribute("selected", selected);
        model.addAttribute("calendarMonth", calendarMonth);
        model.addAttribute("weeks", reportService.calendar(calendarMonth));
        model.addAttribute("eventDays", eventDays);
        model.addAttribute("minDate", today.minusMonths(12));
        model.addAttribute("today", today);
        model.addAttribute("prevMonth", calendarMonth.minusMonths(1).atDay(1));
        model.addAttribute("nextMonth", calendarMonth.plusMonths(1).atDay(1));
        return "reports/monthly";
    }

    /** The accounting report as a PDF: shown in the browser, or downloaded when download=true. */
    @GetMapping("/reports/pdf")
    public ResponseEntity<byte[]> pdf(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                      @RequestParam(defaultValue = "month") String groupBy,
                                      @RequestParam(defaultValue = "false") boolean download) {
        byte[] pdf = pdfService.create(reportService.build(from, to, groupBy));
        String fileName = "report-" + from + "-to-" + to + ".pdf";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, (download ? "attachment" : "inline") + "; filename=\"" + fileName + "\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
    }
}
