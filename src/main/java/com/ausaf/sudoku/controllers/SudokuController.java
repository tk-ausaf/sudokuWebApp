package com.ausaf.sudoku.controllers;

import com.ausaf.sudoku.dto.AttemptSummary;
import com.ausaf.sudoku.dto.AutosaveRequest;
import com.ausaf.sudoku.dto.LeaderboardEntry;
import com.ausaf.sudoku.dto.PuzzleResponse;
import com.ausaf.sudoku.dto.ResumeResponse;
import com.ausaf.sudoku.dto.SubmitRequest;
import com.ausaf.sudoku.dto.SubmitResponse;
import com.ausaf.sudoku.security.CallerIdentity;
import com.ausaf.sudoku.security.GuestCookieService;
import com.ausaf.sudoku.service.LeaderboardService;
import com.ausaf.sudoku.service.SudokuService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Sudoku gameplay and leaderboard endpoints; most are guest-allowed (see {@link com.ausaf.sudoku.security.SecurityConfig}). */
@RestController
@RequestMapping("sudoku")
public class SudokuController {

    @Autowired
    private SudokuService sudokuService;

    @Autowired
    private LeaderboardService leaderboardService;

    /** Resumes the caller's in-progress puzzle, or generates a brand new one. Guest-allowed. */
    @GetMapping("puzzle")
    public PuzzleResponse getPuzzle(HttpServletRequest request) {
        return sudokuService.getPuzzleForUser(currentIdentity(request));
    }

    /** Checks a proposed solution against one owned attempt. Guest-allowed. */
    @PostMapping("submit")
    public SubmitResponse submit(@RequestBody SubmitRequest submitRequest, HttpServletRequest request) {
        return sudokuService.submitSolution(currentIdentity(request), submitRequest.getAttemptId(), submitRequest.getGrid());
    }

    /** Lists the caller's past attempts, most recent first. Guest-allowed. */
    @GetMapping("attempts")
    public List<AttemptSummary> getAttempts(HttpServletRequest request) {
        return sudokuService.getHistory(currentIdentity(request));
    }

    /** Fetches one owned attempt's clue grid and saved progress so it can be resumed. Guest-allowed. */
    @GetMapping("attempts/{attemptId}")
    public ResumeResponse resumeAttempt(@PathVariable String attemptId, HttpServletRequest request) {
        return sudokuService.resumeAttempt(currentIdentity(request), attemptId);
    }

    /** Persists live in-progress cell values for one owned attempt. Guest-allowed. */
    @PatchMapping("attempts/{attemptId}/grid")
    public ResponseEntity<Void> autosave(@PathVariable String attemptId,
                                          @RequestBody AutosaveRequest autosaveRequest,
                                          HttpServletRequest request) {
        sudokuService.autosaveGrid(currentIdentity(request), attemptId, autosaveRequest.getGrid());
        return ResponseEntity.noContent().build();
    }

    /** Public: top solvers for {@code period} (daily/weekly/monthly/yearly), no identity needed. */
    @GetMapping("leaderboard")
    public List<LeaderboardEntry> getLeaderboard(@RequestParam String period) {
        return leaderboardService.getLeaderboard(period);
    }

    /** Resolves the caller as a real user (from SecurityContext) or, failing that, a guest (from the request attribute). */
    private CallerIdentity currentIdentity(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return CallerIdentity.ofUser(auth.getName());
        }
        Object anonymousId = request.getAttribute(GuestCookieService.REQUEST_ATTR);
        return CallerIdentity.ofGuest(anonymousId != null ? anonymousId.toString() : null);
    }
}