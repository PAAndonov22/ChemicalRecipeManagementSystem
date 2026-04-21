#include "ReportController.h"
#include "../services/AuthService.h"
#include "../services/ReportService.h"

#include "../../third_party/httplib.h"

ReportController::ReportController(AuthService& authService, ReportService& reportService)
    : authService_(authService), reportService_(reportService) {}

void ReportController::registerRoutes(httplib::Server& server) const {
    server.Get("/api/reports/summary", [this](const httplib::Request& request, httplib::Response& response) {
        handleJson(request, response, [this](const httplib::Request& innerRequest) {
            authService_.requireUser(innerRequest, {"Admin", "Chemist", "Technician"});
            return reportService_.getSummary();
        });
    });
}
