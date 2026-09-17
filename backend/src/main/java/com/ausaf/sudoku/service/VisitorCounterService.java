package com.ausaf.sudoku.service;

import com.ausaf.sudoku.entity.DailyVisit;
import com.ausaf.sudoku.repository.visitor.DailyVisitRepository;
import com.ausaf.sudoku.security.CallerIdentity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Tracks unique visitors per calendar day, for both guest and logged-in callers, via
 * {@link IdentityResolver}. "Unique" is per identity (a guest session or an account), not per
 * request - {@link #recordVisit} is safe to call on every app load.
 */
@Slf4j
@Service
public class VisitorCounterService {

    @Autowired
    private DailyVisitRepository visitRepository;

    @Autowired
    private IdentityResolver identityResolver;

    /** Records the caller as having visited today, if they haven't already been counted today. */
    public void recordVisit(CallerIdentity identity) {
        ResolvedIdentity owner = identityResolver.resolve(identity);
        String visitorId = owner.toLogString();
        String today = LocalDate.now().toString();

        if (visitRepository.existsByDateAndVisitorId(today, visitorId)) {
            log.debug("Visitor {} already counted for {}", visitorId, today);
            return;
        }

        DailyVisit visit = new DailyVisit();
        visit.setDate(today);
        visit.setVisitorId(visitorId);
        visit.setRecordedAt(Instant.now());
        visitRepository.save(visit);
        log.info("Recorded new unique visitor ({}) for {}", visitorId, today);
    }

    /** @return the number of distinct visitors recorded so far today. */
    public long getTodayCount() {
        return visitRepository.countByDate(LocalDate.now().toString());
    }
}