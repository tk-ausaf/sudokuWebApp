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