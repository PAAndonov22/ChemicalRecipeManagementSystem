package org.codingburgas.ChemicalRecipeSystem.services;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.codingburgas.ChemicalRecipeSystem.models.Event;
import org.codingburgas.ChemicalRecipeSystem.models.EventType;
import org.codingburgas.ChemicalRecipeSystem.models.PeriodReport;
import org.codingburgas.ChemicalRecipeSystem.models.ReportRow;
import org.springframework.stereotype.Service;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

/** Turns a report into a PDF file (accounting report). */
@Service
public class PdfService {
    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
    private static final Font HEADING = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
    private static final Font BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 10);

    public byte[] create(PeriodReport report) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4);
        PdfWriter.getInstance(doc, out);
        doc.open();

        doc.add(new Paragraph("Chemical Recipe System - Accounting report", TITLE));
        doc.add(new Paragraph("Period: " + report.from() + " to " + report.to() + "   |   Grouped by: " + report.groupBy(), NORMAL));
        doc.add(new Paragraph("Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")), NORMAL));

        heading(doc, "Summary");
        PdfPTable summary = table("Indicator", "Value");
        row(summary, "Orders", String.valueOf(report.totalOrders()));
        row(summary, "Revenue from orders", money(report.orderRevenue()));
        row(summary, "Events", String.valueOf(report.totalEvents()));
        row(summary, "Money collected from events", money(report.eventRevenue()));
        row(summary, "TOTAL REVENUE", money(report.totalRevenue()));
        doc.add(summary);

        heading(doc, "Revenue by " + report.groupBy());
        PdfPTable byGroup = table("Period", "Orders", "Order revenue", "Events", "Event money", "Total");
        for (ReportRow r : report.rows()) {
            row(byGroup, r.label(), String.valueOf(r.orders()), money(r.orderRevenue()),
                String.valueOf(r.events()), money(r.eventRevenue()), money(r.totalRevenue()));
        }
        doc.add(byGroup);

        heading(doc, "Events by type");
        PdfPTable byType = table("Type", "Count", "Money collected");
        for (EventType type : report.eventCountByType().keySet()) {
            row(byType, type.name(), String.valueOf(report.eventCountByType().get(type)), money(report.eventRevenueByType().get(type)));
        }
        doc.add(byType);

        heading(doc, "All events");
        PdfPTable eventsTable = table("Date", "Title", "Type", "Tickets", "Money");
        for (Event e : report.events()) {
            row(eventsTable, e.getDate().toString(), e.getTitle(), e.getType().name(), String.valueOf(e.getTicketsSold()), money(e.getRevenue()));
        }
        doc.add(eventsTable);

        doc.close();
        return out.toByteArray();
    }

    private void heading(Document doc, String text) {
        Paragraph p = new Paragraph(text, HEADING);
        p.setSpacingBefore(14);
        p.setSpacingAfter(6);
        doc.add(p);
    }

    private PdfPTable table(String... headers) {
        PdfPTable table = new PdfPTable(headers.length);
        table.setWidthPercentage(100);
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, BOLD));
            cell.setBackgroundColor(new Color(224, 234, 248));
            cell.setPadding(5);
            table.addCell(cell);
        }
        return table;
    }

    private void row(PdfPTable table, String... values) {
        for (String value : values) {
            PdfPCell cell = new PdfPCell(new Phrase(value, NORMAL));
            cell.setPadding(4);
            table.addCell(cell);
        }
    }

    private String money(double amount) {
        return "%.2f EUR".formatted(amount);
    }
}
