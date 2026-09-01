package com.ausaf.sudoku.controllers;

import com.ausaf.sudoku.dto.PuzzleResponse;
import com.ausaf.sudoku.dto.SubmitRequest;
import com.ausaf.sudoku.dto.SubmitResponse;
import com.ausaf.sudoku.service.SudokuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("sudoku")
public class SudokuController {

    @Autowired
    private SudokuService sudokuService;

    @GetMapping("puzzle")
    public PuzzleResponse getPuzzle() {
        return sudokuService.getPuzzleForUser(currentUsername());
    }

    @PostMapping("submit")
    public SubmitResponse submit(@RequestBody SubmitRequest request) {
        return sudokuService.submitSolution(currentUsername(), request.getAttemptId(), request.getGrid());
    }

    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}