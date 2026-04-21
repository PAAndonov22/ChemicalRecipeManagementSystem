#include "controllers/AuditController.h"
#include "controllers/AuthController.h"
#include "controllers/RecipeController.h"
#include "controllers/ReportController.h"
#include "database/Database.h"
#include "database/Migrations.h"
#include "repositories/AuditRepository.h"
#include "repositories/AuthRepository.h"
#include "repositories/RecipeRepository.h"
#include "repositories/ReportRepository.h"
#include "security/PasswordHasher.h"
#include "security/TokenService.h"
#include "services/AuditService.h"
#include "services/AuthService.h"
#include "services/RecipeService.h"
#include "services/ReportService.h"
#include "utils/JsonUtils.h"

#include "../third_party/httplib.h"

#include <filesystem>
#include <iostream>

int main() {
    try {
        const std::filesystem::path projectRoot = std::filesystem::path(CRMS_PROJECT_ROOT);
        const std::filesystem::path databasePath = projectRoot / "data" / "crms.db";
        const std::filesystem::path frontendPath = projectRoot / "frontend";

        Database database(databasePath.string());
        PasswordHasher passwordHasher;
        TokenService tokenService;

        migrations::apply(database, passwordHasher);

        AuthRepository authRepository(database);
        AuditRepository auditRepository(database);
        RecipeRepository recipeRepository(database);
        ReportRepository reportRepository(database);

        AuditService auditService(auditRepository);
        AuthService authService(authRepository, auditService, passwordHasher, tokenService);
        RecipeService recipeService(recipeRepository, authRepository, auditService);
        ReportService reportService(reportRepository);

        AuthController authController(authService);
        RecipeController recipeController(authService, recipeService);
        AuditController auditController(authService, auditService);
        ReportController reportController(authService, reportService);

        httplib::Server server;
        server.set_mount_point("/", frontendPath.string());
        server.set_file_extension_and_mimetype_mapping("js", "application/javascript");
        server.set_file_extension_and_mimetype_mapping("css", "text/css");

        server.Get("/api/health", [](const httplib::Request&, httplib::Response& response) {
            sendJson(response, {
                {"status", "ok"},
                {"service", "Chemical Recipe Management System"}
            });
        });

        authController.registerRoutes(server);
        recipeController.registerRoutes(server);
        auditController.registerRoutes(server);
        reportController.registerRoutes(server);

        server.set_error_handler([](const httplib::Request& request, httplib::Response& response) {
            if (request.path.rfind("/api/", 0) == 0 && response.status == 404) {
                sendJson(response, {
                    {"error", "API route not found."},
                    {"path", request.path}
                }, 404);
            }
        });

        std::cout << "CRMS server listening on http://localhost:8080" << std::endl;
        if (!server.listen("0.0.0.0", 8080)) {
            std::cerr << "Unable to bind server to port 8080." << std::endl;
            return 1;
        }
        return 0;
    } catch (const std::exception& exception) {
        std::cerr << "Fatal startup error: " << exception.what() << std::endl;
        return 1;
    }
}
