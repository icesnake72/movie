# 강의 자료 — Day 2: JPQL 심화 & OpenAPI 문서화 (학생용)

**과정명**: Spring Boot 실무 — JPQL 심화, 다양한 조회 API, Springdoc OpenAPI
**선수 지식**: Day 1 완료 (다대다 관계, 인덱스, 검색 API)

---

## 학습 목표

- JPQL 과 SQL 의 핵심 차이를 설명하고 엔티티 기반 쿼리를 작성할 수 있다
- `ORDER BY`, `GROUP BY`, 집계 함수를 JPQL 에서 활용할 수 있다
- 정렬·통계·특정 영화의 장르 조회 API 를 구현할 수 있다
- Springdoc OpenAPI 를 추가하고 Swagger UI 에 API 를 문서화할 수 있다

## 최종 산출물

- `GET /api/movies/popular?sort=popularity` — 정렬 API
- `GET /api/genres/stats` — 장르별 영화 수 통계 API
- `GET /api/movies/popular/{id}/genres` — 특정 영화의 장르 조회 API
- `http://localhost:9000/swagger-ui.html` — 전체 API Swagger 문서

---

## Session 1. JPQL 복습 및 심화

### 1-1. JPQL vs SQL 핵심 차이

| 구분 | SQL | JPQL |
|------|-----|------|
| 대상 | 테이블, 컬럼명 | 엔티티 클래스, 필드명 |
| 테이블명 | `popular_movie` | `Movie` (클래스명) |
| FK 접근 | `JOIN movie_genre ON ...` | `JOIN m.movieGenres mg` |
| 컬럼명 | `vote_average` | `m.voteAverage` |

**비교 예시:**

```sql
-- SQL
SELECT m.id, m.title, m.vote_average
FROM popular_movie m
INNER JOIN movie_genre mg ON mg.movie_id = m.id
WHERE mg.genre_id = 28
ORDER BY m.vote_average DESC;
```

```java
// JPQL (동일 의미)
@Query("SELECT DISTINCT m FROM Movie m "
     + "JOIN FETCH m.movieGenres mg "
     + "JOIN FETCH mg.genre "
     + "WHERE mg.genre.id = :genreId "
     + "ORDER BY m.voteAverage DESC")
List<Movie> findByGenreIdOrderByVoteAverageDesc(@Param("genreId") Long genreId);
```

> **포인트** — JPQL 은 DB 독립적. MySQL, PostgreSQL, Oracle 모두 같은 코드로 동작.

---

### 1-2. 기본 문법 정리

**WHERE 조건:**

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

**정렬 및 집계:**

```java
// ORDER BY
@Query("SELECT m FROM Movie m ORDER BY m.popularity DESC")
List<Movie> findAllOrderByPopularityDesc();

// COUNT, AVG
@Query("SELECT COUNT(m) FROM Movie m")
long countAll();

@Query("SELECT AVG(m.voteAverage) FROM Movie m")
Double findAverageVoteAverage();
```

---

### 1-3. GROUP BY 와 DTO Projection

**GenreStatDto — record 클래스 생성:**

```java
// genre/dto/GenreStatDto.java
package com.example.movie.genre.dto;

public record GenreStatDto(Long id, String name, Long movieCount) {}
```

**GenreRepository.java 에 추가:**

```java
@Query("SELECT new com.example.movie.genre.dto.GenreStatDto("
     + "g.id, g.name, COUNT(mg)) "
     + "FROM Genre g LEFT JOIN MovieGenre mg ON mg.genre = g "
     + "GROUP BY g.id, g.name "
     + "ORDER BY COUNT(mg) DESC")
List<GenreStatDto> findGenreStats();
```

> **포인트**
> - JPQL `new` 키워드에는 **전체 패키지 경로**가 필요하다.
> - `record` 는 Java 16+ 불변 데이터 클래스. `equals`, `hashCode`, `toString` 자동 생성.
> - `LEFT JOIN` 을 사용해야 영화가 0편인 장르도 결과에 포함된다.

---

## Session 2. 다양한 조회 API 구현

### 2-1. 정렬 API

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

**MovieController.java — list() 최종 버전:**

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

테스트:
```bash
curl -s "http://localhost:9000/api/movies/popular?sort=popularity" | \
  python3 -c "import json,sys; [print(m['popularity'], m['title']) for m in json.load(sys.stdin)[:5]]"
```

---

### 2-2. 장르별 통계 API

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

테스트:
```bash
curl -s http://localhost:9000/api/genres/stats | python3 -c "
import json, sys
for g in json.load(sys.stdin)[:5]:
    print(f\"{g['name']}: {g['movieCount']}편\")
"
```

---

### 2-3. 특정 영화의 장르 조회 API

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

---

## Session 3. Springdoc OpenAPI 도입

### 3-1. 의존성 추가

**build.gradle:**

```groovy
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.8'
```

**application.yaml 에 추가:**

```yaml
springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
    tags-sorter: alpha
```

앱 재기동 → `http://localhost:9000/swagger-ui.html` 접속 확인.

---

### 3-2. OpenAPI 전체 메타데이터

```java
// config/OpenApiConfig.java
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI openAPI() {
    return new OpenAPI()
        .info(new Info()
            .title("TMDB 인기영화 API")
            .description("TMDB 에서 동기화한 인기영화 및 장르 관리 REST API")
            .version("1.0.0"));
  }
}
```

---

### 3-3. MovieController 문서화

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

  @Operation(
      summary = "TMDB 인기영화 동기화",
      description = "TMDB /movie/popular 에서 영화 20건을 DB 에 저장합니다. genres/sync 를 먼저 실행해야 합니다."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "저장 완료"),
      @ApiResponse(responseCode = "500", description = "TMDB API 호출 실패")
  })
  @PostMapping("/sync")
  public ResponseEntity<Map<String, Object>> sync(
      @Parameter(description = "TMDB 페이지 번호", example = "1")
      @RequestParam(name = "page", defaultValue = "1") int page) {
    int saved = movieService.syncPopularMovies(page);
    return ResponseEntity.ok(Map.of("page", page, "saved", saved));
  }

  @Operation(summary = "인기영화 조회", description = "genreId, title, sort 파라미터로 필터링/정렬 가능합니다.")
  @GetMapping
  public List<TmdbMovieDto> list(
      @Parameter(description = "장르 ID (예: 28=액션)", example = "28")
      @RequestParam(required = false) Long genreId,
      @Parameter(description = "제목 검색 (부분 일치)", example = "어벤")
      @RequestParam(required = false) String title,
      @Parameter(description = "정렬: popularity | voteAverage")
      @RequestParam(required = false) String sort) {
    if (genreId != null) return movieService.findByGenreId(genreId);
    if (title != null) return movieService.findByTitle(title);
    if (sort != null) return movieService.findAllSorted(sort);
    return movieService.findAll();
  }

  @Operation(summary = "영화 단건 삭제")
  @ApiResponse(responseCode = "204", description = "삭제 완료")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @Parameter(description = "삭제할 영화의 TMDB ID", example = "1226863")
      @PathVariable("id") Long id) {
    movieService.deleteById(id);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "특정 영화의 장르 목록")
  @GetMapping("/{id}/genres")
  public List<TmdbMovieDto.GenreInfo> genres(
      @Parameter(description = "영화 TMDB ID", example = "1226863")
      @PathVariable("id") Long id) {
    return movieService.findGenresByMovieId(id);
  }
}
```

---

## Session 4. OpenAPI 실습 + 전체 정리

### 4-1. GenreController 문서화

```java
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/genres")
@RequiredArgsConstructor
@Tag(name = "Genre", description = "장르 관리 API")
public class GenreController {

  @Operation(summary = "TMDB 장르 동기화", description = "movies/sync 실행 전에 먼저 실행해야 합니다.")
  @PostMapping("/sync")
  public Map<String, Object> sync() {
    return Map.of("synced", genreService.syncGenres());
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

### 4-2. TmdbMovieDto 에 @Schema 추가

```java
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "TMDB 영화 정보")
public class TmdbMovieDto {

  @Schema(description = "TMDB 영화 고유 ID", example = "1226863")
  private Long id;

  @Schema(description = "한국어 제목", example = "어벤져스: 엔드게임")
  private String title;

  @Schema(description = "평균 평점 (0.0 ~ 10.0)", example = "8.3")
  @JsonProperty("vote_average")
  private Double voteAverage;

  @Schema(description = "TMDB 인기도 점수", example = "1523.7")
  private Double popularity;

  @Schema(description = "장르 상세 목록")
  private List<GenreInfo> genres;

  // ...
}
```

---

### 4-3. Swagger UI 실습 순서

`http://localhost:9000/swagger-ui.html` 에서:

1. **Genre → POST /api/genres/sync** → Execute
2. **Movie → POST /api/movies/popular/sync** → page=1 → Execute
3. **Movie → GET /api/movies/popular** → Execute → 영화 목록 확인
4. **Movie → GET /api/movies/popular** → genreId=28 → Execute → 필터링 확인
5. **Genre → GET /api/genres/stats** → Execute → 통계 확인
6. **Movie → GET /api/movies/popular/{id}/genres** → id 입력 → Execute

---

### 4-4. 2일 전체 구현 API 목록

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | /api/genres/sync | 장르 동기화 |
| GET | /api/genres | 장르 전체 조회 |
| GET | /api/genres/stats | 장르별 영화 수 통계 |
| POST | /api/movies/popular/sync | 영화 동기화 |
| GET | /api/movies/popular | 전체 조회 / 필터 / 정렬 |
| GET | /api/movies/popular?genreId={id} | 장르별 필터링 |
| GET | /api/movies/popular?title={t} | 제목 검색 |
| GET | /api/movies/popular?sort={s} | 정렬 |
| GET | /api/movies/popular/{id}/genres | 영화의 장르 조회 |
| DELETE | /api/movies/popular/{id} | 영화 삭제 |

---

## 자주 나오는 질문

| 질문 | 답변 |
|------|------|
| SQL 이 익숙한데 JPQL 을 꼭 써야 하나요? | `@NativeQuery` 로 SQL 도 가능합니다. JPQL 은 DB 독립적이라 권장합니다 |
| `record` 를 JPQL Projection 으로 쓸 수 있나요? | Spring Boot 3.x + Hibernate 6.x 에서 가능합니다. 조회 전용 DTO 에만 사용하세요 |
| 운영 환경에서 Swagger UI 를 끄려면? | `application-prod.yaml` 에 `springdoc.swagger-ui.enabled: false` 추가 |
| Swagger UI 에서 에러가 나면? | CORS 문제일 수 있습니다. `springdoc.swagger-ui.cors-mappings` 확인 |
