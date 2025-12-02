APP_NAME=imgfloat

.PHONY: run test package docker-build docker-run ssl

run:
mvn spring-boot:run

test:
mvn test

package:
mvn clean package

docker-build:
docker build -t $(APP_NAME):latest .

docker-run:
docker run --rm -p 8080:8080 -e TWITCH_CLIENT_ID=$${TWITCH_CLIENT_ID} -e TWITCH_CLIENT_SECRET=$${TWITCH_CLIENT_SECRET} $(APP_NAME):latest

ssl:
mkdir -p local
keytool -genkeypair -alias $(APP_NAME) -keyalg RSA -keystore local/keystore.p12 -storetype PKCS12 -storepass changeit -keypass changeit -dname "CN=localhost" -validity 365
echo "Use SSL_ENABLED=true SSL_KEYSTORE_PATH=file:$$PWD/local/keystore.p12"
