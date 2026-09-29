---
name: add-feature
description: Use when adding a new feature (CRUD screen, external API integration, etc.) to the FROM project. Analyzes existing conventions first (User entity, session auth, Controller/Service/Repository patterns, Thymeleaf layout), then implements the feature to match them, and reports the file list at the end. Trigger phrases: "기능 추가해줘", "~기능 만들어줘", "게시판/댓글/좋아요 같은 기능 붙여줘", "외부 API 연동해줘".
---

# FROM 프로젝트 기능 추가 워크플로우

이 스킬은 `게시판 CRUD`(2026-08 작업)와 `도서관 정보나루 API 연동`(2026-08 작업)을 만들며 정리된, 이 저장소에 새 기능을 붙일 때 따라야 할 절차다. 여기에 K-PaaS 강의자료(바탕화면 `Kpaas` 폴더 — `3.3/4.6.JPA 기반 공지사항 만들기`, `5.1.JPA 조인(Join) 기초`, `5.2.Spring Session 활용`, `6.1.JPA 기반 회원가입, 로그인하기`)에서 이 프로젝트 스택(JPA + 세션 기반 인증, Spring Security/JWT 미사용)과 맞는 부분만 뽑아 **부록**에 정리해뒀다. `args`로 넘어온 한 줄 요청("댓글 기능", "좋아요 기능", "OO API 연동" 등)을 이 절차에 따라 구현한다.

핵심 원칙: **새 패턴을 만들지 말고, 있는 패턴을 찾아서 그대로 따라간다.** 이 프로젝트는 기능마다 스타일이 흔들리지 않는 것을 중요하게 여긴다.

## 0단계 — 착수 전 확인 (반드시 코드 작성 전에 끝낼 것)

아래를 먼저 Read/Grep으로 읽고, 결과를 1~2문장으로 요약해서 사용자에게 보여준 뒤 구현을 시작한다.

1. **CLAUDE.md** — 이미 로드되어 있음. 빌드 명령, 인증/세션 규칙, 활성/비활성 기능, 배포 방식을 재확인.
2. **User 구조**: `src/main/java/com/from/repository/entity/UserInfoEntity.java` (PK는 `userId` String(20), JPA `@ManyToOne` 관계 없이 순수 컬럼으로만 존재).
3. **로그인/세션**: `interceptor/LoginInterceptor.java`, `config/WebConfig.java`. 세션 키는 `SS_USER_ID`, `SS_USER_NAME`. 새 URL prefix가 로그인이 필요하면 `WebConfig`의 `addPathPatterns`에 추가한다 — 새 인터셉터를 만들지 않는다.
4. **가장 비슷한 기존 기능을 하나 골라서 그대로 베낀다**:
   - DB에 쓰는 단순 CRUD (좋아요/팔로우/댓글류) → `FollowEntity`/`FollowRepository`/`IFollowService`/`FollowService`/`FollowController` 를 템플릿으로 삼는다.
   - 외부 REST API 연동 → `IWeatherService`/`WeatherService` (WebClient + Jackson `JsonNode` 파싱) 를 템플릿으로 삼는다.
   - 게시판형 CRUD(목록/상세/작성/수정/삭제 + 본인 확인) → `com.from.controller.BoardController` / `com.from.service.impl.BoardService` (이번 세션에서 추가됨) 를 템플릿으로 삼는다.
5. **DTO 스타일**: `dto/*.java` — 전부 `record` + `@Builder`(record에 `@Builder` 붙이는 lombok 패턴). class 아님.
6. **Repository/Mapper**: JPA가 기본. `repository/*Repository.java`는 메서드 이름 기반 쿼리 우선, 꼭 필요할 때만 `@Query`. MyBatis(`mapper/*.xml`)는 레거시 조회 전용 용도이므로 새 기능에 굳이 끌어오지 않는다.
7. **Thymeleaf 구조**: `templates/<feature>/*.html`, 최상단에 `<div th:replace="~{layout/navbar :: navbar}"></div>`, `<link rel="stylesheet" th:href="@{/css/common.css}">`, 폰트는 `'Jua', sans-serif`(본문) / `'Hi Melody', cursive`(제목류), 주 색상 `#6071E3`. 새 CSS 프레임워크나 디자인 시스템을 끌어오지 않는다.

## 1단계 — 설계 (DB 있는 CRUD 기능인 경우)

- **Entity**: `@Entity @Table(name="...")`, `@Getter @NoArgsConstructor @AllArgsConstructor @Builder @DynamicInsert @DynamicUpdate`. **`@Setter`는 원칙적으로 달지 않는다**(더티체킹 추적을 흐리고, 의도치 않은 UPDATE를 유발함 — 부록 "Entity 규칙" 참고). 수정이 필요하면 Service 안에서 `@Transactional` 메서드가 필드를 바꾸고 트랜잭션이 끝날 때 더티체킹으로 UPDATE되게 하거나, 새 `Builder` 인스턴스로 만들어 `save()`한다(어쩔 수 없이 `@Setter`를 쓰는 예외가 이미 있다면 — 예: `BoardEntity` — 그건 과거 결정이니 그대로 두고, **새 Entity에서는 지양**한다). PK가 `IDENTITY`면 `@GeneratedValue(strategy = GenerationType.IDENTITY)`. 작성자 컬럼은 `String userId` (FK를 JPA 연관관계로 걸지 않고 문자열 컬럼으로만 — `BoardEntity`/`FollowEntity`/`BookRatingEntity` 참고). 타임스탬프는 `@PrePersist`/`@PreUpdate`로 채운다.
- **Repository**: `extends JpaRepository<XxxEntity, Long>` + 필요한 메서드 이름 기반 쿼리(페이징/정렬이 공짜로 딸려오는 게 `JpaRepository`를 쓰는 이유). insert/update 둘 다 `save()` 하나로 처리(JPA가 신규면 `persist`, 기존이면 `merge`를 알아서 선택). 데이터를 바꾸는 `@Query`에는 반드시 `@Modifying(clearAutomatically = true)`를 붙인다(안 붙이면 영속성 컨텍스트가 변경을 못 따라감). 두 테이블 이상 join이 필요하면 부록 "JOIN/N+1" 절을 먼저 읽는다.
- **DTO**: `record XxxDto(...)` + `@Builder`. null 가능성이 있는 조회는 서비스에서 `Optional<XxxDto>`로 반환한다(널 처리를 Optional로).
- **Service**: 인터페이스 `IXxxService`(`service/`) + 구현체 `XxxService`(`service/impl/`). `@Slf4j @RequiredArgsConstructor @Service`. 메서드 시작/끝에 `log.info("{}.method Start!", this.getClass().getName())` / `...End!` 로그를 남기는 게 이 프로젝트 관례. 본인 확인(수정/삭제 권한)은 반드시 Service 계층에서 `userId` 일치 여부로 검사한다 — Controller에서 하지 않는다.
- **Controller**: `@Slf4j @Controller @RequestMapping("/xxx") @RequiredArgsConstructor`. 화면 이동은 view 이름을 반환(`@Controller`), JSON 응답이 필요한 엔드포인트만 `@ResponseBody`/`@RestController`를 개별적으로 붙인다(예: `FollowController`, `BookController` 참고 — 한 컨트롤러 안에 두 방식이 섞여도 됨). 로그인 필요 여부는 `HttpSession session`에서 `SS_USER_ID`를 꺼내 null 체크.

## 2단계 — 설계 (외부 API 연동 기능인 경우)

- `WeatherService`/`LibraryService`(`service/impl/`) 패턴을 그대로 따른다:
  - `@Value("${xxx.api.key:}")`, `@Value("${xxx.api.base-url:https://...}")` — **API 키는 반드시 기본값을 빈 문자열로 둬서, 키가 아직 발급/설정되지 않아도 앱이 정상 기동되게 한다.** (`application.properties`에 `xxx.api.key=${XXX_API_KEY:}` 형태로 추가)
  - `WebClient.create(baseUrl).get().uri(b -> b.path("/...").queryParam(...).build()).retrieve().bodyToMono(String.class).block()` 로 호출.
  - `ObjectMapper`로 `JsonNode` 트리 파싱. 응답 구조가 배열/단일객체로 오락가락하거나 한 번 더 감싸져 오는 경우를 방어적으로 처리(`LibraryService`의 `unwrapArray`/`unwrapSingle` 참고).
  - **API 키 없음 / 호출 실패 / 응답 비어있음 / JSON 구조가 다름 — 전부 `try/catch`로 잡아서 빈 리스트(or 안전한 기본값) 반환.** 예외를 던져서 페이지 전체가 500으로 죽게 하지 않는다.
  - Controller는 Service를 호출해 `Model`에 담기만 한다. 날짜 계산, 재시도 로직 등은 Service에 둔다.
- `application.properties`에 새 키를 추가할 때 실제 값은 절대 하드코딩하지 않는다. `${VAR_NAME}` 또는 `${VAR_NAME:기본값}` 플레이스홀더만 커밋한다. 실값은 로컬 `.env`(gitignore 대상 여부 확인) 또는 EC2 `/etc/from/from.env`.

## 3단계 — 화면 (Thymeleaf)

- `templates/<feature>/list.html`, `detail.html`, `write.html`, `edit.html` 등 기존 네이밍 관례를 따른다(`board/`, `book/`, `ranking/` 참고).
- 새 페이지는 `templates/layout/navbar.html`에 메뉴 링크를 하나 추가해 사용자가 찾아갈 수 있게 한다(로그인 필요 링크는 `th:if="${session.SS_USER_ID != null}"` 블록 안에).
- 표지 이미지 등 외부 이미지 URL은 `onerror`로 깨진 이미지 대신 placeholder를 넣는다(`book/register.html`, `library/ranking.html` 참고).

## 4단계 — 검증

1. `./gradlew compileJava compileTestJava -q` 로 컴파일 확인.
2. 화면까지 확인하고 싶다면 `run` 스킬을 사용하거나, 직접 `bootRun`을 띄운다. 이때 주의할 점 두 가지(이번 세션에서 실제로 걸렸던 함정):
   - `.env`를 `source .env`로 로드하면 값에 `<...>` 같은 문자가 있을 때 bash가 리다이렉션으로 오해해 깨진다. 대신:
     ```bash
     while IFS='=' read -r key value; do
       [ -z "$key" ] && continue
       export "$key=$value"
     done < .env
     ./gradlew bootRun --console=plain
     ```
   - `bootRun`이 이미 떠 있는 상태에서 **템플릿 파일만** 새로 추가/수정한 경우, devtools 자동 재시작이 `.java` 컴파일에만 반응할 수 있다. 화면이 "template might not exist" 500을 내면 `./gradlew processResources`를 한 번 실행해 `build/resources/main`에 반영한 뒤 새로고침한다.
3. 인증정보/외부 인프라(RDS, Redis, Mongo, 메일)가 필요한 흐름은 로컬 `.env`에 실값이 있는지 먼저 확인하고, 없으면 사용자에게 어떻게 검증할지 물어본다(직접 로그인 vs 테스트 계정 제공 등) — 없는 채로 추측해서 진행하지 않는다.

## 5단계 — 마무리 보고

작업이 끝나면 항상 다음을 정리해서 보고한다:
- 새로 생성한 파일 / 수정한 파일 목록과 각각의 역할
- 필요한 신규 환경변수(있다면) 및 로컬/운영 설정 방법
- 실행/테스트 URL
- 사용자가 명시적으로 "하지 말라"고 한 것(댓글/좋아요/페이징/캐싱 등 범위 밖 기능)을 만들지 않았는지 스스로 체크

## 부록 — K-PaaS 강의자료 기반 세부 규칙

출처: 바탕화면 `Kpaas` 폴더의 `3.3/4.6.JPA 기반 공지사항 만들기(기초/응용)`, `5.1.JPA 조인(Join) 기초`, `5.2.Spring Session 활용`, `6.1.JPA 기반 회원가입, 로그인하기`. 이 슬라이드들은 Spring Security/JWT를 쓰는 강의도 섞여 있지만, 이 저장소는 그런 거 없이 순수 `HttpSession` + `LoginInterceptor`만 쓰므로 **그 부분은 무시**하고, JPA/세션/암호화처럼 스택이 겹치는 내용만 아래에 반영했다.

### Entity 규칙 (기초편)

- Entity ≠ DTO. Controller/Service 바깥으로 Entity를 절대 그대로 내보내지 않는다 — 항상 DTO로 변환해서 반환한다(이미 이 프로젝트 관례와 동일).
- DTO는 `record` + `@Builder`만 쓴다. Entity는 `record`로 만들지 않는다(불변이라 JPA의 변경 감지·더티체킹과 안 맞음).
- DTO 필드명은 camelCase만 쓰고 언더스코어를 넣지 않는다. Jackson으로 Entity→DTO 매핑할 때 필드명이 정확히 같아야 매핑된다.
- `@DynamicInsert`/`@DynamicUpdate`를 Entity에 기본으로 붙인다 — null인 컬럼은 INSERT/UPDATE SQL에서 빼줘서, 예를 들어 제목만 수정해도 `UPDATE ... SET TITLE=... WHERE ...`처럼 필요한 컬럼만 갱신된다.
- 조회수 증가처럼 "읽으면서 동시에 쓰는" 로직은 상세보기 경로에만 `@Transactional`로 넣고, 수정 화면 진입 경로에는 넣지 않는다(수정하러 들어갔다고 조회수가 올라가면 안 됨).
- 수정/삭제 버튼을 보여줄지 말지는 화면(Thymeleaf)에서 세션 사용자 id와 작성자 id를 비교해 결정해도 되지만, 그건 UI 노출 여부일 뿐이고 **실제 수정/삭제 권한 검사는 반드시 Service에서 서버사이드로 다시** 한다(이 프로젝트 기존 관례와 동일 — `BoardService.update/delete` 참고).

### JOIN / N+1 대응 (조인 기초편)

새 Entity에 연관 데이터(예: 게시글 목록에서 작성자 닉네임도 같이 보여주기)가 필요할 때 순서대로 검토:

1. **`@JoinColumn`/`@ManyToOne` 지연로딩만 쓰는 경우 실제 SQL JOIN이 안 나간다** — 목록 조회 1번 + 연관 엔티티 조회 N번(N+1 문제)이 된다. 이 프로젝트는 지금까지 이 문제를 아예 피하려고 FK를 JPA 연관관계로 안 걸고 문자열 컬럼(`userId`)으로만 두고, 필요하면 Service에서 `UserInfoRepository.findByUserId(...)`를 개별 호출하는 방식을 써왔다(`BoardService.toDto`, `FollowService` 참고) — 이 프로젝트 규모에선 이 방식이면 충분하다.
2. 목록 화면에서 N+1이 실제로 체감될 정도로 항목이 많아지면, 매번 개별 조회하는 대신 **JPQL Fetch Join**(`@Query("select b from XxxEntity b join fetch b.author where ...")`)으로 한 번의 SQL로 가져오는 걸 우선 고려한다. `FetchType.EAGER`로 바꾸는 건 지양한다(항상 로딩되어 성능에 안 좋음, `LAZY`가 기본값으로 권장됨).
3. 3개 이상 테이블을 엮어야 하거나 SQL이 복잡해지면 `@Query(nativeQuery = true)`(단, 반환된 엔티티는 영속 상태가 아니라 더티체킹/캐시 대상이 아님)나 기존 MyBatis 매퍼(`mapper/*.xml`)를 재사용하는 걸 고려한다.
4. `@JoinTable`(다대다 브릿지 테이블)은 지양한다 — 이 프로젝트도 지금까지 다대다 관계를 걸 때 중간 엔티티(`FollowEntity`, `BookReviewLikeEntity`처럼)를 직접 만들지, `@JoinTable`을 쓰지 않았다.
5. Lazy 연관관계를 트랜잭션 밖에서 접근하면 에러/빈 값이 나므로, 연관 데이터를 읽는 Service 메서드에는 `@Transactional`을 붙인다.

### 동시성이 걱정되는 기능이라면 (응용편)

- 여러 사용자가 같은 행을 동시에 수정할 수 있는 기능(예: 좋아요 카운트, 재고, 공동 편집)을 만들 때만 아래를 고려한다 — 평범한 CRUD엔 과한 설계이므로 기본으로 넣지 않는다.
- 필요하다면 **Optimistic Lock**을 기본으로 선택한다(Pessimistic Lock보다 처리량에 유리, 이 슬라이드에서도 "가장 흔히 쓰는 방식"으로 소개): Entity에 `@Version private Integer version;` 필드 추가 + DB에 `version INT DEFAULT 0` 컬럼 추가(`ddl-auto=update`가 자동 반영) + 갱신 메서드에 `@Transactional` + 호출부에서 `OptimisticLockException`을 잡아 재시도하거나 사용자에게 "다시 시도해주세요" 안내.
- 여러 엔드포인트에 걸쳐 에러 응답 형식을 통일하고 싶을 때만 `@ControllerAdvice` + `@ExceptionHandler`(`GlobalExceptionHandler`류)를 새로 만든다. 다만 catch-all `Exception` 핸들러는 개발 중엔 스택트레이스를 삼켜버려서 디버깅을 방해하니, 넣더라도 로그는 반드시 남기고 운영 배포 직전에만 최종 확인한다.

### Session (Redis) 참고 — 새로 만들 것 없음

- 이 프로젝트는 이미 `spring.session.store-type=redis`로 Redis 기반 Spring Session을 쓰고 있다(단일 서버 배포지만, 세션이 JVM 힙이 아니라 Redis에 있어서 앱 재시작에도 로그인이 안 끊긴다는 이점은 이미 누리고 있음). 새 기능에서 세션을 쓸 때 추가로 설정할 건 없고, 그냥 기존처럼 `HttpSession session`에서 `session.getAttribute("SS_USER_ID")`를 꺼내 쓰면 된다.
- 디버깅 시 참고: Redis 키 prefix는 `spring.session.redis.namespace`(현재 `spring:session`), 세션 만료시간은 `server.servlet.session.timeout`(현재 `30m`)이 이미 설정돼 있다.

### 회원가입/로그인/암호화 참고

- 이 저장소의 `EncryptUtil`(SHA-256+salt 비밀번호 해시, AES-256-CBC 이메일 암호화)은 슬라이드가 권장하는 정책과 이미 일치한다: **복호화가 필요한 개인정보(이메일 등)는 대칭키 암호화, 비밀번호처럼 절대 복호화하면 안 되는 값은 단방향 해시만.** 새 필드를 암호화해서 저장해야 할 일이 생기면 이 두 원칙 중 어느 쪽인지 먼저 판단한다.
- 암호화된 컬럼은 평문보다 길어진다 — 새로 암호화 컬럼을 추가할 때 `@Column(length=...)`을 평문 길이가 아니라 여유 있게(슬라이드 예시는 64자 기준) 잡는다.
- 아이디 중복확인처럼 "저장했다고 주장하기 전에 정말 저장됐는지" 확인이 필요한 흐름은, 저장 직후 한 번 더 조회해서 실제로 반영됐는지 확인하는 방어적 패턴을 참고할 만하다(이 저장소의 `UserInfoRepository.findByUserId` 스타일과 동일한 `Optional` 기반 존재확인 패턴).

## 하지 말아야 할 것

- 이 프로젝트에 없는 새 인증 방식, 새 세션 키, 새 CSS 프레임워크, 새 ORM 패턴을 도입하지 않는다.
- 요청받지 않은 리팩터링(기존 컨트롤러/서비스 구조 변경 등)을 하지 않는다.
- 실제 비밀 값을 `application.properties`나 코드에 하드코딩하지 않는다.
- DB 스키마가 필요한 기능은 `ddl-auto=update`가 자동 생성하므로, 별도 SQL 마이그레이션 파일을 만들지 않는다(사용자가 명시적으로 요청한 경우 제외).
