# 강의 자료 (강사용)

**과정명**: TMDB 인기영화 백엔드 구축 — Spring Boot 입문 실습
**대상**: Java 기초 + Spring 입문 단계 학생
**총 소요시간**: 약 5시간 (90분 × 3강 또는 60분 × 5강 권장)
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

## Session 1. 프로젝트 셋업과 DB 연결 (45분)

### 1-1. Spring Initializr 프로젝트 생성 (10분)

- [https://start.spring.io](https://start.spring.io) 사용
- 의존성: **Spring Web**, **Spring Data JPA**, **MySQL Driver**, **Validation**, **Lombok**
- Java 21, Gradle, Spring Boot 3.5.x

### 1-2. MySQL 데이터베이스 준비 (10분)

```sql
CREATE DATABASE IF NOT EXISTS movie DEFAULT CHARACTER SET utf8mb4;
```

> **강의 포인트** — DB 는 사람이 만들고, 테이블은 코드(JPA)가 만든다. 책임 분리 개념 강조.

### 1-3. application.yaml 설정 (15분)

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

> **강의 포인트** — `ddl-auto` 옵션 비교
> - `create`: 매 기동마다 DROP + CREATE (학습용)
> - `update`: 차이만 반영 (개발 중)
> - `validate`: 차이 있으면 부팅 실패 (운영)
> - `none`: 자동 작업 안 함 (운영 권장 + Flyway/Liquibase)

### 1-4. 첫 부팅 (10분)

```bash
./gradlew bootRun
```

- 콘솔 로그에서 `Started MovieApplication in X seconds` 확인
- DB 접속 실패 시 트러블슈팅 시연 (포트, 비번, allowPublicKeyRetrieval)

---

## Session 2. 단일 엔티티 설계 (60분)

### 2-1. TMDB 응답 JSON 살펴보기 (10분)

```bash
curl "https://api.themoviedb.org/3/movie/popular?language=ko-KR&page=1" \
  -H "Authorization: Bearer <TOKEN>"
```

- 응답 구조 분석: `page`, `results[]`, `total_pages`, `total_results`
- `results[]` 배열의 각 영화 필드 식별

### 2-2. Movie 엔티티 작성 (30분)

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

  @Builder.Default
  @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL,
             orphanRemoval = true, fetch = FetchType.LAZY)
  private List<MovieGenre> movieGenres = new ArrayList<>();
}
```

> **강의 포인트**
> - `@NoArgsConstructor(PROTECTED)` — JPA 가 리플렉션으로 인스턴스 생성하기 위해 필요. 외부에서 `new Movie()` 호출 방지.
> - `@Builder` — 필드 많을 때 가독성 있게 객체 생성.
> - 컬럼명 `snake_case`, 자바 필드명 `camelCase` 관례.
> - `@Builder.Default` — `@Builder` 사용 시 컬렉션 필드는 반드시 기본값 초기화 필요. 없으면 `null` 반환으로 NPE 발생.

### 2-3. Genre / MovieGenre 엔티티 설계 — 다대다 관계 분리 (25분)

장르는 여러 영화에 속하고, 영화는 여러 장르를 가집니다. 이런 **다대다(N:M) 관계**는 DB 에서 직접 표현할 수 없기 때문에 중간 테이블이 필요합니다.

```
popular_movie (N) ←→ movie_genre ←→ genre (N)
```

**Genre 엔티티 — genre 테이블**

```java
@Entity
@Table(name = "genre")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Genre {
  @Id
  private Long id;  // TMDB 장르 ID 를 PK 로 직접 사용

  @Column(name = "name", length = 100, nullable = false)
  private String name;
}
```

**MovieGenre 엔티티 — movie_genre 중간 테이블**

```java
@Entity
@Table(
    name = "movie_genre",
    uniqueConstraints = @UniqueConstraint(columnNames = {"movie_id", "genre_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class MovieGenre {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "movie_id", nullable = false)
  private Movie movie;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "genre_id", nullable = false)
  private Genre genre;
}
```

> **강의 포인트**
> - `@ManyToMany` 를 쓰지 않고 중간 엔티티를 직접 만드는 이유 — `@ManyToMany` 는 중간 테이블에 컬럼을 추가할 수 없음. 중간 엔티티로 분리하면 향후 장르 순서, 주요 장르 여부 같은 속성 확장이 가능.
> - `cascade = ALL + orphanRemoval = true` — Movie 삭제 시 연관 MovieGenre 레코드 자동 삭제. Genre 는 독립 도메인이라 cascade 대상에서 제외.
> - `fetch = LAZY` — 영화 조회 시 장르를 항상 함께 불러오지 않음. 필요 시 fetch join 으로 한 번에 조회 (N+1 방지).
> - `uniqueConstraints` — 동일 영화에 같은 장르가 중복 저장되는 것을 DB 레벨에서 차단.

### 2-4. Repository 작성 (10분)

```java
public interface MovieRepository extends JpaRepository<Movie, Long> {

  @Query("SELECT DISTINCT m FROM Movie m "
       + "LEFT JOIN FETCH m.movieGenres mg "
       + "LEFT JOIN FETCH mg.genre")
  List<Movie> findAllWithGenres();
}

public interface GenreRepository extends JpaRepository<Genre, Long> {}
```

> **강의 포인트**
> - `JpaRepository` 한 줄로 `save / findAll / findById / deleteById / count` 등 자동 제공.
> - `findAllWithGenres()` — `findAll()` 대신 사용. `LEFT JOIN FETCH` 로 Movie, MovieGenre, Genre 를 한 쿼리로 로딩해 N+1 쿼리 방지.
> - `DISTINCT` 필수 — JOIN 시 Movie 레코드가 장르 수만큼 중복 반환되는 것을 방지.

---

## Session 3. 외부 API 호출과 DTO (75분)

### 3-1. TMDB 토큰 발급과 환경변수 (10분)

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

> **강의 포인트** — 비밀값을 코드/yaml 에 박지 말 것. `${ENV:default}` 패턴은 환경변수 우선, 없으면 기본값.

### 3-2. RestClient 빈 구성 (15분)

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

> **강의 포인트** — RestTemplate 은 deprecated, RestClient(Spring 6+) 사용. WebClient 와 차이도 한 줄로 언급.

### 3-3. TmdbMovieDto / TmdbPopularResponse POJO (35분)

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

> **강의 포인트** — 핵심 어노테이션 3종 비교
> - `@JsonProperty("snake_case")` — JSON 키 ↔ Java 필드 매핑
> - `@JsonIgnoreProperties(ignoreUnknown = true)` — 모르는 필드 무시 (외부 API 안정성)
> - `@JsonNaming` — 클래스 단위 네이밍 전략 (참고만, 직접 사용은 학생 익숙해진 뒤)

### 3-4. record vs class 비교 시연 (15분)

- record 의 간결함과 한계(불변, 상속 불가, Lombok 와 충돌)
- 학습 초기엔 익숙한 POJO 권장

---

## Session 4. Service / Controller 구성 (60분)

### 4-1. MovieService — TMDB 호출과 변환 (30분)

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

> **강의 포인트**
> - `@Transactional` 의 의미: 메서드 단위 트랜잭션 경계
> - `saveAll` 의 동작: PK 가 있으면 upsert
> - DTO ↔ Entity 변환은 항상 Service 책임

### 4-2. MovieController — REST 엔드포인트 (15분)

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

> **강의 포인트** — 응답 본문에 엔티티를 직접 노출하지 않는다. DTO 로 변환하는 이유:
> - 엔티티 변경이 API 스펙 변경으로 직결되는 결합 방지
> - 노출 불필요한 필드 차단
> - 응답 전용 필드 추가 가능

### 4-3. 예외 처리는 일단 단순하게 (15분)

- `deleteById(없는 id)` → Spring Data JPA 가 조용히 무시 (`findById().ifPresent(::delete)`)
- 커스텀 예외, `@RestControllerAdvice` 는 다음 차시 도입 예정 — 지금은 핵심 흐름에 집중

---

## Session 5. 실행 검증과 GitHub 배포 (60분)

### 5-1. 동작 검증 시나리오 (25분)

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

> **강의 포인트** — HTTP 메서드의 멱등성 정의. `DELETE` 는 멱등.

### 5-2. DB 직접 조회 (10분)

```sql
-- 인기 영화 + 장르 목록 함께 조회
SELECT m.id, m.title,
       GROUP_CONCAT(g.name ORDER BY g.name SEPARATOR ', ') AS genres,
       m.vote_average
FROM popular_movie m
LEFT JOIN movie_genre mg ON mg.movie_id = m.id
LEFT JOIN genre g ON g.id = mg.genre_id
GROUP BY m.id, m.title, m.vote_average
ORDER BY m.popularity DESC
LIMIT 5;
```

> **강의 포인트**
> - JPA 가 만든 SQL 을 `show-sql: true` 로그와 매칭해서 보여주기.
> - `GROUP_CONCAT` — 한 영화의 여러 장르 행을 하나의 문자열로 묶어 주는 MySQL 집계 함수. JPA fetch join 결과와 비교하면 동일한 데이터임을 확인 가능.
> - 3개 테이블(popular_movie → movie_genre → genre) 조인 구조를 ERD 와 함께 설명.

### 5-3. Git / GitHub 배포 (25분)

```bash
git init -b main
git add .
git commit -m "feat: TMDB 인기영화 sync/list/delete 구현"

gh repo create movie --public --source=. --push
```

> **강의 포인트** — 커밋 전 보안 체크리스트
> 1. yaml 에 비밀값이 평문으로 남아있는가?
> 2. `.gitignore` 에 `.env`, IDE 설정 파일 포함되어 있는가?
> 3. 환경변수 사용 안내가 README 에 있는가?

---

## 다음 차시 예고 (5분)

- `@RestControllerAdvice` 로 전역 예외 처리
- 페이징/정렬 (`Pageable`, `Page<T>`)
- 단위 테스트 (`@DataJpaTest`, `@WebMvcTest`)

---

## 강의 진행 시 자주 나오는 질문

| 질문 | 답변 요약 |
|---|---|
| TMDB id 를 PK 로 쓰면 우리 시스템 PK 와 충돌할 수 있지 않나요? | 가능. 단일 데이터 소스 학습 단계에서는 단순함이 우선. 다음 단원에서 자체 PK + `tmdb_id UNIQUE` 분리. |
| `@ManyToMany` 를 쓰면 더 간단하지 않나요? | 간단하지만 중간 테이블에 컬럼을 추가할 수 없음. 실무에서는 `MovieGenre` 같은 중간 엔티티를 직접 만드는 방식이 표준. |
| 왜 응답 DTO 가 요청 DTO(`TmdbMovieDto`) 와 같은가요? | 이번 단계에선 동일 형태가 클라이언트 일관성에 도움. 실제 프로젝트에선 응답 DTO 별도 분리가 일반적. |
| `ddl-auto: create` 는 데이터를 다 날리지 않나요? | 맞음. 학습 환경 한정. 운영은 `validate` + 마이그레이션 도구. |
