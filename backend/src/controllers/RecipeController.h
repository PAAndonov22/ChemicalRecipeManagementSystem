#pragma once

namespace httplib {
class Server;
}

class AuthService;
class RecipeService;

class RecipeController {
public:
    RecipeController(AuthService& authService, RecipeService& recipeService);

    void registerRoutes(httplib::Server& server) const;

private:
    AuthService& authService_;
    RecipeService& recipeService_;
};
