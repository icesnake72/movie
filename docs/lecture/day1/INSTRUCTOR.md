# 강의 자료 — Day 1: 다대다 관계 & 인덱스 (강사용)

**과정명**: Spring Boot 실무 — 다대다 관계 설계, 인덱스, 검색 API
**대상**: Spring Boot 입문 완료 (기본 CRUD, JPA 단일 엔티티 경험자)
**총 소요시간**: 4시간 (60분 × 4세션)
**선수 지식**: JPA 단일 엔티티, @OneToMany 기본, Spring MVC CRUD

---

## 학습 목표

- N:M 관계가 왜 중간 테이블로 분리되어야 하는지 설명할 수 있다 (이해)
- `@ManyToMany` 의 한계와 중간 엔티티 패턴의 차이를 비교할 수 있다 (분석)
- 현재 프로젝트의 `MovieGenre` 엔티티 흐름을 처음부터 끝까지 추적할 수 있다 (적용)
- 인덱스의 동작 원리와 생성 방법을 설명하고 `EXPLAIN` 으로 검증할 수 있다 (적용)
- 장르 필터링·제목 검색 API 를 JPQL `@Query` 로 직접 구현할 수 있다 (적용)

## 최종 산출물

- `movie_genre` 테이블에 인덱스 추가 (JPA `@Index` 어노테이션)
- `GET /api/movies/popular?genreId=28` — 장르별 영화 목록 API
- `GET /api/movies/popular?title=어벤` — 제목 검색 API

---

## Session 1. 다대다(N:M) 테이블 설계 이론 (60분)

### 1-1. 1:N 관계 복습 (10분)

학생들이 이미 아는 내용을 빠르게 상기시키고, N:M 으로 자연스럽게 이어진다.

```
users (1) ──────< orders (N)
  id                user_id (FK)
  name              amount
```

> **강의 포인트**
> - WHY: 1:N 은 FK 를 N 쪽에만 두면 해결된다. 추가 테이블이 필요 없다.
> - 질문을 던져라: "한 영화가 여러 장르에 속하고, 한 장르에 여러 영화가 있다면?"
>   → 학생들이 "둘 다 N 이네?" 라고 깨닫게 유도.

---

### 1-2. N:M 관계가 DB 에서 불가능한 이유 (15분)

**❌ 잘못된 시도 1 — popular_movie 에 genre_id 컬럼 추가**

```sql
-- popular_movie 테이블에 genre_id 컬럼을 하나 추가한다면?
ALTER TABLE popular_movie ADD COLUMN genre_id BIGINT;
```

문제: 영화 하나에 장르가 여러 개이면 행이 중복됨.

```
id    title        genre_id
1001  어벤져스      28       ← 액션
1001  어벤져스      878      ← SF  ← 같은 영화가 두 번!
```

→ PK(id) 중복 불가. 구조적으로 불가능.

**❌ 잘못된 시도 2 — genre_id 를 문자열로 저장 (지난 시간 방식)**

```java
@Column(length = 500)
private String genreIds;  // "[28, 878, 12]" 형태
```

문제: "장르 ID 28인 영화만 조회" 가 불가능. SQL `LIKE '%28%'` 은 `128`, `280` 도 걸린다.

> **강의 포인트**
> - PITFALL: 문자열 저장은 쓰기는 쉽지만 읽기(검색·조인)가 사실상 불가능해진다.
> - "그래서 지난 시간에 우리가 이 방식을 버리고 중간 테이블로 전환했습니다."
>   → 이전 커밋 diff 를 잠깐 보여주면 효과적.

---

### 1-3. 중간 테이블(Junction Table) 패턴 (20분)

```
popular_movie (N) ──< movie_genre >── genre (N)
     id                movie_id           id
     title             genre_id           name
                       ↑ FK           ↑ FK
```

**ERD 설명 포인트:**
1. `movie_genre` 는 외래키 두 개(FK) 를 가진 별도 테이블
2. `popular_movie` ↔ `movie_genre` 는 1:N
3. `genre` ↔ `movie_genre` 는 1:N
4. 결과적으로 `popular_movie` ↔ `genre` 는 `movie_genre` 를 통한 N:M

**UNIQUE 제약의 역할:**

```sql
UNIQUE KEY (movie_id, genre_id)
```

→ 같은 영화에 같은 장르가 두 번 들어가는 것을 DB 레벨에서 차단.

---

### 1-4. @ManyToMany vs 중간 엔티티 비교 (15분)

**방식 A — @ManyToMany (JPA 제공)**

```java
// ❌ 나쁜 예 — 간단해 보이지만 함정이 많다
@Entity
public class Movie {

  @ManyToMany
  @JoinTable(
      name = "movie_genre",
      joinColumns = @JoinColumn(name = "movie_id"),
      inverseJoinColumns = @JoinColumn(name = "genre_id")
  )
  private List<Genre> genres = new ArrayList<>();
}
```

문제점:
- 중간 테이블(`movie_genre`)에 컬럼 추가 불가 (예: 장르 등록일, 주요 장르 여부)
- cascade 제어가 어려움 (Movie 삭제 시 Genre 도 삭제될 위험)
- 중간 테이블 레코드를 직접 다루기 어려움

**방식 B — 중간 엔티티 (현재 프로젝트 방식)**

```java
// ✅ 좋은 예 — 중간 엔티티로 직접 분리
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

  // 향후 필드 추가 가능
  // private int sortOrder;
  // private boolean isPrimary;
}
```

> **강의 포인트**
> - WHY: `@ManyToMany` 는 단순하지만 확장이 막힌다. 중간 엔티티는 코드가 조금 더 많지만 실무에서 훨씬 유연하다.
> - 비유: "조인트(joint)가 보이는 가구 vs 본드로 붙인 가구. 나중에 고치려면 조인트가 보이는 게 낫다."
> - WHAT: `@ManyToMany` 도 내부적으로 중간 테이블을 만든다. 다만 우리가 그 테이블의 엔티티를 갖지 못할 뿐.

---

## Session 2. MovieGenre 코드 분석 실습 (60분)

### 2-1. 엔티티 관계 연결 추적 (20분)

**전체 흐름 다이어그램:**

```
TMDB API 응답
  └─ genre_ids: [28, 878, 12]   ← 숫자만 내려온다
         │
         ▼
  syncGenres() 먼저 실행 → genre 테이블에 id, name 저장
         │
         ▼
  syncPopularMovies() 실행
     genreRepository.findAll() → Map<Long, Genre> genreMap 구성
         │
         ▼
  toEntity(dto, genreMap) 호출
     genreIds.forEach → genreMap.get(genreId) → MovieGenre.builder() 생성
         │
         ▼
  movieRepository.saveAll(movies) → popular_movie + movie_genre 동시 저장
```

> **강의 포인트**
> - WHY: genres/sync 를 먼저 해야 movies/sync 가 가능한 이유. genreMap 이 비어있으면 MovieGenre 가 생성되지 않는다.
> - PITFALL: 순서를 바꾸면 영화는 저장되지만 장르 연결이 없어진다. 학생들에게 실제로 순서를 바꿔서 결과를 보여주는 것 추천.

**코드 단계별 추적 (MovieService.java):**

```java
// Step 1: 모든 장르를 Map 으로 로드 (N+1 방지)
Map<Long, Genre> genreMap = genreRepository.findAll().stream()
    .collect(Collectors.toMap(Genre::getId, g -> g));
// 결과 예: {28=Genre(id=28, name=액션), 878=Genre(id=878, name=SF), ...}

// Step 2: TMDB 응답의 각 영화를 엔티티로 변환
List<Movie> movies = response.getResults().stream()
    .map(dto -> toEntity(dto, genreMap))
    .toList();

// Step 3: toEntity 내부에서 MovieGenre 생성
private Movie toEntity(TmdbMovieDto dto, Map<Long, Genre> genreMap) {
  Movie movie = Movie.builder()
      .id(dto.getId())
      .title(dto.getTitle())
      // ... 기타 필드
      .build();

  if (dto.getGenreIds() != null) {
    dto.getGenreIds().forEach(genreId -> {
      Genre genre = genreMap.get(genreId.longValue());
      if (genre != null) {  // ← genre 가 없으면 조용히 스킵
        movie.getMovieGenres().add(
            MovieGenre.builder().movie(movie).genre(genre).build());
      }
    });
  }

  return movie;
}
```

> **강의 포인트**
> - WHAT: `genreMap.get(genreId)` 가 null 을 반환하는 경우는 genres/sync 를 안 했거나, TMDB 에 없는 장르 ID 인 경우. `if (genre != null)` 가드가 중요.
> - cascade = ALL 덕분에 `movieRepository.saveAll(movies)` 한 번으로 `popular_movie` 와 `movie_genre` 가 동시에 저장된다.

---

### 2-2. findAllWithGenres() JPQL 분석 (20분)

**현재 쿼리:**

```java
@Query("SELECT DISTINCT m FROM Movie m "
     + "LEFT JOIN FETCH m.movieGenres mg "
     + "LEFT JOIN FETCH mg.genre")
List<Movie> findAllWithGenres();
```

**키워드별 역할:**

| 키워드 | 역할 | 없으면? |
|--------|------|---------|
| `LEFT JOIN FETCH` | 한 쿼리에 연관 엔티티 함께 로드 | N+1 쿼리 발생 |
| `DISTINCT` | JOIN 으로 중복된 Movie 행 제거 | 장르 수만큼 Movie 중복 반환 |
| `m.movieGenres mg` | MovieGenre 컬렉션 join | 경로 표현식으로 묵시적 join 가능하나 명시적 권장 |

**N+1 문제 시연 (수업 필수):**

```java
// ❌ 이렇게 하면 N+1 발생
List<Movie> movies = movieRepository.findAll();
movies.forEach(m -> {
  // 이 시점에 SELECT * FROM movie_genre WHERE movie_id=? 가 영화 수만큼 실행됨
  m.getMovieGenres().forEach(mg -> System.out.println(mg.getGenre().getName()));
});
```

`show-sql: true` 로그에서 쿼리가 폭발적으로 늘어나는 것을 직접 보여준다.

```java
// ✅ findAllWithGenres() 사용 시 — 쿼리 1번으로 해결
List<Movie> movies = movieRepository.findAllWithGenres();
// 로그에 SELECT 쿼리 1개만 출력됨
```

> **강의 포인트**
> - WHY: Hibernate 는 `LAZY` 컬렉션에 접근할 때 추가 쿼리를 날린다. 영화 20개면 쿼리 21개(1+20). 200개면 201개.
> - PITFALL: `@OneToMany` 에 `EAGER` 를 붙이면 N+1 은 없어지지만 항상 조인이 발생해 불필요한 쿼리를 유발한다.

---

### 2-3. 실습: genres 동기화 후 movies 동기화 직접 실행 (20분)

```bash
# 1. 앱 실행
TMDB_BEARER_TOKEN="<token>" ./gradlew bootRun &

# 2. 장르 먼저 동기화
curl -s -X POST http://localhost:9000/api/genres/sync
# → {"synced": 19}

# 3. 영화 동기화
curl -s -X POST "http://localhost:9000/api/movies/popular/sync?page=1"
# → {"page": 1, "saved": 20}

# 4. 조회 — genres 필드 확인
curl -s http://localhost:9000/api/movies/popular | python3 -c "
import json, sys
movies = json.load(sys.stdin)
print(f'총 {len(movies)}편')
print('첫 번째 영화:')
print(json.dumps(movies[0], ensure_ascii=False, indent=2))
"
```

> **강의 포인트**
> - 응답의 `genres` 배열이 `[{"id": 28, "name": "액션"}, ...]` 형태로 내려오는 것 확인.
> - DB 에서도 직접 확인: `SELECT * FROM movie_genre LIMIT 10;`

---

## Session 3. 인덱스 설계와 생성 (60분)

### 3-1. 인덱스란 무엇인가 (15분)

**비유: 책의 색인(Index)**

```
책 전체 검색: 처음부터 끝까지 읽기 → O(n)
색인 사용:    색인에서 페이지 번호 찾기 → O(log n)
```

DB 도 동일하다. 인덱스 없이 `WHERE title = '어벤져스'` 는 전체 행을 하나씩 비교(Full Table Scan).

**B-Tree 구조 (간략하게):**

```
          [50]
        /      \
    [25]          [75]
   /    \        /    \
 [10]  [30]   [60]  [90]
```

- 이진 탐색 트리. 값이 정렬된 상태로 유지됨.
- 검색: O(log n). 1,000만 건도 약 24번만 비교하면 찾는다.
- 대신 INSERT/UPDATE/DELETE 시 트리를 재구성해야 함 → 쓰기 성능 저하.

> **강의 포인트**
> - WHY: 인덱스는 **읽기 속도를 올리는 대신 쓰기 속도를 희생**하는 트레이드오프.
> - 현실: 대부분의 서비스는 읽기 비율이 80~95%. 인덱스가 대부분의 경우에 이득.

---

### 3-2. 어떤 컬럼에 인덱스를 만들어야 하나 (10분)

**인덱스가 효과적인 경우:**

| 조건 | 이유 |
|------|------|
| `WHERE` 절에 자주 쓰이는 컬럼 | 조회 시 조건 컬럼을 빠르게 찾기 위해 |
| `ORDER BY` 에 쓰이는 컬럼 | 이미 정렬된 인덱스 활용 가능 |
| `JOIN` 의 FK 컬럼 | 조인 조건으로 빠른 매칭 |
| 카디널리티가 높은 컬럼 | `title` (다양) > `adult` (true/false만) |

**인덱스가 비효율적인 경우:**

| 조건 | 이유 |
|------|------|
| 카디널리티가 낮은 컬럼 | `adult(boolean)` — 절반씩 나눠봤자 의미 없음 |
| 자주 변경되는 컬럼 | 인덱스 재구성 비용이 크다 |
| 소규모 테이블 | 행이 수백 개라면 Full Scan 이 더 빠를 수 있다 |

---

### 3-3. JPA @Index 어노테이션으로 생성 (20분)

**movie_genre 테이블에 인덱스 추가:**

```java
// MovieGenre.java
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

**popular_movie 테이블에 인덱스 추가:**

```java
// Movie.java
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

앱 재기동 후 (`ddl-auto: create`) 로그 확인:

```
Hibernate: create index idx_movie_genre_movie_id on movie_genre (movie_id)
Hibernate: create index idx_movie_genre_genre_id on movie_genre (movie_id)
Hibernate: create index idx_popular_movie_popularity on popular_movie (popularity)
```

**MySQL 에서 직접 확인:**

```sql
SHOW INDEX FROM movie_genre;
SHOW INDEX FROM popular_movie;
```

---

### 3-4. EXPLAIN 으로 쿼리 플랜 확인 (15분)

**인덱스 없을 때 vs 있을 때 비교:**

```sql
-- 인덱스 없을 때 (genres/sync 후 movies/sync 실행 전)
EXPLAIN SELECT * FROM popular_movie WHERE title LIKE '%어벤%';
-- type: ALL (Full Table Scan)

-- 인덱스 있을 때 (인덱스 추가 후)
EXPLAIN SELECT * FROM popular_movie WHERE title = '어벤져스: 엔드게임';
-- type: ref (인덱스 사용)
```

**EXPLAIN 컬럼 설명:**

| 컬럼 | 설명 | 좋은 값 |
|------|------|---------|
| `type` | 접근 방식 | const > ref > range > ALL |
| `key` | 사용된 인덱스명 | NULL 이면 인덱스 미사용 |
| `rows` | 검색할 예상 행 수 | 낮을수록 좋음 |
| `Extra` | 추가 정보 | Using index = 커버링 인덱스 |

> **강의 포인트**
> - PITFALL: `LIKE '%어벤%'` — 앞에 `%` 가 있으면 인덱스를 타지 않는다. `LIKE '어벤%'` 은 인덱스 사용 가능.
> - WHAT: `title` 에 인덱스가 있어도 `%어벤%` 처럼 앞이 와일드카드면 B-Tree 시작점을 특정할 수 없어 Full Scan.

---

## Session 4. 검색 API 구현 실습 (60분)

### 4-1. 장르별 영화 조회 API (25분)

**목표:** `GET /api/movies/popular?genreId=28` → 액션 영화만 반환

**Step 1: MovieRepository 에 쿼리 추가**

```java
// MovieRepository.java
@Query("SELECT DISTINCT m FROM Movie m "
     + "LEFT JOIN FETCH m.movieGenres mg "
     + "LEFT JOIN FETCH mg.genre "
     + "WHERE mg.genre.id = :genreId")
List<Movie> findAllByGenreId(@Param("genreId") Long genreId);
```

> **강의 포인트**
> - JPQL 경로: `mg.genre.id` — MovieGenre 엔티티의 genre 필드의 id. SQL 이라면 `mg.genre_id` 지만 JPQL 은 엔티티 그래프를 따라간다.
> - `@Param("genreId")` 를 빠뜨리면 `QueryException: Named parameter not bound: genreId` 에러.

**Step 2: MovieService 에 메서드 추가**

```java
// MovieService.java
@Transactional(readOnly = true)
public List<TmdbMovieDto> findByGenreId(Long genreId) {
  return movieRepository.findAllByGenreId(genreId).stream()
      .map(this::toDto)
      .toList();
}
```

**Step 3: MovieController 에 엔드포인트 추가**

```java
// MovieController.java — list() 메서드 수정
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

**테스트:**

```bash
# 장르 목록 확인
curl -s http://localhost:9000/api/genres | python3 -c "
import json, sys
for g in json.load(sys.stdin): print(g['id'], g['name'])
"

# 액션(28) 영화만 조회
curl -s "http://localhost:9000/api/movies/popular?genreId=28" | \
  python3 -c "import json,sys; d=json.load(sys.stdin); print(f'{len(d)}편'); [print(m['title']) for m in d]"
```

---

### 4-2. 제목 검색 API (20분)

**Step 1: MovieRepository 에 쿼리 추가**

```java
// MovieRepository.java
@Query("SELECT DISTINCT m FROM Movie m "
     + "LEFT JOIN FETCH m.movieGenres mg "
     + "LEFT JOIN FETCH mg.genre "
     + "WHERE LOWER(m.title) LIKE LOWER(CONCAT('%', :title, '%'))")
List<Movie> findAllByTitleContaining(@Param("title") String title);
```

**Step 2: MovieService 에 메서드 추가**

```java
// MovieService.java
@Transactional(readOnly = true)
public List<TmdbMovieDto> findByTitle(String title) {
  return movieRepository.findAllByTitleContaining(title).stream()
      .map(this::toDto)
      .toList();
}
```

**테스트:**

```bash
curl -s "http://localhost:9000/api/movies/popular?title=어벤" | \
  python3 -c "import json,sys; d=json.load(sys.stdin); print(f'{len(d)}편'); [print(m['title']) for m in d]"
```

> **강의 포인트**
> - PITFALL: `LIKE '%어벤%'` 는 인덱스를 사용하지 않는다 (앞 와일드카드). 소규모 데이터에서는 문제없지만 수백만 건이면 Full Scan.
> - 실무 대안: ElasticSearch, MySQL FULLTEXT INDEX, Hibernate Search 등. 지금 단계에서는 "이런 한계가 있다" 는 것을 인지하는 것이 중요.

---

### 4-3. 최종 확인 — 전체 MovieRepository (10분)

```java
public interface MovieRepository extends JpaRepository<Movie, Long> {

  // 전체 조회 (장르 포함)
  @Query("SELECT DISTINCT m FROM Movie m "
       + "LEFT JOIN FETCH m.movieGenres mg "
       + "LEFT JOIN FETCH mg.genre")
  List<Movie> findAllWithGenres();

  // 장르 ID 로 필터링
  @Query("SELECT DISTINCT m FROM Movie m "
       + "LEFT JOIN FETCH m.movieGenres mg "
       + "LEFT JOIN FETCH mg.genre "
       + "WHERE mg.genre.id = :genreId")
  List<Movie> findAllByGenreId(@Param("genreId") Long genreId);

  // 제목 검색
  @Query("SELECT DISTINCT m FROM Movie m "
       + "LEFT JOIN FETCH m.movieGenres mg "
       + "LEFT JOIN FETCH mg.genre "
       + "WHERE LOWER(m.title) LIKE LOWER(CONCAT('%', :title, '%'))")
  List<Movie> findAllByTitleContaining(@Param("title") String title);
}
```

---

## 트러블슈팅 가이드 — Day 1

| 증상 | 원인 | 해결 방법 |
|------|------|-----------|
| movies/sync 후 genres 필드 빈 배열 | genres/sync 를 먼저 안 했음 | `POST /api/genres/sync` 먼저 실행 |
| `HibernateException: genres 비어있음` | genreMap 이 비어있음 | genre 테이블 데이터 확인 |
| `NamedParameterNotBoundException` | `@Param` 누락 | `@Param("genreId")` 추가 |
| `MultipleBagFetchException` | `@OneToMany` 를 두 개 동시에 FETCH | 하나만 FETCH, 나머지는 `@BatchSize` 사용 |
| 인덱스 생성 안 됨 | `ddl-auto: update` 인데 이미 테이블 있음 | DB drop 후 재기동 또는 `ddl-auto: create` |
| `LIKE` 검색이 인덱스를 안 탐 | 앞에 `%` 와일드카드 | 앞 `%` 제거 또는 FULLTEXT 인덱스 검토 |

## 자주 나오는 질문

| 질문 | 답변 요약 | 심화 설명 |
|------|-----------|-----------|
| `@ManyToMany` 써도 되나요? | 단순 학습용은 가능하지만 실무는 중간 엔티티 권장 | 중간 테이블 컬럼 추가, cascade 제어, 직접 레코드 조작 등 불가 |
| 인덱스를 모든 컬럼에 달면 안 되나요? | 쓰기 성능이 급격히 저하됩니다 | INSERT 마다 모든 인덱스 B-Tree 재구성. DBA 가 가장 싫어하는 패턴. |
| `DISTINCT` 없으면 어떻게 되나요? | 장르 수만큼 Movie 객체가 중복으로 반환됩니다 | 영화가 3개 장르라면 같은 Movie 가 3번 반환. `size()` 가 뻥튀기됨. |
| `genreId` 와 `title` 을 동시에 받으면요? | 현재 구현은 genreId 우선, title 은 무시 | 두 조건을 AND 로 처리하려면 별도 쿼리 필요. 다음 시간에 다룸. |
