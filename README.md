
![FROM 시스템 아키텍처](docs/FROM_시스템아키텍처.png)

### ERD

![FROM ERD](docs/FROM_ERD.png)

### 디렉터리 구조

```text
src/main/
├── java/com/from/
│   ├── client/                 # Claude·Gemini API 클라이언트
│   ├── config/                 # DB, Redis 관련 웹 세션 설정, S3, 암호화 등
│   ├── constant/               # 지역 코드 등 상수
│   ├── controller/             # HTTP 요청 및 화면 처리
│   ├── dto/                    # 요청·응답 데이터
│   ├── interceptor/            # 로그인 접근 제어
│   ├── repository/
│   │   ├── entity/             # JPA 엔티티
│   │   └── document/           # MongoDB 문서
│   ├── scheduler/              # 편지 예약 발송·랭킹 동기화
│   ├── service/                # 서비스 인터페이스
│   │   └── impl/               # 비즈니스 로직
│   └── util/                   # 공통 유틸리티
└── resources/
    ├── static/                 # CSS·이미지
    ├── templates/              # 기능별 Thymeleaf 화면
    └── application.properties  # 애플리케이션 설정
```

## 실행 방법

### 1. 사전 준비

- JDK 17
- MariaDB, MongoDB, Redis
- Naver SMTP 계정 및 사용할 외부 API의 인증 정보
- 이미지 업로드에 사용할 S3 버킷과 접근 권한

Gradle Wrapper가 포함되어 있어 Gradle을 별도로 설치할 필요는 없습니다.

### 2. 환경 설정

프로젝트 루트의 `.env` 파일 또는 실행 환경의 환경 변수에 아래 값을 설정합니다. 로컬에서는 `spring-dotenv`가 `.env`를 읽으며, `.env`는 Git에서 제외됩니다.

| 용도 | 환경 변수 |
| --- | --- |
| MariaDB | `DB_USERNAME`, `DB_PASSWORD` |
| MongoDB | `MONGO_USERNAME`, `MONGO_PASSWORD` |
| Redis | `REDIS_PASSWORD` |
| 이메일 | `MAIL_USERNAME`, `MAIL_PASSWORD` |
| 도서·도서관·날씨 API | `ALADIN_API_KEY`, `LIBRARY_API_KEY`, `WEATHER_API_KEY` |
| AI API | `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, `GEMINI_API_KEY` |
| AWS | `AWS_ACCESS_KEY`, `AWS_SECRET_KEY` |
| 이메일 암호화 | `ENCRYPT_AES_KEY`, `ENCRYPT_AES_IV` |
| Kakao 설정 | `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET` |

Kakao 항목은 설정 파일에 남아 있는 값이며, 이 README에서는 소셜 로그인을 제공 기능으로 안내하지 않습니다. AES 키와 IV는 현재 구현에서 요구하는 각각 16바이트 문자열로 설정합니다.

실제 비밀번호와 API 키는 저장소에 커밋하지 않습니다. `application.properties`의 인증 정보는 `${환경변수명}` 형식을 유지합니다.

실행 전에 다음 항목도 자신의 환경에 맞춰 확인합니다.

- DB 접속 주소, Redis 주소, MongoDB URI와 인증 DB
- S3 버킷 이름과 리전, 메일 서버 설정
- **MariaDB 스키마**: `spring.jpa.hibernate.ddl-auto=validate`이므로 테이블이 자동 생성되지 않습니다. 엔티티에 맞는 스키마를 미리 준비해야 합니다.
- **MongoDB 데이터베이스**: 현재 `MongoConfig`의 `MongoTemplate`은 `fromdb`를 사용합니다. URI의 DB명과 별도로 이 설정과 계정 권한을 확인해야 합니다.

### 3. 애플리케이션 실행

macOS / Linux:

```bash
./gradlew bootRun
```

Windows PowerShell:

```powershell
.\gradlew.bat bootRun
```

실행 후 [http://localhost:11000](http://localhost:11000)에 접속합니다.

### 4. 테스트 및 빌드

```bash
# 전체 테스트
./gradlew test

# 테스트를 포함한 빌드
./gradlew build

# 배포용 실행 JAR 생성 (테스트 태스크 제외)
./gradlew clean bootJar
```

Windows에서는 `./gradlew` 대신 `.\gradlew.bat`를 사용합니다. 애플리케이션 컨텍스트를 로드하는 테스트에는 환경 변수와 외부 서비스 연결이 필요합니다.

생성 파일: `build/libs/From-0.0.1-SNAPSHOT.jar`

## 배포 시 설정 관리

운영 환경에서는 systemd 서비스가 `/etc/from/from.env`를 `EnvironmentFile`로 읽어 인증 정보를 주입합니다. 환경 파일은 저장소에 포함하지 않고 접근 권한을 제한합니다.

배포 JAR에는 실제 인증 정보를 넣지 않으며, 배포 전에 다음 명령으로 설정 파일의 환경 변수 플레이스홀더가 남아 있는지 확인합니다.

```bash
unzip -p build/libs/From-0.0.1-SNAPSHOT.jar BOOT-INF/classes/application.properties | grep -c '\${'
```

결과가 `0`이면 배포를 중단하고 빌드 설정을 확인합니다. 이 검사는 플레이스홀더 보존 여부를 확인하기 위한 검사입니다.

## 관련 자료

- [서비스 이용 흐름](docs/FROM_서비스이용흐름.png)
- [시스템 아키텍처](docs/FROM_시스템아키텍처.png)
- [ERD](docs/FROM_ERD.png)
