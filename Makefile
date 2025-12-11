.ONESHELL:
.POSIX:

.DEFAULT_GOAL := build

WATCHDIR = ./src/main

.PHONY: build
build:
	mvn compile

.PHONY: run
run:
	test -f .env && . ./.env; mvn spring-boot:run

.PHONY: watch
watch:
	while sleep 0.1; do find $(WATCHDIR) -type f | entr -d mvn -q compile; done

.PHONY: test
test:
	mvn test

.PHONY: package
package:
	mvn clean package

.PHONY: ssl
ssl:
	mkdir -p local
	keytool -genkeypair -alias imgfloat -keyalg RSA -keystore local/keystore.p12 -storetype PKCS12 -storepass changeit -keypass changeit -dname "CN=localhost" -validity 365
	echo "Use SSL_ENABLED=true SSL_KEYSTORE_PATH=file:$$PWD/local/keystore.p12"
