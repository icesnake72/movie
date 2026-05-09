# 강의 자료 (학생용)

**과정명**: TMDB 인기영화 백엔드 구축 — Spring Boot 입문 실습
**선수 지식**: Java 기본 문법, Maven/Gradle 개념, SQL 기초

---

## 학습 목표

- Spring Boot 프로젝트의 표준 디렉토리 구조와 의존성 관리를 이해한다
- JPA 단일 엔티티로 DB 테이블을 자동 생성·매핑할 수 있다
- 외부 REST API(TMDB)를 `RestClient` 로 호출하고 응답을 DTO 로 매핑할 수 있다
- `Controller → Service → Repository` 3계층 구조로 REST API 를 구현한다
- 환경변수를 활용해 비밀값을 안전하게 분리하고 GitHub 에 배포한다

## 최종 산출물

- `popular_movie` 단일 테이블에 TMDB 인기영화 20건을 적재
- REST 엔드포인트 3종: `POST /sync`, `GET /`, `DELETE /{id}`
- GitHub public 리포지토리 (토큰은 환경변수로 분리)

---

## Session 1. 프로젝트 셋업과 DB 연결

### 1-1. Spring Initializr 프로젝트 생성

- [https://start.spring.io](https://start.spring.io) 사용
- 의존성: **Spring Web**, **Spring Data JPA**, **MySQL Driver**, **Validation**, **Lombok**
- Java 21, Gradle, Spring Boot 3.5.x

### 1-2. MySQL 데이터베이스 준비

```sql
CREATE DATABASE IF NOT EXISTS movie DEFAULT CHARACTER SET utf8mb4;
```

> **포인트** — DB 는 사람이 만들고, 테이블은 코드(JPA)가 만든다. 책임 분리 개념.

### 1-3. application.yaml 설정

```yaml
server:
  port: 9000

spring:
  application:
    name: movie
  datasource:
    url: jdbc:mysql://localhost:3306/movie?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Seoul
    username: root
    password: "1234"
  jpa:
    hibernate:
      ddl-auto: create
    show-sql: true
```

> **포인트** — `ddl-auto` 옵션 비교
> - `create`: 매 기동마다 DROP + CREATE (학습용)
> - `update`: 차이만 반영 (개발 중)
> - `validate`: 차이 있으면 부팅 실패 (운영)
> - `none`: 자동 작업 안 함 (운영 권장 + Flyway/Liquibase)

### 1-4. 첫 부팅

```bash
./gradlew bootRun
```

- 콘솔 로그에서 `Started MovieApplication in X seconds` 확인
- DB 접속 실패 시 트러블슈팅 (포트, 비번, allowPublicKeyRetrieval)

---

## Session 2. 단일 엔티티 설계

### 2-1. TMDB 응답 JSON 살펴보기

```bash
curl "https://api.themoviedb.org/3/movie/popular?language=ko-KR&page=1" \
  -H "Authorization: Bearer <TOKEN>"
```

- 응답 구조 분석: `page`, `results[]`, `total_pages`, `total_results`
- `results[]` 배열의 각 영화 필드 식별

### 2-2. Movie 엔티티 작성

```java
@Entity
@Table(name = "popular_movie")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Movie {
  @Id
  private Long id;                  // TMDB id 를 그대로 PK 로 사용

  @Column(name = "title", length = 500, nullable = false)
  private String title;

  @JsonProperty("backdrop_path")
  private String backdropPath;

  // ... 이하 필드
}
```

> **포인트**
> - `@NoArgsConstructor(PROTECTED)` — JPA 가 리플렉션으로 인스턴스 생성하기 위해 필요. 외부에서 `new Movie()` 호출 방지.
> - `@Builder` — 필드 많을 때 가독성 있게 객체 생성.
> - 컬럼명 `snake_case`, 자바 필드명 `camelCase` 관례.

### 2-3. genre_ids 처리 — 단일 컬럼 보관 전략

```java
@Column(length = 500)
private String genreIds;            // "[10751, 35, 12]" 형태로 저장
```

> **포인트** — 정규화 vs 단일 컬럼
> - 정상이라면 `genre`, `movie_genre` 두 테이블로 분리(다대다)
> - 지금은 학습 단계라 단일 컬럼으로 단순화 → 다음 단원의 자연스러운 동기 부여

### 2-4. Repository 작성

```java
public interface MovieRepository extends JpaRepository<Movie, Long> {}
```

> **포인트** — 한 줄로 `save / findAll / findById / deleteById / count` 등 자동 제공.

---

## Session 3. 외부 API 호출과 DTO

### 3-1. TMDB 토큰 발급과 환경변수

- TMDB 가입 → Settings → API → API Read Access Token 복사
- 환경변수 등록:
  ```bash
  export TMDB_BEARER_TOKEN="<token>"
  ```
- application.yaml:
  ```yaml
  tmdb:
    bearer-token: ${TMDB_BEARER_TOKEN:CHANGE_ME}
  ```

> **포인트** — 비밀값을 코드/yaml 에 박지 말 것. `${ENV:default}` 패턴은 환경변수 우선, 없으면 기본값.

### 3-2. RestClient 빈 구성

```java
@Configuration
public class RestClientConfig {
  @Bean
  public RestClient tmdbRestClient(
      @Value("${tmdb.base-url}") String baseUrl,
      @Value("${tmdb.bearer-token}") String token) {
    return RestClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build();
  }
}
```

> **포인트** — RestTemplate 은 deprecated, RestClient(Spring 6+) 사용. WebClient 와 차이도 한 줄로 언급.

### 3-3. TmdbMovieDto / TmdbPopularResponse POJO

```java
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TmdbMovieDto {
  private Long id;
  private boolean adult;

  @JsonProperty("backdrop_path")
  private String backdropPath;

  @JsonProperty("genre_ids")
  private List<Integer> genreIds;
  // ...
}
```

> **포인트** — 핵심 어노테이션 3종 비교
> - `@JsonProperty("snake_case")` — JSON 키 ↔ Java 필드 매핑
> - `@JsonIgnoreProperties(ignoreUnknown = true)` — 모르는 필드 무시 (외부 API 안정성)
> - `@JsonNaming` — 클래스 단위 네이밍 전략 (참고만, 직접 사용은 익숙해진 뒤)

### 3-4. record vs class 비교

- record 의 간결함과 한계(불변, 상속 불가, Lombok 와 충돌)
- 학습 초기엔 익숙한 POJO 권장

---

## Session 4. Service / Controller 구성

### 4-1. MovieService — TMDB 호출과 변환

```java
@Transactional
public int syncPopularMovies(int page) {
  TmdbPopularResponse response = tmdbRestClient.get()
      .uri(b -> b.path("/movie/popular")
                 .queryParam("language", defaultLanguage)
                 .queryParam("page", page).build())
      .retrieve()
      .body(TmdbPopularResponse.class);

  List<Movie> movies = response.getResults().stream()
      .map(this::toEntity).toList();

  movieRepository.saveAll(movies);
  return movies.size();
}
```

> **포인트**
> - `@Transactional` 의 의미: 메서드 단위 트랜잭션 경계
> - `saveAll` 의 동작: PK 가 있으면 upsert
> - DTO ↔ Entity 변환은 항상 Service 책임

### 4-2. MovieController — REST 엔드포인트

```java
@RestController
@RequestMapping("/api/movies/popular")
@RequiredArgsConstructor
public class MovieController {

  private final MovieService movieService;

  @PostMapping("/sync")
  public Map<String,Object> sync(@RequestParam(defaultValue="1") int page) {
    return Map.of("page", page, "saved", movieService.syncPopularMovies(page));
  }

  @GetMapping
  public List<TmdbMovieDto> list() { return movieService.findAll(); }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    movieService.deleteById(id);
    return ResponseEntity.noContent().build();
  }
}
```

> **포인트** — 응답 본문에 엔티티를 직접 노출하지 않는다. DTO 로 변환하는 이유:
> - 엔티티 변경이 API 스펙 변경으로 직결되는 결합 방지
> - 노출 불필요한 필드 차단
> - 응답 전용 필드 추가 가능

### 4-3. 예외 처리는 일단 단순하게

- `deleteById(없는 id)` → Spring Data JPA 가 조용히 무시 (`findById().ifPresent(::delete)`)
- 커스텀 예외, `@RestControllerAdvice` 는 다음 차시 도입 예정 — 지금은 핵심 흐름에 집중

---

## Session 5. 실행 검증과 GitHub 배포

### 5-1. 동작 검증 시나리오

```bash
# 동기화
curl -X POST "http://localhost:9000/api/movies/popular/sync?page=1"
# → {"page":1,"saved":20}

# 조회
curl http://localhost:9000/api/movies/popular | jq '. | length'
# → 20

# 삭제
curl -i -X DELETE http://localhost:9000/api/movies/popular/1226863
# → 204 No Content

# 같은 id 재삭제 (멱등성 시연)
curl -i -X DELETE http://localhost:9000/api/movies/popular/1226863
# → 204 No Content (변화 없음)
```

> **포인트** — HTTP 메서드의 멱등성 정의. `DELETE` 는 멱등.

### 5-2. DB 직접 조회

```sql
SELECT id, title, genre_ids, vote_average FROM popular_movie
ORDER BY popularity DESC LIMIT 5;
```

> **포인트** — JPA 가 만든 SQL 을 실제로 보여주기. `show-sql: true` 로그와 매칭.

### 5-3. Git / GitHub 배포

```bash
git init -b main
git add .
git commit -m "feat: TMDB 인기영화 sync/list/delete 구현"

gh repo create movie --public --source=. --push
```

> **포인트** — 커밋 전 보안 체크리스트
> 1. yaml 에 비밀값이 평문으로 남아있는가?
> 2. `.gitignore` 에 `.env`, IDE 설정 파일 포함되어 있는가?
> 3. 환경변수 사용 안내가 README 에 있는가?

---

## 다음 차시 예고

- 장르 테이블 분리 → 다대다 매핑 (`@ManyToMany` 또는 `@OneToMany` 두 단계)
- `@RestControllerAdvice` 로 전역 예외 처리
- 페이징/정렬 (`Pageable`, `Page<T>`)
- 단위 테스트 (`@DataJpaTest`, `@WebMvcTest`)

---

## 자주 나오는 질문

| 질문 | 답변 요약 |
|---|---|
| TMDB id 를 PK 로 쓰면 우리 시스템 PK 와 충돌할 수 있지 않나요? | 가능. 단일 데이터 소스 학습 단계에서는 단순함이 우선. 다음 단원에서 자체 PK + `tmdb_id UNIQUE` 분리. |
| `genre_ids` 를 `String` 으로 저장하면 검색 못하잖아요? | 맞음. 그래서 다음 단원에서 정규화. |
| 왜 응답 DTO 가 요청 DTO(`TmdbMovieDto`) 와 같은가요? | 이번 단계에선 동일 형태가 클라이언트 일관성에 도움. 실제 프로젝트에선 응답 DTO 별도 분리가 일반적. |
| `ddl-auto: create` 는 데이터를 다 날리지 않나요? | 맞음. 학습 환경 한정. 운영은 `validate` + 마이그레이션 도구. |
