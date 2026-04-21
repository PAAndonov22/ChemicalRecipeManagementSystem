#include "AuditController.h"
#include "../services/AuthService.h"
#include "../services/AuditService.h"

#include "../../third_party/httplib.h"

AuditController::AuditController(AuthService& authService, AuditService& auditService)
    : authService_(authService), auditService_(auditService) {}

void AuditController::registerRoutes(httplib::Server& server) const {
    server.Get("/api/audit-logs", [this](const httplib::Request& request, httplib::Response& response) {
        handleJson(request, response, [this](const httplib::Request& innerRequest) {
            authService_.requireUser(innerRequest, {"Admin"});
            return auditService_.listLogs(innerRequest);
        });
    });
}
