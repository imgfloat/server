APP_NAME=imgfloat

.PHONY: run test package docker-build docker-run ssl

run:
	test -f .env && . ./.env; \
	export TWITCH_REDIRECT_URI=$${TWITCH_REDIRECT_URI:-http://localhost:8080/login/oauth2/code/twitch}; \
	mvn spring-boot:run

test:
	mvn test

package:
	mvn clean package

ssl:
	mkdir -p local
	keytool -genkeypair -alias $(APP_NAME) -keyalg RSA -keystore local/keystore.p12 -storetype PKCS12 -storepass changeit -keypass changeit -dname "CN=localhost" -validity 365
	echo "Use SSL_ENABLED=true SSL_KEYSTORE_PATH=file:$$PWD/local/keystore.p12"
