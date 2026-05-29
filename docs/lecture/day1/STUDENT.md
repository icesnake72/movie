# 강의 자료 — Day 1: 다대다 관계 & 인덱스 (학생용)

**과정명**: Spring Boot 실무 — 다대다 관계 설계, 인덱스, 검색 API
**선수 지식**: JPA 단일 엔티티, @OneToMany 기본, Spring MVC CRUD

---

## 학습 목표

- N:M 관계가 왜 중간 테이블로 분리되어야 하는지 설명할 수 있다
- `@ManyToMany` 의 한계와 중간 엔티티 패턴의 차이를 비교할 수 있다
- 현재 프로젝트의 `MovieGenre` 엔티티 흐름을 처음부터 끝까지 추적할 수 있다
- 인덱스의 동작 원리와 생성 방법을 설명하고 `EXPLAIN` 으로 검증할 수 있다
- 장르 필터링·제목 검색 API 를 JPQL `@Query` 로 직접 구현할 수 있다

## 최종 산출물

- `movie_genre`, `popular_movie` 테이블에 인덱스 추가
- `GET /api/movies/popular?genreId=28` — 장르별 영화 목록 API
- `GET /api/movies/popular?title=어벤` — 제목 검색 API

---

## Session 1. 다대다(N:M) 테이블 설계 이론

### 1-1. N:M 관계가 DB 에서 불가능한 이유

**❌ 잘못된 시도 1 — 컬럼 하나로 해결하려는 경우**

```sql
ALTER TABLE popular_movie ADD COLUMN genre_id BIGINT;
```

문제: 영화 하나에 장르가 여러 개이면 행이 중복됨.

```
id    title        genre_id
1001  어벤져스      28
1001  어벤져스      878   ← 같은 PK 가 두 번! → 불가능
```

**❌ 잘못된 시도 2 — 문자열로 저장**

```java
private String genreIds;  // "[28, 878, 12]"
```

문제: `WHERE genreId = 28` 쿼리가 불가능. `LIKE '%28%'` 는 128, 280 도 걸린다.

---

### 1-2. 중간 테이블(Junction Table) 패턴

```
popular_movie (N) ──< movie_genre >── genre (N)
     id                movie_id           id
     title             genre_id           name
```

- `movie_genre` 는 FK 두 개를 가진 별도 테이블
- `UNIQUE(movie_id, genre_id)` — 같은 영화에 같은 장르 중복 방지

---

### 1-3. @ManyToMany vs 중간 엔티티

**@ManyToMany (단순하지만 제약 많음):**

```java
// ❌ 중간 테이블에 컬럼 추가 불가
@ManyToMany
@JoinTable(name = "movie_genre",
    joinColumns = @JoinColumn(name = "movie_id"),
    inverseJoinColumns = @JoinColumn(name = "genre_id"))
private List<Genre> genres = new ArrayList<>();
```

**중간 엔티티 (현재 프로젝트 방식):**

```java
// ✅ MovieGenre 엔티티
@Entity
@Table(
    name = "movie_genre",
    uniqueConstraints = @UniqueConstraint(columnNames = {"movie_id", "genre_id"})
)
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

> **포인트** — `@ManyToMany` 를 쓰면 중간 테이블에 컬럼을 추가할 수 없다. 실무에서는 중간 엔티티 패턴을 사용한다.

---

## Session 2. MovieGenre 코드 분석

### 2-1. 데이터 흐름 추적

```
genres/sync 먼저 실행
    → genre 테이블에 id + name 저장

movies/sync 실행
    → genreRepository.findAll() → Map<Long, Genre> 구성
    → TMDB 응답 genre_ids 순회 → MovieGenre 생성
    → movieRepository.saveAll() → popular_movie + movie_genre 동시 저장
```

> **포인트** — genres/sync 를 먼저 해야 한다. 순서를 바꾸면 영화는 저장되지만 장르 연결이 없어진다.

---

### 2-2. MovieService.toEntity() 분석

```java
private Movie toEntity(TmdbMovieDto dto, Map<Long, Genre> genreMap) {
  Movie movie = Movie.builder()
      .id(dto.getId())
      .title(dto.getTitle())
      // ... 기타 필드
      .build();

  if (dto.getGenreIds() != null) {
    dto.getGenreIds().forEach(genreId -> {
      Genre genre = genreMap.get(genreId.longValue());
      if (genre != null) {
        movie.getMovieGenres().add(
            MovieGenre.builder().movie(movie).genre(genre).build());
      }
    });
  }

  return movie;
}
```

> **포인트** — `cascade = ALL` 덕분에 Movie 를 save 하면 MovieGenre 도 자동으로 저장된다.

---

### 2-3. findAllWithGenres() 분석

```java
@Query("SELECT DISTINCT m FROM Movie m "
     + "LEFT JOIN FETCH m.movieGenres mg "
     + "LEFT JOIN FETCH mg.genre")
List<Movie> findAllWithGenres();
```

| 키워드 | 역할 |
|--------|------|
| `LEFT JOIN FETCH` | Movie + MovieGenre + Genre 를 한 쿼리로 함께 로드 |
| `DISTINCT` | JOIN 으로 중복된 Movie 행 제거 |

**N+1 문제란?**

```java
// ❌ 이렇게 하면 영화 수만큼 추가 쿼리 발생
List<Movie> movies = movieRepository.findAll();  // 쿼리 1번
movies.forEach(m ->
    m.getMovieGenres()  // 여기서 영화마다 SELECT 1번씩 추가 → 영화 20개면 쿼리 21번!
);

// ✅ findAllWithGenres() 사용 → 쿼리 1번으로 해결
```

---

### 2-4. 실습: 직접 실행해보기

```bash
# 장르 동기화
curl -s -X POST http://localhost:9000/api/genres/sync

# 영화 동기화
curl -s -X POST "http://localhost:9000/api/movies/popular/sync?page=1"

# genres 필드 확인
curl -s http://localhost:9000/api/movies/popular | python3 -c "
import json, sys
movies = json.load(sys.stdin)
print(f'총 {len(movies)}편')
print(json.dumps(movies[0]['genres'], ensure_ascii=False, indent=2))
"
```

---

## Session 3. 인덱스 설계와 생성

### 3-1. 인덱스란

- 책의 색인(Index)과 같다: 처음부터 읽지 않고 색인에서 페이지 번호를 찾아 바로 이동
- DB 인덱스: B-Tree 구조로 정렬된 포인터. 검색 O(n) → O(log n)
- 트레이드오프: **읽기 빠름 / 쓰기 느림**

**인덱스 장점:**
- `WHERE`, `ORDER BY`, `JOIN` 조건 컬럼의 조회 속도 향상

**인덱스 단점:**
- `INSERT` / `UPDATE` / `DELETE` 시 인덱스도 갱신 → 쓰기 성능 저하
- 인덱스 파일이 추가 디스크 공간 차지
- 잘못된 인덱스는 오히려 옵티마이저를 혼란시킴

---

### 3-2. JPA @Index 로 인덱스 생성

**MovieGenre.java 수정:**

```java
@Entity
@Table(
    name = "movie_genre",
    uniqueConstraints = @UniqueConstraint(columnNames = {"movie_id", "genre_id"}),
    indexes = {
        @Index(name = "idx_movie_genre_movie_id", columnList = "movie_id"),
        @Index(name = "idx_movie_genre_genre_id", columnList = "genre_id")
    }
)
public class MovieGenre {
  // ...
}
```

**Movie.java 수정:**

```java
@Entity
@Table(
    name = "popular_movie",
    indexes = {
        @Index(name = "idx_popular_movie_popularity", columnList = "popularity"),
        @Index(name = "idx_popular_movie_vote_average", columnList = "vote_average"),
        @Index(name = "idx_popular_movie_title", columnList = "title")
    }
)
public class Movie {
  // ...
}
```

앱 재기동 후 확인:

```sql
SHOW INDEX FROM movie_genre;
SHOW INDEX FROM popular_movie;
```

---

### 3-3. EXPLAIN 으로 쿼리 플랜 확인

```sql
-- 인덱스 미사용 (Full Table Scan)
EXPLAIN SELECT * FROM popular_movie WHERE title LIKE '%어벤%';
-- type: ALL, key: NULL

-- 인덱스 사용 (앞 와일드카드 없을 때)
EXPLAIN SELECT * FROM popular_movie WHERE title LIKE '어벤%';
-- type: range, key: idx_popular_movie_title
```

| `type` 값 | 의미 |
|-----------|------|
| `ALL` | Full Table Scan — 인덱스 없음 |
| `range` | 인덱스로 범위 검색 |
| `ref` | 인덱스로 특정 값 검색 |
| `const` | PK 또는 UNIQUE 로 단 1건 |

> **포인트** — `LIKE '%검색어%'` 처럼 앞에 `%` 가 있으면 인덱스를 사용하지 못한다.

---

## Session 4. 검색 API 구현 실습

### 4-1. 장르별 영화 조회 API

**MovieRepository.java 에 추가:**

```java
@Query("SELECT DISTINCT m FROM Movie m "
     + "LEFT JOIN FETCH m.movieGenres mg "
     + "LEFT JOIN FETCH mg.genre "
     + "WHERE mg.genre.id = :genreId")
List<Movie> findAllByGenreId(@Param("genreId") Long genreId);
```

**MovieService.java 에 추가:**

```java
@Transactional(readOnly = true)
public List<TmdbMovieDto> findByGenreId(Long genreId) {
  return movieRepository.findAllByGenreId(genreId).stream()
      .map(this::toDto)
      .toList();
}
```

---

### 4-2. 제목 검색 API

**MovieRepository.java 에 추가:**

```java
@Query("SELECT DISTINCT m FROM Movie m "
     + "LEFT JOIN FETCH m.movieGenres mg "
     + "LEFT JOIN FETCH mg.genre "
     + "WHERE LOWER(m.title) LIKE LOWER(CONCAT('%', :title, '%'))")
List<Movie> findAllByTitleContaining(@Param("title") String title);
```

**MovieService.java 에 추가:**

```java
@Transactional(readOnly = true)
public List<TmdbMovieDto> findByTitle(String title) {
  return movieRepository.findAllByTitleContaining(title).stream()
      .map(this::toDto)
      .toList();
}
```

---

### 4-3. MovieController.java — list() 메서드 수정

```java
@GetMapping
public List<TmdbMovieDto> list(
    @RequestParam(required = false) Long genreId,
    @RequestParam(required = false) String title) {
  if (genreId != null) {
    return movieService.findByGenreId(genreId);
  }
  if (title != null) {
    return movieService.findByTitle(title);
  }
  return movieService.findAll();
}
```

---

### 4-4. 테스트

```bash
# 장르 목록 확인
curl -s http://localhost:9000/api/genres | python3 -c "
import json, sys
for g in json.load(sys.stdin): print(g['id'], g['name'])
"

# 장르 ID 로 필터링 (예: 액션 = 28)
curl -s "http://localhost:9000/api/movies/popular?genreId=28" | \
  python3 -c "import json,sys; d=json.load(sys.stdin); print(f'{len(d)}편'); [print(m['title']) for m in d]"

# 제목 검색
curl -s "http://localhost:9000/api/movies/popular?title=어벤" | \
  python3 -c "import json,sys; d=json.load(sys.stdin); [print(m['title']) for m in d]"
```

---

## 자주 나오는 질문

| 질문 | 답변 |
|------|------|
| `@ManyToMany` 써도 되나요? | 단순 실습용은 가능하지만 실무는 중간 엔티티 패턴을 권장합니다 |
| 인덱스를 모든 컬럼에 달면 안 되나요? | 쓰기 성능이 급격히 저하됩니다. 조회에 자주 쓰이는 컬럼에만 달아야 합니다 |
| `DISTINCT` 없으면 어떻게 되나요? | 장르 수만큼 동일한 Movie 가 중복 반환됩니다 |
| `genreId` 와 `title` 을 동시에 넘기면요? | 현재 구현은 genreId 우선 처리. AND 조건은 별도 쿼리가 필요합니다 |
