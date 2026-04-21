#include "RecipeController.h"
#include "../services/AuthService.h"
#include "../services/RecipeService.h"

#include "../../third_party/httplib.h"

RecipeController::RecipeController(AuthService& authService, RecipeService& recipeService)
    : authService_(authService), recipeService_(recipeService) {}

void RecipeController::registerRoutes(httplib::Server& server) const {
    server.Get("/api/recipes", [this](const httplib::Request& request, httplib::Response& response) {
        handleJson(request, response, [this](const httplib::Request& innerRequest) {
            const auto user = authService_.requireUser(innerRequest);
            return recipeService_.listRecipes(user, innerRequest);
        });
    });

    server.Get(R"(/api/recipes/(\d+))", [this](const httplib::Request& request, httplib::Response& response) {
        handleJson(request, response, [this](const httplib::Request& innerRequest) {
            const auto user = authService_.requireUser(innerRequest);
            return recipeService_.getRecipe(std::stoi(innerRequest.matches[1].str()), user);
        });
    });

    server.Get(R"(/api/recipes/(\d+)/versions)", [this](const httplib::Request& request, httplib::Response& response) {
        handleJson(request, response, [this](const httplib::Request& innerRequest) {
            const auto user = authService_.requireUser(innerRequest);
            return recipeService_.getVersions(std::stoi(innerRequest.matches[1].str()), user);
        });
    });

    server.Post("/api/recipes", [this](const httplib::Request& request, httplib::Response& response) {
        handleJson(request, response, [this](const httplib::Request& innerRequest) {
            const auto user = authService_.requireUser(innerRequest);
            return recipeService_.createRecipe(user, parseJsonBody(innerRequest), innerRequest.remote_addr);
        }, 201);
    });

    server.Put(R"(/api/recipes/(\d+))", [this](const httplib::Request& request, httplib::Response& response) {
        handleJson(request, response, [this](const httplib::Request& innerRequest) {
            const auto user = authService_.requireUser(innerRequest);
            return recipeService_.updateRecipe(std::stoi(innerRequest.matches[1].str()), user, parseJsonBody(innerRequest), innerRequest.remote_addr);
        });
    });

    server.Post(R"(/api/recipes/(\d+)/share)", [this](const httplib::Request& request, httplib::Response& response) {
        handleJson(request, response, [this](const httplib::Request& innerRequest) {
            const auto user = authService_.requireUser(innerRequest);
            return recipeService_.shareRecipe(std::stoi(innerRequest.matches[1].str()), user, parseJsonBody(innerRequest), innerRequest.remote_addr);
        });
    });
}
