package com.crms.config;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private static final String[] SPA_ROUTES = {
        "/login.html",
        "/register.html",
        "/dashboard.html",
        "/recipes.html",
        "/recipe-details.html",
        "/recipe-editor.html",
        "/version-history.html",
        "/reports.html",
        "/settings.html",
        "/admin-users.html",
        "/audit-logs.html"
    };

    private final String frontendLocation;

    public WebConfig(@Value("${crms.frontend.path}") String frontendPath) {
        this.frontendLocation = Path.of(frontendPath).toAbsolutePath().normalize().toUri().toString();
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
        for (String route : SPA_ROUTES) {
            registry.addViewController(route).setViewName("forward:/index.html");
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**").addResourceLocations(frontendLocation);
    }
}
