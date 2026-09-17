package com.ausaf.sudoku.service;

/** A freshly generated multiplayer puzzle: its clue grid plus the one solution it is guaranteed to have. */
public record GeneratedMultiplayerPuzzle(String clueGrid, String solutionGrid) {
}