.DEFAULT_GOAL := help

ROOT_DIR := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))
DOCKER_COMPOSE ?= docker compose
MAVEN ?= ./mvnw
HEALTH_URL ?= http://localhost:8080/actuator/health

.PHONY: help setup up start stop down restart logs ps test build health docs-up docs-down

help: ## Show available commands.
	@printf 'Usage: make <target>\n\n'
	@awk 'BEGIN {FS = ":.*## "} /^[a-z-]+:.*## / {printf "  %-12s %s\n", $$1, $$2}' "$(ROOT_DIR)/Makefile"
	@printf '\nNative test/build require Java 21 and Docker for Testcontainers; Maven is provided by the Wrapper.\n'

setup: ## Create .env from .env.example without overwriting an existing file.
	@cd "$(ROOT_DIR)" && \
	if [ -e .env ] || [ -L .env ]; then \
		printf '%s\n' '.env already exists; left unchanged.'; \
	else \
		cp -n .env.example .env && printf '%s\n' 'Created .env from .env.example.'; \
	fi

up: ## Build and start the backend and PostgreSQL in the background.
	cd "$(ROOT_DIR)" && $(DOCKER_COMPOSE) -f compose.yaml up --build -d

start: ## Start existing stopped containers without rebuilding or recreating them.
	cd "$(ROOT_DIR)" && $(DOCKER_COMPOSE) -f compose.yaml start

stop: ## Stop running containers without removing them.
	cd "$(ROOT_DIR)" && $(DOCKER_COMPOSE) -f compose.yaml stop

down: ## Stop the application stack and preserve database volumes.
	cd "$(ROOT_DIR)" && $(DOCKER_COMPOSE) -f compose.yaml down

restart: ## Restart the backend service.
	cd "$(ROOT_DIR)" && $(DOCKER_COMPOSE) -f compose.yaml restart backend

logs: ## Follow backend logs (Ctrl+C to stop following).
	cd "$(ROOT_DIR)" && $(DOCKER_COMPOSE) -f compose.yaml logs -f backend

ps: ## Show application container status.
	cd "$(ROOT_DIR)" && $(DOCKER_COMPOSE) -f compose.yaml ps

test: ## Run all backend tests, including the PostgreSQL smoke test.
	cd "$(ROOT_DIR)/backend" && $(MAVEN) test

build: ## Package the executable JAR, including tests.
	cd "$(ROOT_DIR)/backend" && $(MAVEN) package

health: ## Check backend health (override with HEALTH_URL=...).
	cd "$(ROOT_DIR)" && curl --fail --silent --show-error "$(HEALTH_URL)"

docs-up: ## Start the separate Swagger UI stack.
	cd "$(ROOT_DIR)" && $(DOCKER_COMPOSE) -f compose.docs.yaml up -d

docs-down: ## Stop the Swagger UI stack.
	cd "$(ROOT_DIR)" && $(DOCKER_COMPOSE) -f compose.docs.yaml down
