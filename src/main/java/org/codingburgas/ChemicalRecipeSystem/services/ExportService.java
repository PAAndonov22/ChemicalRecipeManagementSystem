package org.codingburgas.ChemicalRecipeSystem.services;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletResponse;

/** Writes a table as CSV (UTF-8 with BOM, opens correctly in Excel) or XLSX (streamed, so big tables are fine). */
@Service
public class ExportService {
    public void write(String format, String name, List<String> headers, List<List<Object>> rows, HttpServletResponse response)
        throws IOException {
        if ("xlsx".equals(format)) {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + name + ".xlsx\"");
            try (SXSSFWorkbook book = new SXSSFWorkbook(100); OutputStream out = response.getOutputStream()) {
                SXSSFSheet sheet = book.createSheet(name);
                Row head = sheet.createRow(0);
                for (int c = 0; c < headers.size(); c++) {
                    head.createCell(c).setCellValue(headers.get(c));
                }
                int r = 1;
                for (List<Object> row : rows) {
                    Row line = sheet.createRow(r++);
                    for (int c = 0; c < row.size(); c++) {
                        Object v = row.get(c);
                        if (v instanceof Number n) {
                            line.createCell(c).setCellValue(n.doubleValue());
                        } else {
                            line.createCell(c).setCellValue(safe(String.valueOf(v == null ? "" : v)));
                        }
                    }
                }
                book.write(out);
                book.dispose();
            }
            return;
        }
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + name + ".csv\"");
        StringBuilder csv = new StringBuilder("﻿");
        csv.append(String.join(",", headers.stream().map(ExportService::quote).toList())).append("\r\n");
        for (List<Object> row : rows) {
            csv.append(String.join(",", row.stream().map(v -> quote(v == null ? "" : String.valueOf(v))).toList())).append("\r\n");
        }
        response.getOutputStream().write(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** A text cell that starts with = + - @ would run as a formula in Excel: neutralise it. */
    public static String safe(String s) {
        return !s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0 ? "'" + s : s;
    }

    private static String quote(String s) {
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
