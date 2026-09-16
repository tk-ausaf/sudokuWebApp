package com.ausaf.sudoku.repository.visitor;

import com.ausaf.sudoku.entity.DailyVisit;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/** Spring Data MongoDB repository for {@link DailyVisit} documents. */
@Repository
public interface DailyVisitRepository extends MongoRepository<DailyVisit, String> {
    /** @return true if this identity has already been recorded as visiting on this date. */
    boolean existsByDateAndVisitorId(String date, String visitorId);

    /** @return the number of distinct visitors recorded for this date. */
    long countByDate(String date);
}