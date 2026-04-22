#include "AuthController.h"
#include "../services/AuthService.h"

#include "../../third_party/httplib.h"

AuthController::AuthController(AuthService& authService) : authService_(authService) {}

void AuthController::registerRoutes(httplib::Server& server) const {
    server.Post("/api/auth/register", [this](const httplib::Request& request, httplib::Response& response) {
        handleJson(request, response, [this](const httplib::Request& innerRequest) {
            return authService_.registerUser(parseJsonBody(innerRequest), innerRequest.remote_addr);
        }, 201);
    });

    server.Post("/api/auth/login", [this](const httplib::Request& request, httplib::Response& response) {
        handleJson(request, response, [this](const httplib::Request& innerRequest) {
            return authService_.login(parseJsonBody(innerRequest), innerRequest.remote_addr);
        });
    });

    server.Post("/api/auth/logout", [this](const httplib::Request& request, httplib::Response& response) {
        handleJson(request, response, [this](const httplib::Request& innerRequest) {
            return authService_.logout(innerRequest, innerRequest.remote_addr);
        });
    });

    server.Get("/api/auth/me", [this](const httplib::Request& request, httplib::Response& response) {
        handleJson(request, response, [this](const httplib::Request& innerRequest) {
            return authService_.currentUser(innerRequest);
        });
    });

    server.Put("/api/account/profile", [this](const httplib::Request& request, httplib::Response& response) {
        handleJson(request, response, [this](const httplib::Request& innerRequest) {
            return authService_.updateProfile(innerRequest, parseJsonBody(innerRequest), innerRequest.remote_addr);
        });
    });

    server.Put("/api/account/password", [this](const httplib::Request& request, httplib::Response& response) {
        handleJson(request, response, [this](const httplib::Request& innerRequest) {
            return authService_.changePassword(innerRequest, parseJsonBody(innerRequest), innerRequest.remote_addr);
        });
    });

    server.Get("/api/account/settings", [this](const httplib::Request& request, httplib::Response& response) {
        handleJson(request, response, [this](const httplib::Request& innerRequest) {
            return authService_.getSettings(innerRequest);
        });
    });

    server.Put("/api/account/settings", [this](const httplib::Request& request, httplib::Response& response) {
        handleJson(request, response, [this](const httplib::Request& innerRequest) {
            return authService_.updateSettings(innerRequest, parseJsonBody(innerRequest), innerRequest.remote_addr);
        });
    });

    server.Get("/api/account/sessions", [this](const httplib::Request& request, httplib::Response& response) {
        handleJson(request, response, [this](const httplib::Request& innerRequest) {
            return authService_.listSessions(innerRequest);
        });
    });

    server.Delete(R"(/api/account/sessions/(\d+))", [this](const httplib::Request& request, httplib::Response& response) {
        handleJson(request, response, [this](const httplib::Request& innerRequest) {
            return authService_.revokeSession(std::stoi(innerRequest.matches[1].str()), innerRequest, innerRequest.remote_addr);
        });
    });

    server.Get("/api/users", [this](const httplib::Request& request, httplib::Response& response) {
        handleJson(request, response, [this](const httplib::Request& innerRequest) {
            authService_.requireUser(innerRequest, {"Admin", "Chemist"});
            return authService_.listUsers();
        });
    });

    server.Get("/api/admin/users", [this](const httplib::Request& request, httplib::Response& response) {
        handleJson(request, response, [this](const httplib::Request& innerRequest) {
            return authService_.listAdminUsers(innerRequest);
        });
    });

    server.Put(R"(/api/admin/users/(\d+))", [this](const httplib::Request& request, httplib::Response& response) {
        handleJson(request, response, [this](const httplib::Request& innerRequest) {
            return authService_.updateAdminUser(std::stoi(innerRequest.matches[1].str()), innerRequest, parseJsonBody(innerRequest), innerRequest.remote_addr);
        });
    });

    server.Post(R"(/api/admin/users/(\d+)/reset-password)", [this](const httplib::Request& request, httplib::Response& response) {
        handleJson(request, response, [this](const httplib::Request& innerRequest) {
            return authService_.resetAdminPassword(std::stoi(innerRequest.matches[1].str()), innerRequest, parseJsonBody(innerRequest), innerRequest.remote_addr);
        });
    });
}
