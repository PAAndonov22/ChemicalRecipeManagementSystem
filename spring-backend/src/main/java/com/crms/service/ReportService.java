package com.crms.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.crms.repository.ReportRepository;
import com.crms.util.ValidationUtils;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class ReportService {
    private final ReportRepository repository;

    public ReportService(ReportRepository repository) {
        this.repository = repository;
    }

    public Map<String, Object> getSummary(HttpServletRequest request) {
        return repository.buildSummary(
            ValidationUtils.optionalQuery(request, "statusSort"),
            ValidationUtils.optionalQuery(request, "ingredientSort"),
            ValidationUtils.optionalQuery(request, "activitySort")
        );
    }
}
