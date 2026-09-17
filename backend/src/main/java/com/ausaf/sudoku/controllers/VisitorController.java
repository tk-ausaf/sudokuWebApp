package com.ausaf.sudoku.controllers;

import com.ausaf.sudoku.dto.VisitorCountResponse;
import com.ausaf.sudoku.security.CallerIdentity;
import com.ausaf.sudoku.security.GuestSessionFilter;
import com.ausaf.sudoku.service.VisitorCounterService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Daily unique-visitor counter endpoints; both guest-allowed (see {@link com.ausaf.sudoku.security.SecurityConfig}). */
@RestController
@RequestMapping("visitors")
public class VisitorController {

    @Autowired
    private VisitorCounterService visitorCounterService;

    /** Records the caller as a visitor for today (idempotent per identity per day) and returns the updated count. */
    @PostMapping("visit")
    public VisitorCountResponse recordVisit(HttpServletRequest request) {
        visitorCounterService.recordVisit(currentIdentity(request));
        return new VisitorCountResponse(visitorCounterService.getTodayCount());
    }

    /** Today's unique-visitor count so far, without recording a new one. */
    @GetMapping("today")
    public VisitorCountResponse getTodayCount() {
        return new VisitorCountResponse(visitorCounterService.getTodayCount());
    }

    /** Resolves the caller as a real user (from SecurityContext) or, failing that, a guest (from the request attribute). */
    private CallerIdentity currentIdentity(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return CallerIdentity.ofUser(auth.getName());
        }
        Object anonymousId = request.getAttribute(GuestSessionFilter.REQUEST_ATTR);
        return CallerIdentity.ofGuest(anonymousId != null ? anonymousId.toString() : null);
    }
}