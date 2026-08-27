# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
./gradlew build       # Compile and package
./gradlew bootRun     # Start the application (port 11000)
./gradlew test        # Run all tests
./gradlew clean       # Clean build artifacts
```

To run a single test class:
```bash
./gradlew test --tests "com.from.SomeTest"
```

## Tech Stack

- **Java 17**, Spring Boot 3.2.5
- **Persistence:** MySQL (JPA + MyBatis) and MongoDB (dual setup)
- **Templates:** Thymeleaf
- **Build:** Gradle with Lombok annotation processing

## Architecture

Standard Spring layering: `controller → service → repository/mapper`

```
com.from/
├── config/         # AppConfig, WebConfig (interceptors), DatabaseConfig, MongoConfig, EncryptUtil
├── controller/     # HTTP handlers
├── service/        # Business logic (interfaces in service/, impls in service/impl/)
├── repository/     # Spring Data JPA repositories + entity/
├── mapper/         # MyBatis XML mappers (XML files in src/main/resources/mapper/)
├── domain/         # MyBatis domain objects
├── dto/            # Record-based DTOs (UserInfoDTO, MsgDTO)
├── interceptor/    # LoginInterceptor — session-based auth guard
├── scheduler/      # @Scheduled tasks
└── util/           # CmmUtil (null/whitespace helpers)
```

Templates are under `src/main/resources/templates/` organized by feature (user/, book/, review/, ranking/, image/, layout/).

## Authentication & Session

- Session attributes: `SS_USER_ID`, `SS_USER_NAME`
- `LoginInterceptor` blocks unauthenticated access to `/book/**`, `/ranking/**`, `/review/**`, `/user/mypage`, `/service`
- Public routes are explicitly excluded in `WebConfig.java`

## Encryption

`EncryptUtil.java` provides:
- **SHA-256** with salt `"FROM_SALT_2025"` — used for password hashing
- **AES-256-CBC** with hardcoded key/IV — used for email encryption

## Email Verification Flow

1. Controller calls `EmailService` to send a 6-digit code stored in HTTP session
2. Code expires after 5 minutes (checked in service layer)
3. Three verification types: `SIGNUP`, `FIND_ID`, `FIND_PASSWORD`

## Infrastructure Dependencies

The app expects external services at startup:
- MariaDB (AWS RDS) at `mariadb-recovery.crw06g6ga7s0.ap-northeast-2.rds.amazonaws.com:3306/myDB`
- MongoDB at `3.36.242.179:27017/MyDB`
- Redis at `localhost:6379` (co-located with the app)
- Naver SMTP at `smtp.naver.com:587`

These are configured in `src/main/resources/application.properties`, which **is tracked in Git** — it only ever contains `${VAR}` placeholders (e.g. `spring.datasource.password=${DB_PASSWORD}`), never actual secret values. No Spring profiles are used.

The actual secret values (DB password, Redis password, Mongo credentials, mail credentials, AWS keys, third-party API keys — `DB_USERNAME`, `DB_PASSWORD`, `REDIS_PASSWORD`, `MONGO_USERNAME`, `MONGO_PASSWORD`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `WEATHER_API_KEY`, `ALADIN_API_KEY`, `ANTHROPIC_API_KEY`, `GEMINI_API_KEY`, `OPENAI_API_KEY`, `AWS_ACCESS_KEY`, `AWS_SECRET_KEY`, `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET`) live only on the deployment server (EC2) in `/etc/from/from.env`, which is **not committed to Git** (`chmod 600`, owned by `root`).

The app runs as a systemd service (`from.service`, unit at `/etc/systemd/system/from.service`) which loads `/etc/from/from.env` via `EnvironmentFile=` before starting `java -jar /home/ec2-user/data/From-0.0.1-SNAPSHOT.jar`. `application.properties`'s `${VAR}` placeholders are resolved from those environment variables at startup.

**Deploy jar must never have secrets baked in.** Build with `./gradlew clean bootJar` (skips the test task, which fails locally without the env vars set) and ship the resulting `build/libs/From-0.0.1-SNAPSHOT.jar` as-is — it should still contain unresolved `${...}` placeholders. Verify before deploying:
```bash
unzip -p build/libs/From-0.0.1-SNAPSHOT.jar BOOT-INF/classes/application.properties | grep -c '\${'
```
A count of 0 means secrets were resolved into the jar at build time — do not deploy that jar.

To restart the app after updating secrets or deploying a new jar:
```bash
sudo systemctl restart from
sudo systemctl status from
sudo journalctl -u from -n 50 --no-pager
```

## Active vs. Commented-Out Features

Only user authentication is currently active. Book search (Aladin API), AI review generation (OpenAI), image generation (Gemini/Nanobana), Kakao OAuth, and weekly rankings are all implemented but commented out across controllers, services, repositories, mappers, and schedulers.
