#pragma once

#include "../utils/JsonUtils.h"

class ReportRepository;

class ReportService {
public:
    explicit ReportService(ReportRepository& repository);

    json getSummary() const;

private:
    ReportRepository& repository_;
};
