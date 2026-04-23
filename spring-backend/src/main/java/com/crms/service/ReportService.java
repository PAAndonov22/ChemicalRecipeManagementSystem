package com.crms.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.crms.repository.ReportRepository;

@Service
public class ReportService {
    private final ReportRepository repository;

    public ReportService(ReportRepository repository) {
        this.repository = repository;
    }

    public Map<String, Object> getSummary() {
        return repository.buildSummary();
    }
}
