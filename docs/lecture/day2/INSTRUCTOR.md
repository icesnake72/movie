# 강의 자료 — Day 2: JPQL 심화 & OpenAPI 문서화 (강사용)

**과정명**: Spring Boot 실무 — JPQL 심화, 다양한 조회 API, Springdoc OpenAPI
**대상**: Day 1 완료자 (다대다 관계, 인덱스 이해)
**총 소요시간**: 4시간 (60분 × 4세션)
**선수 지식**: Day 1 내용, JPQL 기초, REST API 구현 경험

---

## 학습 목표

- JPQL 과 SQL 의 핵심 차이를 설명하고, 엔티티 기반 쿼리를 직접 작성할 수 있다 (적용)
- `ORDER BY`, `GROUP BY`, 집계 함수를 JPQL 에서 활용할 수 있다 (적용)
- 정렬·통계·특정 영화의 장르 조회 API 를 구현할 수 있다 (적용)
- Springdoc OpenAPI 의존성을 추가하고 Swagger UI 를 통해 API 를 문서화할 수 있다 (적용)
- `@Operation`, `@ApiResponse`, `@Schema` 어노테이션으로 API 스펙을 표현할 수 있다 (적용)

## 최종 산출물

- `GET /api/movies/popular?sort=popularity` — 정렬 API
- `GET /api/genres/stats` — 장르별 영화 수 통계 API
- `GET /api/movies/popular/{id}/genres` — 특정 영화의 장르 조회 API
- Swagger UI (`http://localhost:9000/swagger-ui.html`) 에 전체 API 문서화

---

## Session 1. JPQL 복습 및 심화 (60분)

### 1-1. JPQL vs SQL 핵심 차이 (15분)

JPQL 은 SQL 을 흉내 낸 것처럼 보이지만 **대상이 다르다**.

| 구분 | SQL | JPQL |
|------|-----|------|
| 대상 | 테이블, 컬럼 | 엔티티 클래스, 필드 |
| 예시 | `FROM popular_movie` | `FROM Movie m` |
| FK 접근 | `JOIN movie_genre ON ...` | `JOIN m.movieGenres mg` |
| 컬럼명 | `vote_average` | `m.voteAverage` |
| 결과 | 행(Row) | 객체(Object) |

**직접 대조:**

```sql
-- SQL
SELECT m.id, m.title, m.vote_average
FROM popular_movie m
INNER JOIN movie_genre mg ON mg.movie_id = m.id
INNER JOIN genre g ON g.id = mg.genre_id
WHERE g.id = 28
ORDER BY m.vote_average DESC;
```

```java
// JPQL — 동일 쿼리
@Query("SELECT DISTINCT m FROM Movie m "
     + "JOIN FETCH m.movieGenres mg "
     + "JOIN FETCH mg.genre "
     + "WHERE mg.genre.id = :genreId "
     + "ORDER BY m.voteAverage DESC")
List<Movie> findByGenreIdOrderByVoteAverageDesc(@Param("genreId") Long genreId);
```

> **강의 포인트**
> - WHY: JPQL 은 DB 벤더(MySQL, PostgreSQL, Oracle)에 독립적이다. SQL 은 방언(dialect)이 다르지만 JPQL 은 동일하게 작성하면 Hibernate 가 해당 DB 방언으로 변환해준다.
> - PITFALL: `popular_movie` 대신 `Movie` 를 쓰는 것은 클래스명이다. 패키지 없이 클래스명만 사용. `@Entity(name = "...")` 으로 변경 가능하지만 관례상 클래스명을 그대로 쓴다.

---

### 1-2. 기본 문법 정리 (20분)

**SELECT 절:**

```java
// 전체 엔티티 반환
@Query("SELECT m FROM Movie m")
List<Movie> findAll();

// 특정 필드만 반환 (Object[] 또는 DTO Projection)
@Query("SELECT m.id, m.title, m.voteAverage FROM Movie m")
List<Object[]> findIdTitleVoteAverage();

// DTO Projection (권장) — new 키워드 + 전체 경로
@Query("SELECT new com.example.movie.movie.dto.MovieSummaryDto(m.id, m.title, m.voteAverage) FROM Movie m")
List<MovieSummaryDto> findSummary();
```

**WHERE 절:**

```java
// 단일 조건
@Query("SELECT m FROM Movie m WHERE m.voteAverage >= :minVote")
List<Movie> findByVoteAverageGreaterThan(@Param("minVote") Double minVote);

// 복합 조건
@Query("SELECT m FROM Movie m WHERE m.adult = false AND m.voteAverage >= :minVote")
List<Movie> findNonAdultByVote(@Param("minVote") Double minVote);

// IN 절
@Query("SELECT m FROM Movie m WHERE m.id IN :ids")
List<Movie> findByIds(@Param("ids") List<Long> ids);
```

**ORDER BY:**

```java
// 내림차순 정렬
@Query("SELECT m FROM Movie m ORDER BY m.popularity DESC")
List<Movie> findAllOrderByPopularityDesc();

// 다중 정렬
@Query("SELECT m FROM Movie m ORDER BY m.voteAverage DESC, m.voteCount DESC")
List<Movie> findAllOrderByVoteAverageDescAndVoteCountDesc();
```

**집계 함수:**

```java
// COUNT, AVG, MAX, MIN
@Query("SELECT COUNT(m) FROM Movie m WHERE m.adult = false")
long countNonAdult();

@Query("SELECT AVG(m.voteAverage) FROM Movie m")
Double findAverageVoteAverage();

@Query("SELECT MAX(m.popularity) FROM Movie m")
Double findMaxPopularity();
```

> **강의 포인트**
> - WHAT: JPQL 집계 함수는 SQL 과 동일하지만 반환 타입에 주의. `COUNT` → `Long`, `AVG/MAX/MIN` → `Double` 또는 `null` 가능.
> - PITFALL: `COUNT(m)` 과 `COUNT(m.id)` 는 결과가 같지만 `COUNT(m.movieGenres)` 는 컬렉션을 세는 것이라 다르다.

---

### 1-3. GROUP BY 와 집계 쿼리 (25분)

**장르별 영화 수 집계:**

```java
// Object[] 방식 (권장하지 않음 — 타입 불안전)
@Query("SELECT mg.genre.id, mg.genre.name, COUNT(mg) "
     + "FROM MovieGenre mg "
     + "GROUP BY mg.genre.id, mg.genre.name "
     + "ORDER BY COUNT(mg) DESC")
List<Object[]> findGenreStatsAsObjects();

// DTO Projection 방식 (권장)
@Query("SELECT new com.example.movie.genre.dto.GenreStatDto("
     + "mg.genre.id, mg.genre.name, COUNT(mg)) "
     + "FROM MovieGenre mg "
     + "GROUP BY mg.genre.id, mg.genre.name "
     + "ORDER BY COUNT(mg) DESC")
List<GenreStatDto> findGenreStats();
```

**GenreStatDto 클래스 (record 로 작성):**

```java
// GenreStatDto.java
package com.example.movie.genre.dto;

public record GenreStatDto(Long id, String name, Long movieCount) {}
```

> **강의 포인트**
> - WHY: `record` 는 Java 16+ 에서 도입된 불변 데이터 클래스. `equals`, `hashCode`, `toString`, 생성자가 자동 생성됨.
> - WHAT: JPQL 의 `new` 키워드는 **전체 패키지 경로**가 필요하다. 클래스명만 쓰면 안 된다.
> - PITFALL: `FROM MovieGenre mg` 에서 시작하면 장르가 없는 영화는 통계에 포함되지 않는다. `FROM Genre g LEFT JOIN MovieGenre mg ON ...` 으로 써야 장르가 있어도 0 건 장르가 보인다.

**LEFT JOIN 버전 (장르가 0건인 경우도 포함):**

```java
@Query("SELECT new com.example.movie.genre.dto.GenreStatDto("
     + "g.id, g.name, COUNT(mg)) "
     + "FROM Genre g LEFT JOIN MovieGenre mg ON mg.genre = g "
     + "GROUP BY g.id, g.name "
     + "ORDER BY COUNT(mg) DESC")
List<GenreStatDto> findGenreStatsIncludingEmpty();
```

---

## Session 2. 다양한 조회 API 구현 (60분)

### 2-1. 정렬 API 구현 (20분)

**목표:** `GET /api/movies/popular?sort=popularity` 또는 `sort=voteAverage`

**MovieRepository.java 에 추가:**

```java
@Query("SELECT DISTINCT m FROM Movie m "
     + "LEFT JOIN FETCH m.movieGenres mg "
     + "LEFT JOIN FETCH mg.genre "
     + "ORDER BY m.popularity DESC")
List<Movie> findAllOrderByPopularityDesc();

@Query("SELECT DISTINCT m FROM Movie m "
     + "LEFT JOIN FETCH m.movieGenres mg "
     + "LEFT JOIN FETCH mg.genre "
     + "ORDER BY m.voteAverage DESC")
List<Movie> findAllOrderByVoteAverageDesc();
```

**MovieService.java 에 추가:**

```java
@Transactional(readOnly = true)
public List<TmdbMovieDto> findAllSorted(String sort) {
  List<Movie> movies = switch (sort) {
    case "popularity" -> movieRepository.findAllOrderByPopularityDesc();
    case "voteAverage" -> movieRepository.findAllOrderByVoteAverageDesc();
    default -> movieRepository.findAllWithGenres();
  };
  return movies.stream().map(this::toDto).toList();
}
```

**MovieController.java — list() 메서드 최종 버전:**

```java
@GetMapping
public List<TmdbMovieDto> list(
    @RequestParam(required = false) Long genreId,
    @RequestParam(required = false) String title,
    @RequestParam(required = false) String sort) {
  if (genreId != null) return movieService.findByGenreId(genreId);
  if (title != null) return movieService.findByTitle(title);
  if (sort != null) return movieService.findAllSorted(sort);
  return movieService.findAll();
}
```

> **강의 포인트**
> - PITFALL: `ORDER BY` 와 `JOIN FETCH` 를 함께 쓸 때 Hibernate 가 경고를 낸다: `HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory!`. 페이징과 함께 쓰면 전체를 메모리로 올린 후 정렬하므로 위험. 지금은 정렬만이라 괜찮지만 페이징을 추가하면 별도 처리 필요.
> - WHY: Java 21 의 `switch` 표현식. `case "popularity" ->` 형태로 간결하게 작성 가능.

---

### 2-2. 장르별 통계 API 구현 (20분)

**목표:** `GET /api/genres/stats` → 장르별 영화 수 반환

**GenreRepository.java 에 추가:**

```java
@Query("SELECT new com.example.movie.genre.dto.GenreStatDto("
     + "g.id, g.name, COUNT(mg)) "
     + "FROM Genre g LEFT JOIN MovieGenre mg ON mg.genre = g "
     + "GROUP BY g.id, g.name "
     + "ORDER BY COUNT(mg) DESC")
List<GenreStatDto> findGenreStats();
```

**GenreService.java 에 추가:**

```java
@Transactional(readOnly = true)
public List<GenreStatDto> getStats() {
  return genreRepository.findGenreStats();
}
```

**GenreController.java 에 추가:**

```java
@GetMapping("/stats")
public List<GenreStatDto> stats() {
  return genreService.getStats();
}
```

**응답 예시:**

```json
[
  {"id": 28, "name": "액션", "movieCount": 12},
  {"id": 35, "name": "코미디", "movieCount": 8},
  {"id": 18, "name": "드라마", "movieCount": 6}
]
```

---

### 2-3. 특정 영화의 장르 조회 API (20분)

**목표:** `GET /api/movies/popular/{id}/genres`

**MovieRepository.java 에 추가:**

```java
@Query("SELECT mg.genre FROM MovieGenre mg WHERE mg.movie.id = :movieId")
List<Genre> findGenresByMovieId(@Param("movieId") Long movieId);
```

**MovieService.java 에 추가:**

```java
@Transactional(readOnly = true)
public List<TmdbMovieDto.GenreInfo> findGenresByMovieId(Long movieId) {
  return movieRepository.findGenresByMovieId(movieId).stream()
      .map(g -> {
        TmdbMovieDto.GenreInfo info = new TmdbMovieDto.GenreInfo();
        info.setId(g.getId());
        info.setName(g.getName());
        return info;
      })
      .toList();
}
```

**MovieController.java 에 추가:**

```java
@GetMapping("/{id}/genres")
public List<TmdbMovieDto.GenreInfo> genres(@PathVariable("id") Long id) {
  return movieService.findGenresByMovieId(id);
}
```

> **강의 포인트**
> - WHAT: `SELECT mg.genre FROM MovieGenre mg` — SELECT 절에 엔티티의 연관 필드를 직접 쓸 수 있다. 결과는 `Genre` 객체 리스트.
> - 404 처리: 존재하지 않는 id 를 넘기면 빈 리스트가 반환된다. 엄밀히는 404 를 반환해야 하지만 예외 처리는 다음 차시 주제.

---

## Session 3. Springdoc OpenAPI 도입 (60분)

### 3-1. 의존성 추가 (5분)

**build.gradle 에 추가:**

```groovy
dependencies {
  // 기존 의존성 ...
  implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.8'
}
```

**application.yaml 에 추가:**

```yaml
springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
    tags-sorter: alpha
    operations-sorter: alpha
  default-consumes-media-type: application/json
  default-produces-media-type: application/json
```

앱 재기동 후 `http://localhost:9000/swagger-ui.html` 접속 → 자동 생성된 UI 확인.

> **강의 포인트**
> - WHY: API 문서는 코드와 별도로 관리하면 반드시 불일치가 생긴다. 코드에서 직접 생성하는 방식(Code-First)은 항상 최신 상태를 보장한다.
> - WHAT: Springdoc 은 Spring MVC 의 `@RequestMapping`, `@GetMapping` 등을 스캔해서 자동으로 OpenAPI 3.0 스펙 JSON 을 생성한다. Swagger UI 는 이 JSON 을 시각화한다.

---

### 3-2. OpenAPI 전체 메타데이터 설정 (10분)

```java
// config/OpenApiConfig.java
package com.example.movie.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI openAPI() {
    return new OpenAPI()
        .info(new Info()
            .title("TMDB 인기영화 API")
            .description("TMDB 에서 동기화한 인기영화 및 장르 관리 REST API")
            .version("1.0.0")
            .contact(new Contact()
                .name("Movie Team")
                .email("team@example.com")));
  }
}
```

---

### 3-3. @Operation, @ApiResponse 기본 사용 (25분)

**MovieController.java — 어노테이션 추가:**

```java
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/movies/popular")
@RequiredArgsConstructor
@Tag(name = "Movie", description = "인기 영화 관리 API")
public class MovieController {

  private final MovieService movieService;

  @Operation(
      summary = "TMDB 인기영화 동기화",
      description = "TMDB /movie/popular 엔드포인트에서 지정한 페이지의 영화 20건을 DB 에 저장합니다. "
                  + "genres/sync 를 먼저 실행해야 장르 연결이 됩니다."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "저장 완료 — page 와 saved 건수 반환"),
      @ApiResponse(responseCode = "500", description = "TMDB API 호출 실패")
  })
  @PostMapping("/sync")
  public ResponseEntity<Map<String, Object>> sync(
      @Parameter(description = "TMDB 페이지 번호 (1 이상)", example = "1")
      @RequestParam(name = "page", defaultValue = "1") int page) {
    int saved = movieService.syncPopularMovies(page);
    return ResponseEntity.ok(Map.of("page", page, "saved", saved));
  }

  @Operation(
      summary = "인기영화 조회",
      description = "저장된 인기영화 목록을 반환합니다. genreId, title, sort 파라미터로 필터링/정렬 가능합니다."
  )
  @GetMapping
  public List<TmdbMovieDto> list(
      @Parameter(description = "장르 ID 로 필터링 (예: 28=액션, 878=SF)", example = "28")
      @RequestParam(required = false) Long genreId,
      @Parameter(description = "제목 검색 (부분 일치, 대소문자 무시)", example = "어벤")
      @RequestParam(required = false) String title,
      @Parameter(description = "정렬 기준: popularity | voteAverage", example = "popularity")
      @RequestParam(required = false) String sort) {
    if (genreId != null) return movieService.findByGenreId(genreId);
    if (title != null) return movieService.findByTitle(title);
    if (sort != null) return movieService.findAllSorted(sort);
    return movieService.findAll();
  }

  @Operation(summary = "영화 단건 삭제")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "삭제 완료"),
      @ApiResponse(responseCode = "404", description = "해당 ID 없음 (현재 미구현 — 조용히 처리)")
  })
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @Parameter(description = "삭제할 영화의 TMDB ID", example = "1226863")
      @PathVariable("id") Long id) {
    movieService.deleteById(id);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "특정 영화의 장르 목록 조회")
  @GetMapping("/{id}/genres")
  public List<TmdbMovieDto.GenreInfo> genres(
      @Parameter(description = "영화 TMDB ID", example = "1226863")
      @PathVariable("id") Long id) {
    return movieService.findGenresByMovieId(id);
  }
}
```

> **강의 포인트**
> - WHY: `@Tag` 는 Swagger UI 에서 그룹을 만든다. Controller 마다 하나씩 달면 "Movie", "Genre" 탭으로 구분됨.
> - WHAT: `@Operation(summary)` 는 짧은 한 줄 설명, `description` 은 상세 설명.
> - PITFALL: `@ApiResponse(responseCode = "404")` 를 달아도 실제 로직이 404 를 반환하지 않으면 거짓 문서가 된다. 문서와 코드의 일치를 유지하는 것이 중요.

---

### 3-4. @Schema 로 DTO 문서화 (20분)

**TmdbMovieDto.java — 핵심 필드에 @Schema 추가:**

```java
import io.swagger.v3.oas.annotations.media.Schema;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "TMDB 영화 정보")
public class TmdbMovieDto {

  @Schema(description = "TMDB 영화 고유 ID (PK 로 그대로 사용)", example = "1226863")
  private Long id;

  @Schema(description = "성인 콘텐츠 여부", example = "false")
  private boolean adult;

  @Schema(description = "배경 이미지 경로 (https://image.tmdb.org/t/p/{size}{path} 형태로 조립)", example = "/abc.jpg")
  @JsonProperty("backdrop_path")
  private String backdropPath;

  @Schema(description = "장르 ID 목록", example = "[28, 878]")
  @JsonProperty("genre_ids")
  private List<Integer> genreIds;

  @Schema(description = "한국어 제목", example = "어벤져스: 엔드게임")
  private String title;

  @Schema(description = "평균 평점 (0.0 ~ 10.0)", example = "8.3")
  @JsonProperty("vote_average")
  private Double voteAverage;

  @Schema(description = "투표 수 (낮으면 평점 신뢰도 낮음)", example = "25000")
  @JsonProperty("vote_count")
  private Integer voteCount;

  @Schema(description = "TMDB 인기도 점수 (높을수록 최근에 인기)", example = "1523.7")
  private Double popularity;

  // 나머지 필드 ...

  @Schema(description = "장르 상세 목록 (id + name)")
  private List<GenreInfo> genres;

  @Getter @Setter @NoArgsConstructor @AllArgsConstructor
  @Schema(description = "장르 정보")
  public static class GenreInfo {
    @Schema(description = "TMDB 장르 ID", example = "28")
    private Long id;
    @Schema(description = "장르명", example = "액션")
    private String name;
  }
}
```

**GenreStatDto.java — record 에 @Schema:**

```java
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "장르별 영화 수 통계")
public record GenreStatDto(
    @Schema(description = "장르 ID", example = "28") Long id,
    @Schema(description = "장르명", example = "액션") String name,
    @Schema(description = "해당 장르에 속한 영화 수", example = "12") Long movieCount
) {}
```

---

## Session 4. OpenAPI 실습 + 전체 정리 (60분)

### 4-1. GenreController 문서화 (15분)

```java
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/genres")
@RequiredArgsConstructor
@Tag(name = "Genre", description = "장르 관리 API")
public class GenreController {

  private final GenreService genreService;

  @Operation(
      summary = "TMDB 장르 동기화",
      description = "TMDB /genre/movie/list 에서 장르 목록을 가져와 DB 에 저장합니다. "
                  + "movies/sync 실행 전에 반드시 먼저 실행해야 합니다."
  )
  @PostMapping("/sync")
  public Map<String, Object> sync() {
    int synced = genreService.syncGenres();
    return Map.of("synced", synced);
  }

  @Operation(summary = "장르 전체 조회")
  @GetMapping
  public List<TmdbGenreDto> list() {
    return genreService.findAll();
  }

  @Operation(summary = "장르별 영화 수 통계")
  @GetMapping("/stats")
  public List<GenreStatDto> stats() {
    return genreService.getStats();
  }
}
```

---

### 4-2. Swagger UI 직접 테스트 시연 (20분)

`http://localhost:9000/swagger-ui.html` 에서 순서대로 실행:

1. **Genre → POST /api/genres/sync** → Execute → 19 synced 확인
2. **Movie → POST /api/movies/popular/sync** → page=1 → Execute → 20 saved 확인
3. **Movie → GET /api/movies/popular** → Execute → 영화 목록 확인
4. **Movie → GET /api/movies/popular** → genreId=28 → Execute → 액션 영화만 확인
5. **Genre → GET /api/genres/stats** → Execute → 장르별 영화 수 확인

> **강의 포인트**
> - WHY: Swagger UI 는 API 문서이자 테스트 도구. curl 명령을 외울 필요 없이 브라우저에서 직접 시험 가능.
> - 실무 활용: 프론트엔드 개발자, QA 팀이 API 스펙을 확인하고 테스트하는 표준 도구. Postman 을 대체하거나 보완.
> - PITFALL: Swagger UI 에서 `/api/movies/popular/sync` 를 실행하면 실제 TMDB API 를 호출하고 DB 에 저장된다. 개발 환경에서는 괜찮지만 운영 환경에서 Swagger UI 를 열어두면 위험.

---

### 4-3. 최종 API 목록 정리 (10분)

| 메서드 | 경로 | 설명 | Day |
|--------|------|------|-----|
| POST | /api/genres/sync | 장르 동기화 | 기존 |
| GET | /api/genres | 장르 전체 조회 | 기존 |
| GET | /api/genres/stats | 장르별 영화 수 통계 | **Day 2 신규** |
| POST | /api/movies/popular/sync | 영화 동기화 | 기존 |
| GET | /api/movies/popular | 전체 조회 / 필터 / 정렬 | 기존 + **Day 1 확장** |
| GET | /api/movies/popular?genreId={id} | 장르별 필터링 | **Day 1 신규** |
| GET | /api/movies/popular?title={t} | 제목 검색 | **Day 1 신규** |
| GET | /api/movies/popular?sort={s} | 정렬 | **Day 2 신규** |
| GET | /api/movies/popular/{id}/genres | 영화의 장르 조회 | **Day 2 신규** |
| DELETE | /api/movies/popular/{id} | 영화 삭제 | 기존 |

---

### 4-4. 2일 전체 핵심 요약 (15분)

```
Day 1
├── N:M 관계는 반드시 중간 테이블로 분리
│   └── @ManyToMany 대신 MovieGenre 엔티티 직접 생성
├── 인덱스: 읽기 속도 ↑, 쓰기 속도 ↓ 트레이드오프
│   └── FK 컬럼, ORDER BY 컬럼, WHERE 컬럼에 우선 적용
└── JPQL: 엔티티 경로 탐색 + JOIN FETCH 로 N+1 방지

Day 2
├── JPQL 심화: ORDER BY, GROUP BY, DTO Projection
├── 다양한 조회 API: 정렬, 통계, 서브리소스
└── Springdoc OpenAPI: 의존성 → Config → 어노테이션 → Swagger UI
```

---

## 트러블슈팅 가이드 — Day 2

| 증상 | 원인 | 해결 방법 |
|------|------|-----------|
| Swagger UI 접속 안 됨 | 의존성 누락 또는 포트 오류 | build.gradle 의존성 확인, `http://localhost:9000/swagger-ui.html` |
| `NoSuchBeanDefinitionException: OpenAPI` | springdoc 의존성 버전 문제 | `2.8.8` 로 명시 |
| DTO Projection `null` 반환 | JPQL `new` 키워드에 전체 패키지 경로 누락 | `new com.example.movie.genre.dto.GenreStatDto(...)` |
| `HHH90003004` 경고 | ORDER BY + JOIN FETCH + 페이징 조합 | 페이징 없이 정렬만이면 무시 가능 |
| Swagger UI 에서 응답 형식 깨짐 | `@Schema` 미적용 필드 | 응답 DTO 에 `@Schema` 추가 |
| `GroupByException` | GROUP BY 절에 SELECT 절 컬럼 누락 | `GROUP BY g.id, g.name` 확인 |

## 자주 나오는 질문

| 질문 | 답변 요약 | 심화 설명 |
|------|-----------|-----------|
| SQL 이 익숙한데 JPQL 을 꼭 써야 하나요? | 필수는 아니지만 권장. `@NativeQuery` 로 SQL 도 사용 가능 | JPQL 은 DB 독립적. Native SQL 은 특정 DB 함수(`GROUP_CONCAT` 등) 사용 가능 |
| `record` 를 JPQL DTO Projection 으로 쓸 수 있나요? | Spring Boot 3.x + Hibernate 6.x 에서 가능 | 불변이라 JPA 프록시 생성 불가. 조회 전용 DTO 에만 사용 가능 |
| 운영 환경에서 Swagger UI 를 비활성화하려면? | `springdoc.swagger-ui.enabled=false` | `application-prod.yaml` 에 false 설정 |
| `@ApiResponse` 에 응답 Body 스펙도 넣을 수 있나요? | `@ApiResponse(content = @Content(schema = @Schema(implementation = TmdbMovieDto.class)))` | 명시하지 않으면 Springdoc 이 반환 타입에서 자동 추론 |
