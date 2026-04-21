#pragma once

namespace httplib {
class Server;
}

class AuthService;
class ReportService;

class ReportController {
public:
    ReportController(AuthService& authService, ReportService& reportService);

    void registerRoutes(httplib::Server& server) const;

private:
    AuthService& authService_;
    ReportService& reportService_;
};
