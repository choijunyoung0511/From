# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
./gradlew build       # Compile and package
./gradlew bootRun     # Start the application (port 8080)
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
- MySQL at `192.168.75.128:3306/fromdb` (user: `fromuser`)
- MongoDB at `192.168.75.128:27017/fromdb`
- Naver SMTP at `smtp.naver.com:587`

These are configured in `src/main/resources/application.properties` (currently untracked — add your own).

## Active vs. Commented-Out Features

Only user authentication is currently active. Book search (Aladin API), AI review generation (OpenAI), image generation (Gemini/Nanobana), Kakao OAuth, and weekly rankings are all implemented but commented out across controllers, services, repositories, mappers, and schedulers.
