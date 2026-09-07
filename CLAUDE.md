# Project conventions

## Javadoc

Every class, interface, and enum in `src/main/java` and `src/test/java` must have a
class-level Javadoc comment (`/** ... */` above the declaration, above any annotations
like `@Service`/`@RestController`) describing what it is and its role in the app -
one to three sentences, not a restatement of the class name.

Add a method-level Javadoc comment to a public method only when its behavior, contract,
or a non-obvious constraint isn't already clear from the signature - skip trivial
getters/setters and self-explanatory controller endpoint methods.

This applies to new classes as they're created, not just a one-time backfill.

## Logging

Every `@Service`/`@Component`/`@RestController`/`@Controller`/filter class with non-trivial logic
gets Lombok's `@Slf4j` and log statements at its meaningful steps - not every line, but every
business event, decision branch, and error path a step's logic produces. This applies to new code
as it's written, not just the one-time backfill already done across the app.

Guidelines:
- **INFO**: a real business event completed (a puzzle assigned/solved, a user registered/logged
  in, a game created/started/ended, attempts reassigned on login).
- **WARN**: an expected-but-notable failure - a validation rejection, a 404/409/400 about to be
  thrown, a rejected multiplayer move, a failed login. Something a bug or an attacker could cause,
  not necessarily one.
- **ERROR**: something unexpected - an exception that isn't a deliberate `ResponseStatusException`
  (see `GlobalExceptionHandler`, which logs every one of these with its stack trace so nothing is
  silently lost) or an `@Async` method's uncaught exception (see `AsyncConfig`).
- **DEBUG**: routine/high-frequency detail not worth INFO in production (autosave ticks, a correct
  move's cell, per-move persistence, expired-token validation failures) - useful with
  `LOG_LEVEL=DEBUG` when troubleshooting, otherwise silent.
- Never log a password, full JWT/token, or full cookie value. Log identifiers instead
  (`ResolvedIdentity.toLogString()`, a username, a game/attempt id) - these already appear in
  cookies/URLs and aren't secrets themselves.
- Don't duplicate the same event at two layers - `RequestCorrelationFilter` already logs every
  HTTP request/response (method, path, status, duration) with a correlation id in the MDC, so
  controllers stay thin and business-event logging belongs in the service layer.