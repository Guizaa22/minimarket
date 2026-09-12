# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

**2M Market** is a standalone JavaFX 21 desktop application (Java 17) for managing a small market's stock, point-of-sale, sales, suppliers, and employees. Built with Maven. The UI, comments, and Javadoc are in **French** — match that language when editing existing code.

The database backend is **PostgreSQL** (via HikariCP pool). The project was migrated from SQLite and the old SQLite-era troubleshooting docs and one-off scripts have been removed. Current docs: `README.md` (overview), `SETUP_DATABASE.md` (DB setup), `DEPLOIEMENT.md` (production). `schema_postgres.sql` and `RegressionIT.java` still contain comments *explaining* why the schema uses NUMERIC/indexes (design rationale from the migration) — those are intentional.

## Commands

All commands run from the repo root (require JDK 17+ and Maven; a reachable PostgreSQL for running the app and integration tests).

```bash
mvn javafx:run          # Run the application (main class app.MainApp)
mvn compile             # Compile
mvn test                # Run all tests (unit + integration)
mvn package             # Build fat JAR via shade plugin (target/*.jar)

# Run a single test class or method
mvn test -Dtest=ProduitServiceTest
mvn test -Dtest=VenteServiceTest#nomDeLaMethode
```

- **Unit tests** (`*Test.java`, in `service/` and `model/`) mock the DAOs with Mockito — no database needed.
- **Integration tests** (`*IT.java`, in `dao/`) need a real PostgreSQL. They run against the isolated schema `test_2m` (set via `-DPGSCHEMA=test_2m` in surefire config) and **self-skip** if no database is reachable, so `mvn test` never fails purely for lack of a DB.
- The Windows-native standalone installer (bundled JRE) is built by `tools/build-installer.ps1`, which calls `jpackage` directly — not through Maven.

## Configuration

Connection settings are resolved by `util.ConfigLoader` in strict priority order (highest first):

1. System property (`-Dkey=value`)
2. Environment variable
3. `%APPDATA%\2M-Market\database.properties` (secrets, outside the repo)
4. `config.properties` at repo root (development)
5. `/config.properties` on the classpath

Keys: `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`, or the URL form `DATABASE_URL`, plus `PGSSLMODE`, `DB_POOL_SIZE`, `PGSCHEMA`. **Never put passwords in `config.properties` or the classpath resource** — both are committed/shipped. See `config.properties.example` for documentation of all keys. `util.Config` is the typed accessor over `ConfigLoader`.

There is **no default admin account**: on first launch, `MainApp` prompts the user to create the initial administrator (`DatabaseSetup.createInitialAdmin`).

## Architecture

Strict layered architecture. Dependencies flow **controller → service → dao → database**; skipping layers is a regression the codebase was deliberately refactored away from.

- **`app.MainApp`** — JavaFX entry point. On startup: tests the DB connection, applies the schema, ensures an admin exists, then loads `Connexion.fxml`. `stop()` closes the Hikari pool and background tasks.
- **`controller/`** — JavaFX FXML controllers, one per screen (paired with `resources/view/*.fxml`). Presentation only; business logic lives in services. Instantiated by JavaFX itself.
- **`service/`** — Business rules and validation. This is where invariants live (e.g. `ProduitService` validates the buy/sell price ratio; `VenteService.encaisser` builds and records a sale transactionally). Services have a no-arg constructor wiring real DAOs plus a constructor taking DAO dependencies for test injection.
- **`dao/`** — JDBC data access, one DAO per table. `dao.DBConnector` owns the HikariCP pool: `getConnection()` returns a **distinct pooled connection each call** that the caller must close (try-with-resources) — closing only returns it to the pool. Do not reintroduce a shared static connection; it breaks concurrent transactions.
- **`model/`** — Plain domain objects (`Produit`, `Vente`, `Categorie`, `Utilisateur`, …).
- **`util/`** — Config loading, `DatabaseSetup` (runs `/database/schema_postgres.sql` on every startup — idempotent), `SecurityUtil` (BCrypt), PDF export, ticket printing.
- **`ui/`** — Reusable JavaFX widgets and dialogs (numeric pad, toasts, tabac sale dialog, background task runner).

### Session state

`service.SessionContext.get()` is the single shared session holder (logged-in `Utilisateur` + current `Panier`), replacing state that was previously scattered and could diverge across controllers. The public constructor + `reset()` exist for isolated test contexts.

### Category types (important domain rule)

`model.TypeCategorie` drives stock behavior and is stored explicitly on the category — it is **not** inferred from the category name (a past bug: renaming a category silently changed how stock decremented):

- `Standard` — ordinary product.
- `Tabac` — sold by the pack, can also be sold per unit.
- `FrakCigarette` — cigarettes sold per unit that decrement the associated pack's stock.

Tabac sales flow through `VenteService`, `VenteDAO`, and `ui.DialogueVenteTabac`; see `VenteTabacIT` for the expected behavior.

### Schema & money

`src/main/resources/database/schema_postgres.sql` is the source of truth for the DB and is executed on startup. Monetary amounts are `NUMERIC(12,3)` (use `BigDecimal` in Java, never `double`/`float`), timestamps are real `TIMESTAMP`. All stock/sale writes should go through `ProduitService`/`VenteService` so audit logging (`AuditService` / `audit_log`) and validation stay consistent.

## Logging

SLF4J with Logback (`resources/logback.xml`) — console plus a rotating file under `%APPDATA%\2M-Market\logs`. The `slf4j-api` version is pinned in `pom.xml` because HikariCP pulls in an incompatible 1.7.x transitively.
