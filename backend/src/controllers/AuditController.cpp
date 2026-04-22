#include "AuditController.h"
#include "../services/AuthService.h"
#include "../services/AuditService.h"

#include "../../third_party/httplib.h"

AuditController::AuditController(AuthService& authService, AuditService& auditService)
    : authService_(authService), auditService_(auditService) {}

void AuditController::registerRoutes(httplib::Server& server) const {
    server.Get("/api/audit-logs/export", [this](const httplib::Request& request, httplib::Response& response) {
        try {
            authService_.requireUser(request, {"Admin"});
            response.status = 200;
            response.set_header("Content-Type", "text/csv");
            response.set_header("Content-Disposition", "attachment; filename=\"audit-logs.csv\"");
            response.set_content(auditService_.exportCsv(request), "text/csv");
        } catch (const HttpException& exception) {
            sendJson(response, json{{"error", exception.what()}, {"status", exception.status()}}, exception.status());
        } catch (const std::exception& exception) {
            sendJson(response, json{{"error", "Unexpected server error."}, {"details", exception.what()}}, 500);
        }
    });

    server.Get("/api/audit-logs", [this](const httplib::Request& request, httplib::Response& response) {
        handleJson(request, response, [this](const httplib::Request& innerRequest) {
            authService_.requireUser(innerRequest, {"Admin"});
            return auditService_.listLogs(innerRequest);
        });
    });
}
