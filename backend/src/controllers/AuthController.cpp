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

    server.Get("/api/users", [this](const httplib::Request& request, httplib::Response& response) {
        handleJson(request, response, [this](const httplib::Request& innerRequest) {
            authService_.requireUser(innerRequest, {"Admin", "Chemist"});
            return authService_.listUsers();
        });
    });
}
