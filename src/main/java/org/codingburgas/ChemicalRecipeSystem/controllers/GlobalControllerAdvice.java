package org.codingburgas.ChemicalRecipeSystem.controllers;

import java.util.Set;

import org.codingburgas.ChemicalRecipeSystem.config.Permissions;
import org.codingburgas.ChemicalRecipeSystem.dto.UserDto;
import org.codingburgas.ChemicalRecipeSystem.errors.ConflictException;
import org.codingburgas.ChemicalRecipeSystem.errors.ForbiddenException;
import org.codingburgas.ChemicalRecipeSystem.errors.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.RequestContextUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/** Runs for every controller: gives each view the user + permissions, and turns every error into a clean page (no stack trace). */
@ControllerAdvice
public class GlobalControllerAdvice {
    private static final Logger log = LoggerFactory.getLogger(GlobalControllerAdvice.class);

    private final org.codingburgas.ChemicalRecipeSystem.services.NotificationService notifications;

    public GlobalControllerAdvice(org.codingburgas.ChemicalRecipeSystem.services.NotificationService notifications) {
        this.notifications = notifications;
    }

    @ModelAttribute("currentUser")
    public UserDto currentUser(HttpSession session) {
        return (UserDto) session.getAttribute("user");
    }

    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }

    /** "light" / "dark" from the user's settings; null = follow the operating system. */
    @ModelAttribute("uiTheme")
    public String uiTheme(HttpSession session) {
        var prefs = (org.codingburgas.ChemicalRecipeSystem.dto.PreferencesDto) session.getAttribute("prefs");
        return prefs == null || prefs.theme() == org.codingburgas.ChemicalRecipeSystem.models.Theme.SYSTEM ? null : prefs.theme().name().toLowerCase();
    }

    @ModelAttribute("unreadCount")
    public long unreadCount(HttpSession session) {
        UserDto user = (UserDto) session.getAttribute("user");
        return user == null ? 0 : notifications.unread(user.getId());
    }

    /** The current query string without "page" - the pager appends its own page number. */
    @ModelAttribute("qs")
    public String queryWithoutPage(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null ? "" : java.util.Arrays.stream(query.split("&")).filter(p -> !p.isEmpty() && !p.startsWith("page=")).collect(java.util.stream.Collectors.joining("&"));
    }

    /** Names of the permissions of the logged in user, e.g. can.contains('RECIPE_WRITE') in templates. */
    @ModelAttribute("can")
    public Set<String> can(HttpSession session) {
        UserDto user = (UserDto) session.getAttribute("user");
        return user == null ? Set.of() : Permissions.names(user.getRole());
    }

    /** Services throw IllegalArgumentException for bad input -> go back to the same page with the message. */
    @ExceptionHandler(IllegalArgumentException.class)
    public String handleBadInput(IllegalArgumentException exception, HttpServletRequest request) {
        return backWithError(exception.getMessage(), request);
    }

    /** Two people edited the same record: the second one is sent back to the fresh data. */
    @ExceptionHandler({ConflictException.class, ObjectOptimisticLockingFailureException.class})
    public String handleConflict(Exception exception, HttpServletRequest request) {
        String message = exception instanceof ConflictException ? exception.getMessage()
            : "This record was changed by someone else at the same time. Please reload and try again.";
        return backWithError(message, request);
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public String handleTooBig(HttpServletRequest request) {
        return backWithError("The file is larger than 5 MB.", request);
    }

    @ExceptionHandler(NotFoundException.class)
    public ModelAndView handleNotFound(NotFoundException exception, HttpServletResponse response) {
        return errorPage(HttpStatus.NOT_FOUND, exception.getMessage(), response);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ModelAndView handleForbidden(ForbiddenException exception, HttpServletResponse response) {
        return errorPage(HttpStatus.FORBIDDEN, exception.getMessage(), response);
    }

    /** Anything else: log it for the developers, show a neutral page to the user. */
    @ExceptionHandler(Exception.class)
    public ModelAndView handleOther(Exception exception, HttpServletResponse response) {
        if (exception instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.resolve(errorResponse.getStatusCode().value());
            return errorPage(status == null ? HttpStatus.BAD_REQUEST : status, null, response);
        }
        log.error("Unexpected error", exception);
        return errorPage(HttpStatus.INTERNAL_SERVER_ERROR, null, response);
    }

    private static String backWithError(String message, HttpServletRequest request) {
        RequestContextUtils.getOutputFlashMap(request).put("error", message);
        String previousPage = request.getHeader("Referer");
        return "redirect:" + (previousPage != null ? previousPage : "/");
    }

    private static ModelAndView errorPage(HttpStatus status, String detail, HttpServletResponse response) {
        response.setStatus(status.value());
        ModelAndView view = new ModelAndView("error");
        view.addObject("status", status.value());
        view.addObject("error", status.getReasonPhrase());
        view.addObject("detail", detail);
        return view;
    }
}
