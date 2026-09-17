package com.ausaf.sudoku.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Marks one identity (a real account or a guest session) as having visited on one calendar day -
 * one document per (date, visitorId) pair, deduplicated at the application level in
 * {@link com.ausaf.sudoku.service.VisitorCounterService} rather than a DB-level unique index
 * (matching how username uniqueness is enforced elsewhere in this app). The day's unique-visitor
 * count is simply the number of documents for that date.
 */
@Data
@Document(collection = "daily_visits")
@NoArgsConstructor
public class DailyVisit {

    @Id
    private String id;

    /** ISO local date ("yyyy-MM-dd") this visit was recorded on. */
    private String date;

    /** {@code "user:<id>"} or {@code "guest:<id>"} - see {@link com.ausaf.sudoku.service.ResolvedIdentity#toLogString()}. */
    private String visitorId;

    private Instant recordedAt;
}