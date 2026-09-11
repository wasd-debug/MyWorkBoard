# Repository Guidelines

## Project Structure & Module Organization

- `backend/` contains the Java 17 Spring Boot service. Application code is under `backend/src/main/java/com/salarytracker`, tests under `backend/src/test/java`, and Flyway migrations under `backend/src/main/resources/db/migration`.
- `backend/modules/` defines module boundaries; `backend/app/` assembles the application.
- `frontend/` contains the Vue 3/Vite client. Put views in `src/views`, reusable components in `src/components`, API wrappers in `src/api`, Pinia stores in `src/stores`, and theme styles in `src/styles`.
- `frontend/packages/` holds shared packages such as `sync-engine`, `api-client`, and UI primitives.
- `deploy/` contains Dockerfiles, Compose definitions, Nginx configuration, database initialization, and local/deployment helpers.

## Build, Test, and Development Commands

```bash
cd backend && mvn spring-boot:run   # start API on :8080
cd backend && mvn test              # run JUnit/architecture tests
cd backend && mvn package           # build the backend JAR
cd frontend && npm install          # install frontend dependencies
cd frontend && npm run dev          # start Vite on :5173
cd frontend && npm run build        # create the production bundle
cd frontend && node --test packages/sync-engine/src/index.test.js
```

For a local MySQL-backed API, use `bash deploy/dev-backend.sh`. Run the full stack with `docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d --build`.

## Coding Style & Naming Conventions

Use four-space indentation in Java and two spaces in Vue/JavaScript. Java types use `PascalCase`; methods and variables use `camelCase`; constants use `UPPER_SNAKE_CASE`. Name Vue components in `PascalCase.vue`. Keep controllers thin, place business rules in services, and preserve module boundaries. No repository-wide formatter is configured; match nearby code and run `git diff --check` before committing.

## Testing Guidelines

Use JUnit 5 names ending in `Test.java`; write behavior-focused method names such as `recognizesAllSupportedTransactionKinds`. Architecture changes must keep `ArchitectureBoundaryTest` passing. Add Node tests beside shared package code as `*.test.js`. There is no fixed coverage threshold; cover new rules, migrations, and regressions.

## Commit & Pull Request Guidelines

Recent history uses concise conventional prefixes, for example `feat：完善账本流水管理` and `fix：账本`. Prefer `feat:`, `fix:`, `chore:`, or `test:` followed by a specific summary. PRs should describe scope, data/schema impact, test commands and results, linked issues, and screenshots for UI changes. Keep unrelated changes in separate commits.

## Security & Configuration Tips

Never commit `deploy/.env`, credentials, tokens, database snapshots, or generated build output. Add schema changes as a new versioned Flyway migration; do not rewrite an applied migration. Avoid destructive Compose commands such as `down -v` unless data removal is explicitly intended.

## Deployment & SSH

For this project, all future SSH connections must use the repository-local `workboard.pem` key; do not use password authentication. Connect with `ssh -i ./workboard.pem ubuntu@212.64.29.21`. Keep the key permission at `600`, never display or copy its contents, and never add it to Git. The root `.gitignore` must continue to exclude `/workboard.pem`.
