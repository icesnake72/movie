# Movie / Genre / MovieGenre 설계 문서

**작성일:** 2026-05-19
**범위:** 기존 코드 현황 분석 및 설계 기준 정리

---

## DB 스키마

### 테이블: popular_movie

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK (TMDB ID 직접 사용) | TMDB 영화 고유 ID — AUTO_INCREMENT 아님 |
| adult | TINYINT(1) | NOT NULL | 성인 콘텐츠 여부 |
| backdrop_path | VARCHAR(500) | NULL 허용 | 배경 이미지 경로 |
| title | VARCHAR(500) | NOT NULL | 한국어(또는 번역) 제목 |
| original_language | VARCHAR(10) | NULL 허용 | ISO 639-1 언어 코드 |
| original_title | VARCHAR(500) | NULL 허용 | 원어 제목 |
| overview | TEXT | NULL 허용 | 시놉시스 (길이 가변) |
| popularity | DOUBLE | NULL 허용 | TMDB 인기도 점수 |
| poster_path | VARCHAR(500) | NULL 허용 | 포스터 이미지 경로 |
| release_date | DATE | NULL 허용 | 개봉일 (미개봉 시 NULL) |
| softcore | TINYINT(1) | NOT NULL | 선정성 콘텐츠 플래그 |
| video | TINYINT(1) | NOT NULL | 비디오 자체 여부 |
| vote_average | DOUBLE | NULL 허용 | 평균 평점 (0.0~10.0) |
| vote_count | INT | NULL 허용 | 평점 참여 투표 수 |

> **설계 결정:** `id`는 TMDB 외부 ID를 그대로 PK로 사용한다. TMDB와 동일한 ID 공간을 유지하므로 sync 시 upsert 처리가 단순해진다. 단, 내부 생성 레코드가 없는 읽기 전용 데이터이므로 AUTO_INCREMENT 불필요.

---

### 테이블: genre

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK (TMDB ID 직접 사용) | TMDB 장르 고유 ID |
| name | VARCHAR(100) | NOT NULL | 장르명 (예: Action, Comedy) |

> **설계 결정:** `popular_movie`와 동일하게 TMDB ID를 PK로 사용. `created_at` / `updated_at`은 현재 미포함 — TMDB 장르 목록은 거의 변경되지 않으므로 생략 가능하나, 변경 이력이 필요하다면 추후 추가 권장.

---

### 테이블: movie_genre (중간 테이블)

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 중간 테이블 자체 PK |
| movie_id | BIGINT | NOT NULL, FK → popular_movie.id | 영화 참조 |
| genre_id | BIGINT | NOT NULL, FK → genre.id | 장르 참조 |

**UNIQUE 제약:** `(movie_id, genre_id)` — 동일 영화에 동일 장르 중복 불가

### 관계

```
popular_movie (1) → movie_genre (N): movie_id FK
genre         (1) → movie_genre (N): genre_id FK
popular_movie (N) ↔ genre (N): movie_genre 중간 테이블로 분리
```

---

## Entity 및 JPA 관계

### Movie (popular_movie 테이블)

```java
@Entity @Table(name = "popular_movie")
public class Movie {
  @Id
  private Long id;  // TMDB ID, @GeneratedValue 없음

  @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true,
             fetch = FetchType.LAZY)
  private List<MovieGenre> movieGenres = new ArrayList<>();
}
```

- `cascade = ALL` + `orphanRemoval = true`: Movie 삭제 시 연관 MovieGenre 자동 삭제
- 연관관계 주인: MovieGenre (FK를 보유)

### Genre (genre 테이블)

```java
@Entity @Table(name = "genre")
public class Genre {
  @Id
  private Long id;  // TMDB ID, @GeneratedValue 없음

  @Column(name = "name", length = 100, nullable = false)
  private String name;
}
```

- 단순 참조 엔티티. Movie 쪽에서 단방향 참조만 사용하므로 Genre에는 `@OneToMany` 없음
- Genre를 독립 도메인으로 취급 → Movie 쪽 cascade에 Genre 포함 금지

### MovieGenre (movie_genre 중간 테이블)

```java
@Entity
@Table(name = "movie_genre",
       uniqueConstraints = @UniqueConstraint(columnNames = {"movie_id", "genre_id"}))
public class MovieGenre {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "movie_id", nullable = false)
  private Movie movie;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "genre_id", nullable = false)
  private Genre genre;
}
```

### 관계 설계 결정사항

| 관계 | 전략 | 이유 |
|------|------|------|
| Movie → MovieGenre | @OneToMany LAZY + cascade ALL | Movie 조회 시 장르가 항상 필요하지 않음. 삭제 시 고아 레코드 방지 |
| MovieGenre → Movie | @ManyToOne LAZY | fetch join으로 필요 시 함께 로딩 |
| MovieGenre → Genre | @ManyToOne LAZY | fetch join으로 필요 시 함께 로딩 |
| Movie ↔ Genre | N:M → 중간 테이블 분리 | `@ManyToMany` 대신 중간 Entity 사용 — 향후 순서(order), 주요 장르(primary) 같은 속성 추가 가능 |

### N+1 방지 전략

```java
// MovieRepository
@Query("SELECT DISTINCT m FROM Movie m "
     + "LEFT JOIN FETCH m.movieGenres mg "
     + "LEFT JOIN FETCH mg.genre")
List<Movie> findAllWithGenres();
```

- `findAll()` 대신 `findAllWithGenres()`를 사용하여 MovieGenre, Genre를 한 쿼리로 로딩
- `DISTINCT` 필수 — JOIN FETCH 시 Movie 레코드가 장르 수만큼 중복 반환되는 것을 방지

---

## REST API 명세

### 기본 규칙

- Base URL: `/api`
- 응답 형식: JSON
- 인증: 없음 (현재 구조 기준)

### 엔드포인트

#### Movie (인기 영화)

| 메서드 | 경로 | 설명 | 요청 파라미터 | 응답 |
|--------|------|------|---------------|------|
| POST | /api/movies/popular/sync | TMDB에서 인기 영화 동기화 | `page` (int, 기본값 1) | `{ page, saved }` |
| GET | /api/movies/popular | 저장된 인기 영화 전체 조회 | - | `List<TmdbMovieDto>` |
| DELETE | /api/movies/popular/{id} | 영화 단건 삭제 | - | 204 No Content |

#### Genre

| 메서드 | 경로 | 설명 | 응답 |
|--------|------|------|------|
| POST | /api/genres/sync | TMDB에서 장르 목록 동기화 | `{ synced }` |
| GET | /api/genres | 저장된 장르 전체 조회 | `List<TmdbGenreDto>` |

#### TmdbMovieDto (응답 DTO)

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | TMDB 영화 ID |
| adult | boolean | 성인 콘텐츠 여부 |
| backdropPath | String | 배경 이미지 경로 |
| genreIds | List\<Integer\> | 장르 ID 목록 |
| title | String | 제목 |
| originalLanguage | String | 원작 언어 코드 |
| originalTitle | String | 원어 제목 |
| overview | String | 시놉시스 |
| popularity | Double | 인기도 점수 |
| posterPath | String | 포스터 이미지 경로 |
| releaseDate | String | 개봉일 (ISO 8601) |
| softcore | boolean | 선정성 플래그 |
| video | boolean | 비디오 여부 |
| voteAverage | Double | 평균 평점 |
| voteCount | Integer | 투표 수 |
| genres | List\<GenreInfo\> | id + name 장르 상세 |

#### TmdbGenreDto (응답 DTO)

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | 장르 ID |
| name | String | 장르명 |

---

## 예외 처리 전략

> **현재 상태:** `@RestControllerAdvice` 및 커스텀 예외 클래스가 아직 구현되어 있지 않음. 아래는 도입 권장 설계이다.

### 커스텀 예외 계층

```
BusinessException (RuntimeException 상속)
├── MovieNotFoundException    → 404
├── GenreNotFoundException    → 404
└── TmdbSyncException         → 502 (외부 API 연동 실패)
```

### ErrorResponse 형식

```json
{
  "code": "MOVIE_NOT_FOUND",
  "message": "영화를 찾을 수 없습니다.",
  "timestamp": "2026-05-19T10:00:00"
}
```

### @RestControllerAdvice 처리 범위

| 예외 | HTTP 상태 | 처리 방법 |
|------|-----------|-----------|
| MovieNotFoundException | 404 | code + message 반환 |
| GenreNotFoundException | 404 | code + message 반환 |
| TmdbSyncException | 502 | 외부 연동 실패 안내 (상세 숨김) |
| MethodArgumentNotValidException | 400 | 필드별 검증 오류 반환 |
| DataIntegrityViolationException | 409 | 중복 데이터 안내 |
| Exception | 500 | 내부 오류 (스택트레이스 노출 금지) |

**현재 미비 사항:**
- `deleteById` 시 존재하지 않는 ID에 대한 예외 처리 없음 (JPA는 조용히 무시함)
- TMDB 응답이 null일 때 로그만 남기고 0 반환 — 필요 시 `TmdbSyncException`으로 전환 검토

---

## 설계 결정사항 및 근거

| 항목 | 결정 | 근거 |
|------|------|------|
| Movie.id PK | TMDB ID 직접 사용 | sync 시 upsert 단순화 (`saveAll`이 merge로 동작) |
| Genre.id PK | TMDB ID 직접 사용 | 위와 동일, TMDB 코드와 1:1 대응 |
| N:M 관계 | `@ManyToMany` 대신 중간 Entity(MovieGenre) 사용 | 향후 순서, primary 장르 등 속성 확장 가능. cascade 제어 명확 |
| cascade 범위 | Movie → MovieGenre에만 ALL + orphanRemoval | Genre는 독립 도메인 — Movie 삭제 시 Genre 삭제 불가 |
| Fetch 전략 | 전체 LAZY, 목록 조회 시 fetch join | N+1 방지, 불필요한 쿼리 제거 |
| DTO/Entity 분리 | TmdbMovieDto, TmdbGenreDto 별도 운용 | Entity 직접 노출 금지 원칙 준수 |
| created_at/updated_at 미포함 | TMDB 원본 데이터 기준 유지 | 변경 이력이 필요하다면 `@EntityListeners(AuditingEntityListener.class)` 도입 권장 |
