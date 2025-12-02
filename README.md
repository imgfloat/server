# Imgfloat

A Spring Boot overlay server for Twitch broadcasters and their channel admins. Broadcasters can authorize via Twitch OAuth and invite channel admins to manage images that float over a transparent canvas. Updates are pushed in real time over WebSockets so OBS browser sources stay in sync.

## Features
- Twitch OAuth (OAuth2 login) with broadcaster and channel admin access controls.
- Admin console with Twitch player embed and canvas preview.
- Broadcaster overlay view optimized for OBS browser sources.
- Real-time image creation, movement, resize, rotation, visibility toggles, and deletion via STOMP/WebSockets.
- In-memory channel directory optimized with lock-free collections for fast updates.
- Optional SSL with local self-signed keystore support.
- Dockerfile, Makefile, CI workflow, and Maven build.

## Getting started
### Prerequisites
- Java 17+
- Maven 3.9+
- Twitch Developer credentials (Client ID/Secret)

### Local run
```bash
TWITCH_CLIENT_ID=your_id TWITCH_CLIENT_SECRET=your_secret mvn spring-boot:run
```
The default server port is `8080`. Log in via `/oauth2/authorization/twitch`.

### Enable TLS locally
```bash
make ssl
SSL_ENABLED=true SSL_KEYSTORE_PATH=file:$(pwd)/local/keystore.p12 \
TWITCH_CLIENT_ID=your_id TWITCH_CLIENT_SECRET=your_secret \
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8443"
```

### Make targets
- `make run` – start the dev server.
- `make test` – run unit/integration tests.
- `make package` – build the runnable jar.
- `make docker-build` / `make docker-run` – containerize and run the service.
- `make ssl` – create a self-signed PKCS12 keystore in `./local`.

### Docker
```bash
make docker-build
TWITCH_CLIENT_ID=your_id TWITCH_CLIENT_SECRET=your_secret docker run -p 8080:8080 imgfloat:latest
```

### OAuth configuration
Spring Boot reads Twitch credentials from `TWITCH_CLIENT_ID` and `TWITCH_CLIENT_SECRET`. The redirect URI is `{baseUrl}/login/oauth2/code/twitch`.

### CI
GitHub Actions runs `mvn verify` on pushes and pull requests via `.github/workflows/ci.yml`.

### License
MIT
