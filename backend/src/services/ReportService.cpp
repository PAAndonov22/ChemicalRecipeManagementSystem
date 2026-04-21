#include "ReportService.h"
#include "../repositories/ReportRepository.h"

ReportService::ReportService(ReportRepository& repository) : repository_(repository) {}

json ReportService::getSummary() const {
    return repository_.buildSummary();
}
