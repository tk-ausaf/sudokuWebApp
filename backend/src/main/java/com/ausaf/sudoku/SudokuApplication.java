package com.ausaf.sudoku;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Spring Boot entry point for the Sudoku web app. */
@SpringBootApplication
public class SudokuApplication {

	/** Boots the embedded servlet container and Spring context. */
	public static void main(String[] args) {
		SpringApplication.run(SudokuApplication.class, args);
	}

}