# Repository Guidelines

## Snapshot & Stack
- Java Spring Boot 3.2 service that backs the Imgfloat livestream overlays, admin UI, and dashboard.
- Uses Spring MVC controllers, WebSocket/STOMP broadcasts (`SimpMessagingTemplate`), OAuth2/Twitch authentication, and scheduled/async services (`@EnableScheduling`, `@EnableAsync` in `ImgfloatApplication`).
- Media-heavy assets are stored on disk (`assets/`, `previews/`), processed with `ffmpeg`/`ffprobe`, and surfaced through `static/js` / Thymeleaf templates under `src/main/resources`.

## Architecture & Modules
- `src/main/java/dev/kruhlmann/imgfloat/config`: Spring configuration beans (upload limits, security overrides, asset storage wiring).
- `controller`: REST + view controllers keep endpoints thin and delegate work to services and repositories.
- `service`: business logic (asset management, marketplace seeds, git info, Twitch emote syncing, audit logging, cleanup, media optimization, etc.). `service.media` contains ffmpeg helpers and media detection.
- `repository`: Spring Data JPA interfaces for the Imgfloat DB (`Asset`, `Channel`, `ScriptAsset`, etc.) and a separate audit DB under `repository.audit`.
- `model`: `db` packages mirror schema entities; `api.request`/`response` hold DTOs used by controllers.
- `templates`: Thymeleaf views (`admin.html`, `broadcast.html`, `dashboard.html`, etc.).
- `static`: frontend JavaScript modules (`static/js/admin`, `static/js/broadcast/*`, `static/js/customAssets.js`, etc.), CSS, icons, and broadcast worker scripts. Keep new assets next to the feature they support.
- `src/main/resources/db/migration` and `db/audit`: Flyway migration scripts that must stay in sync with the SQLite schemas referenced by `IMGFLOAT_DB_PATH` and `IMGFLOAT_AUDIT_DB_PATH`.

## Environment & Configuration
- `.env` files are only loaded when you run `make run`; shelling `mvn spring-boot:run` directly requires manually exporting each variable.
- Required environment variables (set them via `.env` locally or through your deployment tooling):

  | Variable | Purpose |
  | --- | --- |
  | `IMGFLOAT_ASSETS_PATH` | Base filesystem directory for uploaded assets. `AssetStorageService` sanitizes broadcaster segments under this path. |
  | `IMGFLOAT_PREVIEWS_PATH` | Where generated previews (PNG thumbnails) go. |
  | `IMGFLOAT_DB_PATH` | SQLite file used by the main Flyway-managed schema (`db/migration`). |
  | `IMGFLOAT_AUDIT_DB_PATH` | SQLite file for audit logs (`db/audit`) and session storage. |
  | `IMGFLOAT_INITIAL_TWITCH_USERNAME_SYSADMIN` | Twitch login that becomes the initial sysadmin and can manage all channels. |
  | `IMGFLOAT_GITHUB_CLIENT_OWNER/REPO/VERSION` | GitHub release coordinates used by `GithubReleaseService` to build download links for the client bundle. |
  | `IMGFLOAT_TOKEN_ENCRYPTION_KEY` | Base64/Base64URL-encoded 32-byte key used by Spring Security to encrypt OAuth tokens at rest. |
  | `SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE` / `MAX_REQUEST_SIZE` | Control upload limits (defaults to 10MB in `Makefile`, also read by `UploadLimitsConfig` which exposes `uploadLimitBytes` to controllers/services). |
  | `TWITCH_CLIENT_ID` & `TWITCH_CLIENT_SECRET` | Used by Spring Security’s OAuth2 client registration in `application.yml`. |

- Optional/operational variables:

  | Variable | Purpose |
  | --- | --- |
  | `IMGFLOAT_COMMIT_URL_PREFIX` | Enables the commit chip in the UI (`GitInfoService`) when paired with `git-commit-id-plugin`. |
  | `IMGFLOAT_DOCS_URL` | Base URL for documentation links rendered in templates. |
  | `IMGFLOAT_IS_STAGING` | Set to `1` to surface a staging banner across non-broadcast pages. Defaults to `0`. |
  | `IMGFLOAT_MARKETPLACE_SCRIPTS_PATH` | Directory containing marketplace seed directories (`MarketplaceScriptSeedLoader` reads metadata, source.js, logo, attachments). `doc/marketplace-scripts` holds examples. |
  | `IMGFLOAT_SYSADMIN_CHANNEL_ACCESS_ENABLED` | Toggle to let sysadmins manage every channel without being added as an admin. |
  | `TWITCH_REDIRECT_URI` | Overrides the default redirect URI for OAuth (`{baseUrl}/login/oauth2/code/twitch`). |
  | `IMGFLOAT_TOKEN_ENCRYPTION_PREVIOUS_KEYS` | Comma-delimited base64 keys to decrypt tokens created before rotating `IMGFLOAT_TOKEN_ENCRYPTION_KEY`. |

- `AssetStorageService` will fall back to system temp dirs (`java.io.tmpdir/imgfloat-assets` & `-previews`) if `IMGFLOAT_ASSETS_PATH`/`PREVIEWS_PATH` are unset, but storing data on persistent storage is required for production.
- `UploadLimitsConfig` defaults to 50MB/100MB for tests if the environment properties are absent; production should honor the `spring.servlet.multipart.*` values you expose.

## Build, Run, & Watch
- `make build` / `mvn compile`: compile the server.
- `make run`: sources `.env` (if present) and runs `mvn spring-boot:run`. Use this for local dev; hot reload works via `spring-devtools`.
- `make watch`: compiles continuously using `entr` (sleep loop watches `src/main`). Requires `entr` installed and you must refresh the browser manually after the compile succeeds.
- `make test` / `mvn test`: run the JUnit 5 + Mockito suite (including integration tests that spin up the Spring context). Run this before pushing changes that touch controllers, services, or persistence logic.
- `make package` / `mvn clean package`: clean build that packages the runnable JAR.
- `springdoc-openapi` is on the classpath; the automated Swagger UI can be helpful for understanding the available REST endpoints.

## Database & Persistence
- The main SQLite DB (`IMGFLOAT_DB_PATH`) holds channel, user, asset, marketplace, and settings tables. Flyway (configured in `application.yml`) runs migrations from `classpath:db/migration`. Don’t bypass these migration scripts when updating schemas.
- Audit logs live in the separate `IMGFLOAT_AUDIT_DB_PATH` with migrations under `src/main/resources/db/audit`. The audit DB also stores Spring Session tables; `spring.session.jdbc.initialize-schema` is set to `never`, so the Flyway scripts **must** include the session table definitions.
- `AuditLogService` sanitizes log entries before persisting them and is used by controllers such as `AuditLogApiController` and `AccountService`.

## Media & Asset Handling
- `ChannelDirectoryService` handles uploads. It enforces `uploadLimitBytes` (derived from `spring.servlet.multipart.max-file-size`), normalizes broadcaster names, and records audit events via `AuditLogService`.
- Uploaded assets go through `MediaDetectionService`, `MediaOptimizationService`, and `AssetStorageService` which writes to `IMGFLOAT_ASSETS_PATH`. Previews (PNGs) are written to `IMGFLOAT_PREVIEWS_PATH`.
- `AssetStorageService` sanitizes broadcaster segments (`sanitizeUserSegment`) to avoid traversal and auto-creates directories. When deleting, `AssetCleanupService` runs asynchronously after startup to remove orphan files that are not referenced by any DB record.
- Media processing uses `ffmpeg`/`ffprobe` via `FfmpegService`. Make sure those binaries are available on the system; otherwise video/preview transcodes and APNG → GIF conversions will fail with warnings logged from `FfmpegService`.

## Marketplace Scripts & Allowed Domains
- Marketplace seeds live under the path configured by `IMGFLOAT_MARKETPLACE_SCRIPTS_PATH`. Each subfolder is a listing identifier and **must** contain `metadata.json` and `source.js`. Optional files: `logo.png` (renders as the marketplace badge) and `attachments/` for extra files (filenames must be unique per script).
- `metadata.json` combines `name`, optional `description`, optional `broadcaster`, and optional `allowedDomains`. Allowed domains control the `fetch` sandbox in the broadcast worker and are normalized via `ChannelDirectoryService.normalizeAllowedDomains`. Valid entries are hostname or hostname+port (regex `^[a-z0-9.-]+(?::[0-9]{1,5})?$`), de-duplicated, lower-cased, and capped at 32 entries per script.
- Seeds are cached at startup; `MarketplaceScriptSeedLoader` exposes `listEntriesForQuery` and `findById` so the frontend can show the marketplace catalog without hitting Twitch/SevenTV endpoints.
- Example scripts are shipped under `doc/marketplace-scripts/`.

## Security, Auth, & Tokens
- Spring Security OAuth2 login is configured in `application.yml` under `spring.security.oauth2.client`. Twitch is the only configured provider; scopes include `user:read:email` and `moderation:read`.
- OAuth tokens are encrypted using `IMGFLOAT_TOKEN_ENCRYPTION_KEY`; rotate keys by setting `IMGFLOAT_TOKEN_ENCRYPTION_PREVIOUS_KEYS` to a comma list of old keys (oldest last) before swapping in a new `IMGFLOAT_TOKEN_ENCRYPTION_KEY`.
- `IMGFLOAT_INITIAL_TWITCH_USERNAME_SYSADMIN` seeds the first sysadmin; subsequent sysadmins can be managed via the UI or API. To allow sysadmins to hop between channels without being channel admins, set `IMGFLOAT_SYSADMIN_CHANNEL_ACCESS_ENABLED=true`.
- `GitInfoService` will expose a commit link if `IMGFLOAT_COMMIT_URL_PREFIX` is configured and `git.properties` is present (generated by the `git-commit-id-plugin`), otherwise it falls back to running `git` directly.
- `GithubReleaseService` fails fast when `IMGFLOAT_GITHUB_CLIENT_OWNER`, `IMGFLOAT_GITHUB_CLIENT_REPO`, or `IMGFLOAT_GITHUB_CLIENT_VERSION` are missing; these powers the download link in the UI.

## Frontend Notes
- Broadcast client code lives in `src/main/resources/static/js/broadcast/` (renderer, workers, runtime helpers). When editing overlay scripts, keep worker changes in `script-worker.js` and keep main page logic in `renderer.js`.
- Admin/dashboard JS modules (`customAssets.js`, `settings.js`, `channels.js`, etc.) are plain ES modules bundled through Thymeleaf templates, so keep related CSS/HTML under `static/css` and `templates`.
- Templates render dynamic data via controllers such as `ViewController`, which also injects `uploadLimitBytes`, version info (`VersionService`), and feature flags (staging banner, docs URL, commit chip wrapped in `GitInfoService`/`GithubReleaseService` values).

## Testing & Validation
- Tests are under `src/test/java/dev/kruhlmann/imgfloat`. Controller integration tests (e.g., `ChannelApiIntegrationTest`) boot up the application context—mock external services carefully or rely on the in-memory SQLite test DB created from the Flyway scripts.
- Service unit tests (e.g., `ChannelDirectoryServiceTest`) cover asset normalization, allowed domain semantics, and marketplace seed behavior. Add targeted tests for new service logic, API validations, or asset handling (uploads, attachments, downloads).
- Run `make test` before shipping changes; it also generates JaCoCo reports via the Maven plugin.

## Coding Style & Contributions
- Java code uses 4-space indentation, `UpperCamelCase` for types, `lowerCamelCase` for fields/methods, and `UPPER_SNAKE_CASE` for constants. DTOs often use `record`s when immutability is feasible (`ScriptMarketplaceEntry`, `MarketplaceScriptSeedLoader.SeedScript`, etc.).
- Prefer constructor injection for Spring components. Controllers should remain thin and delegate to service-layer beans; services orchestrate repositories, asset storage, storage cleanup, Git/Twitch helpers, and messaging (websockets/events).
- Keep assets close to their feature (e.g., `static/js/broadcast/` for overlay code, `templates/` for the Thymeleaf view referencing that script).
- Avoid path traversal by relying on existing sanitizers (`AssetStorageService.sanitizeUserSegment`). When adding new file paths, reuse `safeJoin`/`sanitizeUserSegment` patterns.
- When syncing marketplace favorites or script attachments, obey `ChannelDirectoryService.loadScriptAttachments` semantics—attachments are stored in `script_asset_attachment` records and are cleaned up when orphaned.

## Operational Tips
- `AssetCleanupService` runs asynchronously once per startup (after `ApplicationReadyEvent`) and purges disk files whose IDs are not referenced by any DB row; do not rely on manual cleanup unless debugging.
- `VersionService` looks for `META-INF/maven/dev.kruhlmann/imgfloat/pom.properties` first, falls back to parsing `pom.xml` (useful when running from source), and throws if no version is available—keep your `pom.version` accurate for release builds.
- `EmoteSyncScheduler`, `SevenTvEmoteService`, `TwitchEmoteService`, and `TwitchAppAccessTokenService` orchestrate Twitch/7TV emote catalogs and should be aware of scheduling constraints if you modify emote ingestion.
- Static files, marketplace metadata, and assets are best tested locally by running `make run` with the appropriate env variables and by refreshing the admin/broadcast UI manually.
