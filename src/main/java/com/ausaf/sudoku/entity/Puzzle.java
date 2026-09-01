package com.ausaf.sudoku.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "puzzles")
@NoArgsConstructor
@AllArgsConstructor
public class Puzzle {

    @Id
    private String id;

    private int index;

    /** 81 chars, row-major, '0' marks a blank cell. */
    private String puzzle;

    /** 81 chars, row-major, the fully solved grid. */
    private String solution;
}