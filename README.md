# Imgfloat

A Spring Boot overlay server for Twitch broadcasters and their channel admins. Broadcasters can authorize via Twitch OAuth and invite channel admins to manage images that float over a transparent canvas. Updates are pushed in real time over WebSockets so OBS browser sources stay in sync.

## Features
- Twitch OAuth (OAuth2 login) with broadcaster and channel admin access controls.
- Admin console with Twitch player embed and canvas preview.
- Broadcaster overlay view optimized for OBS browser sources.
- Real-time asset creation, movement, resize, rotation, visibility toggles, and deletion via STOMP/WebSockets.
- In-memory channel directory optimized with lock-free collections for fast updates.
- Optional SSL with local self-signed keystore support.
- Dockerfile, Makefile, CI workflow, and Maven build.
- OpenAPI/Swagger UI docs available at `/swagger-ui.html`.

## Getting started
### Prerequisites
- Java 17+
- Maven 3.9+
- Twitch Developer credentials (Client ID/Secret)

### Local run
```bash
TWITCH_CLIENT_ID=your_id TWITCH_CLIENT_SECRET=your_secret \
TWITCH_REDIRECT_URI=http://localhost:8080/login/oauth2/code/twitch mvn spring-boot:run
```
The default server port is `8080`. Log in via `/oauth2/authorization/twitch`. The redirect URI above is what Twitch should be configured to call for local development.

### Hot reload during development
- The project includes Spring Boot DevTools so Java and Thymeleaf changes trigger a restart automatically when you run `make run` (which now forks the Spring Boot process so devtools can watch the classpath).
- Static assets under `src/main/resources/static` are refreshed through the built-in LiveReload server from DevTools; install a LiveReload browser extension to automatically reload the overlay or dashboard when CSS/JS files change.

### Enable TLS locally
```bash
make ssl
SSL_ENABLED=true SSL_KEYSTORE_PATH=file:$(pwd)/local/keystore.p12 \
TWITCH_CLIENT_ID=your_id TWITCH_CLIENT_SECRET=your_secret \
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8443"
```

### Make targets
- `make run` – start the dev server (exports `TWITCH_REDIRECT_URI` to `http://localhost:8080/login/oauth2/code/twitch` if unset).
- `make test` – run unit/integration tests.
- `make package` – build the runnable jar.
- `make docker-build` / `make docker-run` – containerize and run the service.
- `make ssl` – create a self-signed PKCS12 keystore in `./local`.

### Docker
```bash
make docker-build
TWITCH_CLIENT_ID=your_id TWITCH_CLIENT_SECRET=your_secret docker run -p 8080:8080 imgfloat:latest
```

### Data storage configuration
- `IMGFLOAT_DB_PATH` – filesystem location for the SQLite database (default: `imgfloat.db`).
- `IMGFLOAT_ASSETS_PATH` – root directory where uploaded assets are persisted (default: `assets`).
- `IMGFLOAT_PREVIEWS_PATH` – root directory for generated preview images (default: `previews`).

### Docker Compose example with persistent storage
```yaml
services:
  imgfloat:
    image: imgfloat:latest
    environment:
      TWITCH_CLIENT_ID: your_id
      TWITCH_CLIENT_SECRET: your_secret
      TWITCH_REDIRECT_URI: https://yourdomain/login/oauth2/code/twitch
      IMGFLOAT_DB_PATH: /var/lib/imgfloat/imgfloat.db
      IMGFLOAT_ASSETS_PATH: /var/lib/imgfloat/assets
      IMGFLOAT_PREVIEWS_PATH: /var/lib/imgfloat/previews
    ports:
      - "8080:8080"
    volumes:
      - ./data/db:/var/lib/imgfloat
      - ./data/assets:/var/lib/imgfloat/assets
      - ./data/previews:/var/lib/imgfloat/previews
```
The `volumes` map local folders into the container so database and uploaded files survive container restarts.

### OAuth configuration
Spring Boot reads Twitch credentials from `TWITCH_CLIENT_ID` and `TWITCH_CLIENT_SECRET`. The redirect URI comes from `TWITCH_REDIRECT_URI` (defaulting to `{baseUrl}/login/oauth2/code/twitch`).

### CI
GitHub Actions runs `mvn verify` on pushes and pull requests via `.github/workflows/ci.yml`.

### License
MIT
