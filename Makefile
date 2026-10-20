.DEFAULT_GOAL := help

ROOT_DIR := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))
DOCKER_COMPOSE ?= docker compose
MAVEN ?= ./mvnw
HEALTH_URL ?= http://localhost:8080/actuator/health
OBSERVABILITY_ENV ?= .env.observability
OBSERVABILITY_PROJECT ?= finfast-observability
OBSERVABILITY_COMPOSE = $(DOCKER_COMPOSE) --env-file "$(OBSERVABILITY_ENV)" -p "$(OBSERVABILITY_PROJECT)" -f compose.observability.yaml

.PHONY: help setup up start stop down restart logs ps test build health docs-up docs-down observability-setup observability-up observability-down observability-health observed-up

help: ## Show available commands.
	@printf 'Usage: make <target>\n\n'
	@awk 'BEGIN {FS = ":.*## "} /^[a-z-]+:.*## / {printf "  %-22s %s\n", $$1, $$2}' "$(ROOT_DIR)/Makefile"
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

observability-setup: ## Create local Grafana settings; choose credentials before startup.
	@cd "$(ROOT_DIR)" && \
	if [ -e "$(OBSERVABILITY_ENV)" ] || [ -L "$(OBSERVABILITY_ENV)" ]; then \
		printf '%s\n' 'Observability environment already exists; left unchanged.'; \
	else \
		cp -n .env.observability.example "$(OBSERVABILITY_ENV)" && \
		printf '%s\n' 'Created observability settings. Set GRAFANA_ADMIN_USER and GRAFANA_ADMIN_PASSWORD.'; \
	fi

observability-up: ## Start optional telemetry; Grafana defaults to http://localhost:3300.
	cd "$(ROOT_DIR)" && $(OBSERVABILITY_COMPOSE) up -d --wait --wait-timeout 150

observability-down: ## Stop telemetry; preserve containers, shared network and all volumes.
	cd "$(ROOT_DIR)" && $(OBSERVABILITY_COMPOSE) stop

observability-health: ## Check all six telemetry services over private Docker DNS.
	cd "$(ROOT_DIR)" && $(OBSERVABILITY_COMPOSE) exec -T grafana sh -ec \
		'for endpoint in http://grafana:3000/api/health http://loki:3100/ready http://tempo:3200/ready http://alloy:12345/-/ready http://prometheus:9090/-/ready http://alertmanager:9093/-/ready; do curl -fsS --max-time 5 "$$endpoint"; printf "\n"; done'

observed-up: observability-up ## Start the backend with ECS stdout, Prometheus metrics and OTLP traces.
	cd "$(ROOT_DIR)" && $(DOCKER_COMPOSE) --env-file .env --env-file "$(OBSERVABILITY_ENV)" -f compose.yaml -f compose.observed.yaml up --build -d backend
