# Repository Guidelines

## Project Structure & Module Organization
- Java Spring Boot service under `src/main/java` (controllers, services, repositories, models); configuration in `src/main/resources`.
- Frontend overlays and admin UI assets live in `src/main/resources/static/js` and `src/main/resources/templates` (Thymeleaf).
- Tests are in `src/test/java`; sample assets and previews are stored under `assets/` and `previews/` when running locally.
- Marketplace seed content (if used) sits at the path pointed to `IMGFLOAT_MARKETPLACE_SCRIPTS_PATH`, each with `metadata.json`, `source.js`, optional `logo.png`, and `attachments/`.

## Build, Test, and Development Commands
- `make build` / `mvn compile`: compile the application.
- `make run`: load `.env` (if present) and start the Spring Boot app on port 8080 with required env vars.
- `make watch`: recompile on source changes (needs `entr`); restart browser manually.
- `make test` / `mvn test`: run the full test suite.
- `make package`: clean build a runnable JAR.

## Coding Style & Naming Conventions
- Java 17, Spring Boot 3.x. Follow standard Java conventions: 4-space indentation, `UpperCamelCase` for types, `lowerCamelCase` for methods/fields, constants in `UPPER_SNAKE_CASE`.
- Keep controllers thin, delegate logic to services, and favor immutable DTOs/records where possible.
- Place web assets next to related features (`static/js` modules, matching templates). Use descriptive filenames (e.g., `broadcast/renderer.js`).
- Prefer constructor injection for Spring components; avoid field injection.

## Testing Guidelines
- Use JUnit 5 with Mockito for unit tests; keep tests under `src/test/java` mirroring package paths.
- Name tests descriptively (`ClassNameTest`, method names expressing behavior).
- For changes touching overlays or asset handling, add tests for repository/service logic and handle edge cases (missing files, bad metadata).
- Run `make test` before opening a PR; add targeted integration tests when altering controllers or WebSocket flows.

## Commit & Pull Request Guidelines
- Commits: concise, present-tense summaries (`Fix script import domain validation`). Group related changes; avoid noisy churn.
- PRs: include a clear summary, linked issue (if any), test results, and screenshots/GIFs when UI changes affect admin or broadcast overlays.
- Call out any config/env changes (new required vars such as `IMGFLOAT_*`) and migration steps.

## Security & Configuration Tips
- Store secrets via environment variables (`.env` only for local dev). Required paths: `IMGFLOAT_ASSETS_PATH`, `IMGFLOAT_PREVIEWS_PATH`, `IMGFLOAT_DB_PATH`, `IMGFLOAT_AUDIT_DB_PATH`.
- When seeding marketplace scripts, ensure `metadata.json` is well-formed; attachment filenames must be unique per script.
- Keep OAuth keys and token encryption keys (`IMGFLOAT_TOKEN_ENCRYPTION_KEY`) in a secret manager for non-local environments.
