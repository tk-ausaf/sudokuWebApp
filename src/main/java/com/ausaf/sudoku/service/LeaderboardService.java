package com.ausaf.sudoku.service;

import com.ausaf.sudoku.dto.LeaderboardEntry;
import com.ausaf.sudoku.entity.User;
import com.ausaf.sudoku.repository.user.UserRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.LimitOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.aggregation.SortOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Global leaderboard ranked by how many puzzles a user has solved within a calendar-aligned
 * UTC window (today / this ISO week / this calendar month / this calendar year). Only attempts
 * owned by a real account count - a guest who never logged in never appears here.
 */
@Service
public class LeaderboardService {

    private static final int TOP_N = 100;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private UserRepository userRepository;

    /**
     * Aggregates the top {@value #TOP_N} users by completed-puzzle count within the given
     * period, resolving each winning userId to a display name via {@link UserRepository}.
     *
     * @param period one of "daily", "weekly", "monthly", "yearly"
     * @throws ResponseStatusException 400 if period isn't one of the four supported values
     */
    public List<LeaderboardEntry> getLeaderboard(String period) {
        LocalDateTime start = periodStart(period);

        MatchOperation match = Aggregation.match(
                Criteria.where("completed").is(true)
                        .and("userId").ne(null)
                        .and("completedAt").gte(start));

        GroupOperation groupByUser = Aggregation.group("userId").count().as("solvedCount");
        SortOperation sortByCount = Aggregation.sort(Sort.Direction.DESC, "solvedCount");
        LimitOperation limit = Aggregation.limit(TOP_N);

        Aggregation aggregation = Aggregation.newAggregation(match, groupByUser, sortByCount, limit);

        List<LeaderboardRow> rows = mongoTemplate.aggregate(aggregation, "puzzle_attempts", LeaderboardRow.class)
                .getMappedResults();

        Map<String, String> namesByUserId = new HashMap<>();
        List<String> userIds = rows.stream().map(LeaderboardRow::getId).toList();
        for (User user : userRepository.findAllById(userIds)) {
            namesByUserId.put(user.getId(), user.getName());
        }

        List<LeaderboardEntry> entries = new ArrayList<>();
        int rank = 1;
        for (LeaderboardRow row : rows) {
            String displayName = namesByUserId.getOrDefault(row.getId(), "Unknown");
            entries.add(new LeaderboardEntry(rank++, displayName, row.getSolvedCount()));
        }
        return entries;
    }

    /** Start of the given calendar-aligned UTC window (today / this ISO week / month / year), inclusive. */
    private LocalDateTime periodStart(String period) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return switch (period) {
            case "daily" -> today.atStartOfDay();
            case "weekly" -> today.with(DayOfWeek.MONDAY).atStartOfDay();
            case "monthly" -> today.withDayOfMonth(1).atStartOfDay();
            case "yearly" -> today.withDayOfYear(1).atStartOfDay();
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "period must be one of: daily, weekly, monthly, yearly");
        };
    }

    /** One grouped aggregation result row: a userId and how many puzzles it solved in the window. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class LeaderboardRow {
        @Id
        private String id;
        private long solvedCount;
    }
}