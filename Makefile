.ONESHELL:
.POSIX:

.DEFAULT_GOAL := build

IMGFLOAT_DB_PATH ?= ./imgfloat.db
IMGFLOAT_GITHUB_OWNER ?= Kruhlmann
IMGFLOAT_GITHUB_REPO ?= imgfloat-j
IMGFLOAT_ASSETS_PATH ?= ./assets
IMGFLOAT_PREVIEWS_PATH ?= ./previews
SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE ?= 10MB
SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE ?= 10MB
RUNTIME_ENV = IMGFLOAT_ASSETS_PATH=$(IMGFLOAT_ASSETS_PATH) \
			  IMGFLOAT_PREVIEWS_PATH=$(IMGFLOAT_PREVIEWS_PATH) \
			  IMGFLOAT_GITHUB_OWNER=$(IMGFLOAT_GITHUB_OWNER) \
			  IMGFLOAT_GITHUB_REPO=$(IMGFLOAT_GITHUB_REPO) \
			  IMGFLOAT_DB_PATH=$(IMGFLOAT_DB_PATH) \
			  SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE=$(SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE) \
			  SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE=$(SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE)
WATCHDIR = ./src/main

node_modules: package-lock.json
	npm install

.PHONY: build
build:
	mvn compile

.PHONY: run
run:
	test -f .env && . ./.env; $(RUNTIME_ENV) mvn spring-boot:run

.PHONY: watch
watch:
	-mvn compile
	while sleep 0.1; do find $(WATCHDIR) -type f | entr -d mvn compile; done

.PHONY: test
test:
	mvn test

.PHONY: package
package:
	mvn clean package

.PHONY: runx
runx:
	IMGFLOAT_CHANNELS_URL=http://localhost:8080/channels ./src/main/shell/run-electron-app-in-xorg

.PHONY: fix
fix: node_modules
	./node_modules/.bin/prettier --write src

