# 강의 자료 — Day 3: 출연자(Actor) N:M 심화 — 관계 속성을 가진 중간 엔티티 & JPQL 집계 응용 (학생용)

**과정명**: Spring Boot 실무 — N:M 심화, 관계 속성 중간 엔티티, JPQL 집계·self-join, 슬라이스 테스트
**선수 지식**: Day 1·2 완료 (MovieGenre 중간 엔티티, JPQL `GROUP BY`, `JOIN FETCH`)

---

## 학습 목표

- 중간 엔티티가 **관계 자체의 속성**(배역명, 출연순서)을 가져야 하는 경우를 식별하고 설계할 수 있다
- TMDB `/movie/{id}/credits` 응답을 Actor + MovieActor 로 분리 저장하고, 재동기화 시 unique 제약을 위반하지 않는 동기화 로직을 작성할 수 있다
- JPQL self-join 과 `GROUP BY ... HAVING COUNT(DISTINCT)` 패턴으로 "동료 배우", "공동 출연작" 같은 집합 연산을 표현할 수 있다
- `Pageable` + `Object[]` 프로젝션으로 랭킹 쿼리를 구현하고, 결과를 DTO 로 변환할 수 있다
- `@DataJpaTest` + H2(MODE=MySQL) 슬라이스 테스트로 N:M 쿼리를 외부 의존 없이 검증할 수 있다

## 최종 산출물

- `Actor`, `MovieActor` 엔티티
- `POST /api/movies/popular/{id}/credits/sync` — TMDB credits 동기화
- `GET /api/movies/popular/{id}/actors` — 영화별 출연진 (castOrder 순)
- `GET /api/movies/popular/{id}/actors/count` — 영화별 출연진 수
- `GET /api/movies/popular/shared?actorId=...&actorId=...` — 공동 출연작 (교집합)
- `GET /api/actors`, `GET /api/actors?name=...` — 배우 조회/검색
- `GET /api/actors/{id}` — 배우 단건
- `GET /api/actors/{id}/movies` — 필모그래피 (인기순)
- `GET /api/actors/{id}/co-actors` — 동료 배우 (self-join)
- `GET /api/actors/top?limit=10` — 최다 출연 배우 랭킹
- `MovieActorRepositoryTest` — `@DataJpaTest` 기반 H2 슬라이스 테스트

---

## Session 1. 관계 속성을 가진 중간 엔티티 설계

### 1-1. Day 1 복습 — MovieGenre 는 연결만 한다

```
movie_genre
  id, movie_id, genre_id    ← 연결 외에는 정보 없음
```

→ 이 정도라면 `@ManyToMany` 로도 표현 가능 (다만 확장성 때문에 중간 엔티티가 좋다).

### 1-2. 영화-배우 관계는 "어떻게 출연하는지"가 필요

```
영화 (1)  ─< movie_actor >─  (N) 배우
                ↑ character_name (배역명), cast_order (출연순서)
```

배역명과 출연순서는 **영화에도 배우에도 속하지 않는다.**
"이 영화에 이 배우가 출연한다는 사실 자체" 에 속하는 정보다.

> **포인트** — 관계 자체에 속성이 필요한 순간, `@ManyToMany` 는 표현 불가능. 중간 엔티티만이 답.

### 1-3. @ManyToMany 가 불가능한 이유

```java
// ❌ characterName, castOrder 를 어디에 둘 것인가?
@Entity
public class Movie {
  @ManyToMany
  @JoinTable(name = "movie_actor",
      joinColumns = @JoinColumn(name = "movie_id"),
      inverseJoinColumns = @JoinColumn(name = "actor_id"))
  private List<Actor> actors = new ArrayList<>();
}
```

`@JoinTable` 은 두 FK 외 어떤 컬럼도 추가할 수 없다.

### 1-4. MovieActor 엔티티 (실제 코드)

```java
// movie/MovieActor.java
@Entity
@Table(
    name = "movie_actor",
    uniqueConstraints = @UniqueConstraint(columnNames = {"movie_id", "actor_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class MovieActor {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "movie_id", nullable = false)
  private Movie movie;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "actor_id", nullable = false)
  private Actor actor;

  @Column(name = "character_name", length = 300)
  private String characterName;

  @Column(name = "cast_order")
  private Integer castOrder;  // TMDB order. 0 에 가까울수록 주연
}
```

| 요소 | 의미 |
|------|------|
| `UNIQUE(movie_id, actor_id)` | "같은 영화 + 같은 배우" 중복 차단 |
| `@ManyToOne(LAZY)` | FK 양쪽 모두 LAZY (N+1 방지의 출발점) |
| `characterName`, `castOrder` | 관계 속성 — 영화·배우 어디에도 못 둠 |

### 1-5. Actor 엔티티 — 독립 도메인 + TMDB id 를 PK 로

```java
// actor/Actor.java
@Entity
@Table(name = "actor")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Actor {

  @Id
  @Column(name = "id", nullable = false)
  private Long id;  // TMDB person id 를 그대로 PK 로 사용

  @Column(name = "name", length = 200, nullable = false)
  private String name;

  @JsonProperty("profile_path")
  @Column(name = "profile_path", length = 500)
  private String profilePath;

  @Column(name = "gender")
  private Integer gender;  // 0=미상, 1=여성, 2=남성, 3=논바이너리

  @Column(name = "popularity")
  private Double popularity;
}
```

**Movie 의 컬렉션:**

```java
// movie/Movie.java 발췌
@Builder.Default
@OneToMany(mappedBy = "movie",
  cascade = CascadeType.ALL,   // Movie 삭제 시 출연 매핑도 함께 삭제
  orphanRemoval = true,
  fetch = FetchType.LAZY)
private List<MovieActor> movieActors = new ArrayList<>();
// Actor 자체는 cascade 대상 아님 — 독립 도메인
```

> **포인트** — `cascade` 가 흐르는 경계 = **애그리거트 경계**.
> - Movie → MovieActor: cascade O (영화의 일부)
> - MovieActor → Actor: cascade X (배우는 독립적으로 살아남는다)

---

## Session 2. TMDB credits 동기화 — unique 제약을 깨지 않는 3단계

### 2-1. TMDB `/movie/{id}/credits` 응답 구조

```json
{
  "id": 1226863,
  "cast": [
    {"id": 1234, "name": "...", "character": "...", "order": 0, "popularity": 12.3, "gender": 2, "profile_path": "..."}
  ],
  "crew": [
    {"id": 9999, "job": "Director", ...}
  ]
}
```

우리는 `cast` 만 받는다. `crew` 는 DTO 에 매핑하지 않아 자동으로 무시된다.

```java
// actor/dto/TmdbCreditsResponse.java
@Getter @Setter @NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TmdbCreditsResponse {
  private Long id;
  private List<TmdbCastDto> cast;
  // crew 필드를 매핑하지 않음 → 무시됨
}
```

```java
// actor/dto/TmdbCastDto.java
@Getter @Setter @NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TmdbCastDto {
  private Long id;
  private String name;
  @JsonProperty("profile_path") private String profilePath;
  private Integer gender;
  private Double popularity;
  private String character;
  private Integer order;
}
```

### 2-2. ActorService.syncCredits() — 3단계 패턴

```java
@Transactional
public int syncCredits(Long movieId) {
  Movie movie = movieRepository.findById(movieId).orElse(null);
  if (movie == null) {
    log.warn("credits 동기화 대상 영화가 존재하지 않음. movieId={}", movieId);
    return 0;
  }

  TmdbCreditsResponse response = tmdbRestClient.get()
      .uri(uriBuilder -> uriBuilder.path("/movie/{id}/credits")
          .queryParam("language", defaultLanguage)
          .build(movieId))
      .retrieve()
      .body(TmdbCreditsResponse.class);

  if (response == null || response.getCast() == null || response.getCast().isEmpty()) {
    log.warn("TMDB credits 응답이 비어있음. movieId={}", movieId);
    return 0;
  }

  // 1) 동일 인물 중복 제거 후 Actor upsert
  Map<Long, Actor> actorMap = response.getCast().stream()
      .collect(Collectors.toMap(
          TmdbCastDto::getId,
          this::toActor,
          (a, b) -> a));
  actorRepository.saveAll(actorMap.values());

  // 2) 재동기화 대비: 기존 매핑 제거
  movieActorRepository.deleteByMovieId(movieId);

  // 3) movie_actor 매핑 생성 — 1인 2역 중복 제거 + 더 앞선 castOrder 채택
  Map<Long, MovieActor> mappingMap = response.getCast().stream()
      .collect(Collectors.toMap(
          TmdbCastDto::getId,
          cast -> MovieActor.builder()
              .movie(movie)
              .actor(actorMap.get(cast.getId()))
              .characterName(cast.getCharacter())
              .castOrder(cast.getOrder())
              .build(),
          (a, b) -> safeOrder(a.getCastOrder()) <= safeOrder(b.getCastOrder()) ? a : b));
  movieActorRepository.saveAll(mappingMap.values());

  return mappingMap.size();
}

private int safeOrder(Integer order) {
  return order == null ? Integer.MAX_VALUE : order;
}
```

**판서로 정리:**

```
[1] Actor upsert
    - 같은 TMDB id 중복은 toMap merge 함수로 제거
    - PK 가 외부 부여 → saveAll 은 MERGE(이미 있으면 UPDATE, 없으면 INSERT)

[2] 기존 movie_actor 매핑 일괄 삭제
    - 같은 movieId 로 두 번 호출돼도 unique 위반 없음 (멱등성 확보)
    - @Modifying + JPQL DELETE — SQL 한 방

[3] movie_actor 매핑 재생성
    - 1인 2역 대비: 같은 actor id 가 두 번 등장하면 더 앞선 castOrder 채택
    - saveAll 한 번으로 batch insert
```

> **포인트**
> - `toMap` 의 3번째 인자(merge function)를 안 주면 같은 key 들어오는 순간 `IllegalStateException: Duplicate key`.
> - `@Modifying` 없는 DELETE 쿼리는 `Not supported for DML operations` 에러.

### 2-3. MovieActorRepository.deleteByMovieId — @Modifying

```java
// movie/MovieActorRepository.java
@Modifying
@Query("DELETE FROM MovieActor ma WHERE ma.movie.id = :movieId")
void deleteByMovieId(@Param("movieId") Long movieId);
```

| 방식 | 동작 |
|------|------|
| derived query (`deleteByMovie_Id`) | SELECT 후 한 건씩 DELETE — 영속성 컨텍스트 거침, 쿼리 폭증 |
| `@Modifying` + JPQL DELETE (현재) | 단일 DELETE — 영속성 컨텍스트 우회, 빠름 |

### 2-4. 실습 — credits 동기화 직접 실행

```bash
# 1. 사전: 영화 동기화가 되어 있어야 함
curl -s http://localhost:9000/api/movies/popular | python3 -c "import json,sys; print(len(json.load(sys.stdin)))"

# 2. 특정 영화의 credits 동기화
MOVIE_ID=1226863
curl -s -X POST "http://localhost:9000/api/movies/popular/${MOVIE_ID}/credits/sync"
# → {"movieId": 1226863, "saved": 22}

# 3. 같은 영화 한 번 더 — 멱등성 확인 (같은 saved 수, 에러 없음)
curl -s -X POST "http://localhost:9000/api/movies/popular/${MOVIE_ID}/credits/sync"

# 4. DB 확인
mysql -uroot movie -e "SELECT COUNT(*) FROM actor;"
mysql -uroot movie -e "SELECT COUNT(*) FROM movie_actor WHERE movie_id=${MOVIE_ID};"
mysql -uroot movie -e "SELECT character_name, cast_order FROM movie_actor WHERE movie_id=${MOVIE_ID} ORDER BY cast_order LIMIT 5;"
```

---

## Session 3. JPQL 심화 — self-join 과 집합 연산

### 3-1. 영화→출연진 / 배우→필모그래피 (JOIN FETCH 복습)

```java
// movie/MovieActorRepository.java
@Query("SELECT ma FROM MovieActor ma "
    + "JOIN FETCH ma.actor "
    + "WHERE ma.movie.id = :movieId "
    + "ORDER BY ma.castOrder ASC")
List<MovieActor> findByMovieIdWithActor(@Param("movieId") Long movieId);

@Query("SELECT ma FROM MovieActor ma "
    + "JOIN FETCH ma.movie "
    + "WHERE ma.actor.id = :actorId "
    + "ORDER BY ma.movie.popularity DESC")
List<MovieActor> findByActorIdWithMovie(@Param("actorId") Long actorId);
```

> **포인트** — 이번엔 `DISTINCT` 가 없다. 왜?
> `MovieActor → Actor` 는 `@ManyToOne` (단일 객체) → 결과 중복이 발생하지 않음.
> Day 1 에서 `DISTINCT` 가 필요했던 이유는 `@OneToMany` 컬렉션 join 으로 부모 행이 자식 수만큼 뻥튀기됐기 때문.

**Service 의 DTO 매핑:**

```java
// actor/ActorService.java
@Transactional(readOnly = true)
public List<CastMemberDto> findActorsByMovie(Long movieId) {
  return movieActorRepository.findByMovieIdWithActor(movieId).stream()
      .map(ma -> {
        Actor a = ma.getActor();
        return CastMemberDto.builder()
            .id(a.getId())
            .name(a.getName())
            .profilePath(a.getProfilePath())
            .gender(a.getGender())
            .popularity(a.getPopularity())
            .character(ma.getCharacterName())
            .castOrder(ma.getCastOrder())
            .build();
      })
      .toList();
}
```

`CastMemberDto` 는 Actor 의 필드 + MovieActor 의 관계 속성을 합친 형태:

```java
// actor/dto/CastMemberDto.java
@Getter @Builder
public class CastMemberDto {
  private Long id;
  private String name;
  private String profilePath;
  private Integer gender;
  private Double popularity;
  private String character;
  private Integer castOrder;
}
```

### 3-2. 동료 배우 — self-join (theta join)

**문제:** "배우 A 와 같은 영화에 함께 출연한 배우들을 찾아라."

**먼저 SQL 로 그려보기:**

```sql
SELECT DISTINCT a2.*
FROM movie_actor ma1
JOIN movie_actor ma2 ON ma1.movie_id = ma2.movie_id
JOIN actor a2 ON a2.id = ma2.actor_id
WHERE ma1.actor_id = :actorId
  AND ma2.actor_id <> :actorId;
```

→ 같은 테이블을 자기 자신과 조인 = **self-join**.

**JPQL (theta join 스타일):**

```java
@Query("SELECT DISTINCT ma2.actor FROM MovieActor ma1, MovieActor ma2 "
    + "WHERE ma1.movie = ma2.movie "
    + "AND ma1.actor.id = :actorId "
    + "AND ma2.actor.id <> :actorId "
    + "ORDER BY ma2.actor.popularity DESC")
List<Actor> findCoActors(@Param("actorId") Long actorId);
```

| 표현 | 의미 |
|------|------|
| `FROM MovieActor ma1, MovieActor ma2` | 콤마 = cross join + WHERE 로 조건 부여 (theta join) |
| `ma1.movie = ma2.movie` | 같은 영화에서 만난 두 출연 정보 |
| `ma1.actor.id = :actorId` | 기준 배우의 출연 |
| `ma2.actor.id <> :actorId` | 본인 제외 |
| `SELECT DISTINCT ma2.actor` | 여러 영화에서 함께 만났더라도 한 번만 |

> **포인트**
> - `<>` 조건 빠지면 본인 포함됨
> - `DISTINCT` 빠지면 A 와 B 가 영화 2편에서 만났을 때 B 가 2번 등장

### 3-3. 공동 출연작 — GROUP BY + HAVING COUNT(DISTINCT) (가장 중요)

**문제:** "배우 A, B, C 가 **모두 함께** 출연한 영화는?"

**사고 과정:**

```
movie_actor 에서 (actor_id IN [A, B, C]) 만 필터한 뒤
영화별로 묶고
그 그룹의 distinct actor 수가 3 이면 "교집합 조건 만족"

영화1: A         → 1  ✗
영화2: A, B      → 2  ✗
영화3: A, B, C   → 3  ✓
영화4: B, C      → 2  ✗
```

**JPQL:**

```java
@Query("SELECT ma.movie.id FROM MovieActor ma "
    + "WHERE ma.actor.id IN :actorIds "
    + "GROUP BY ma.movie.id "
    + "HAVING COUNT(DISTINCT ma.actor.id) = :count")
List<Long> findSharedMovieIds(@Param("actorIds") List<Long> actorIds,
    @Param("count") long count);
```

> **포인트** — 오늘의 핵심 패턴.
> - "여러 조건을 **모두** 만족하는 것 찾기" = `GROUP BY + HAVING COUNT(DISTINCT) = N`
> - 추천 시스템(여러 태그 모두 가진 상품), 권한 매칭(여러 역할 모두 보유한 사용자) 등 어디서나 응용 가능.
> - `COUNT(*)` 가 아니라 `COUNT(DISTINCT ...)` — 1인 2역으로 같은 배우가 2번 들어와도 정확.
> - 호출 쪽에서 `actorIds.distinct()` 안 하면 `:count` 가 distinct 수보다 커서 결과 항상 비어버림.

**Service 의 두 단계 쿼리:**

```java
// movie/MovieService.java
@Transactional(readOnly = true)
public List<TmdbMovieDto> findSharedMovies(List<Long> actorIds) {
  if (actorIds == null || actorIds.isEmpty()) {
    return List.of();
  }
  List<Long> distinctIds = actorIds.stream().distinct().toList();
  List<Long> movieIds = movieActorRepository.findSharedMovieIds(distinctIds, distinctIds.size());
  if (movieIds.isEmpty()) {
    return List.of();
  }
  return movieRepository.findAllByIdInWithGenres(movieIds).stream()
      .map(this::toDto)
      .toList();
}
```

**2단계 fetch join 쿼리 (장르까지 함께 로드):**

```java
// movie/MovieRepository.java
@Query("SELECT DISTINCT m FROM Movie m "
    + "LEFT JOIN FETCH m.movieGenres mg "
    + "LEFT JOIN FETCH mg.genre "
    + "WHERE m.id IN :ids "
    + "ORDER BY m.popularity DESC")
List<Movie> findAllByIdInWithGenres(@Param("ids") List<Long> ids);
```

---

## Session 4. 랭킹 쿼리, REST 라우팅, 슬라이스 테스트

### 4-1. 최다 출연 배우 — Pageable + Object[] 프로젝션

```java
// movie/MovieActorRepository.java
@Query("SELECT ma.actor, COUNT(ma) FROM MovieActor ma "
    + "GROUP BY ma.actor "
    + "ORDER BY COUNT(ma) DESC")
List<Object[]> findTopActorsByMovieCount(Pageable pageable);
```

**Service 의 변환:**

```java
@Transactional(readOnly = true)
public List<ActorRankDto> findTopActors(int limit) {
  return movieActorRepository.findTopActorsByMovieCount(PageRequest.of(0, Math.max(1, limit)))
      .stream()
      .map(row -> {
        Actor a = (Actor) row[0];
        long count = ((Number) row[1]).longValue();
        return ActorRankDto.builder()
            .id(a.getId())
            .name(a.getName())
            .profilePath(a.getProfilePath())
            .popularity(a.getPopularity())
            .movieCount(count)
            .build();
      })
      .toList();
}
```

```java
// actor/dto/ActorRankDto.java
@Getter @Builder
public class ActorRankDto {
  private Long id;
  private String name;
  private String profilePath;
  private Double popularity;
  private long movieCount;
}
```

> **포인트**
> - `Pageable` 을 인자에 넣으면 JPQL 에 LIMIT 가 자동 적용.
> - `((Number) row[1]).longValue()` — DB/dialect 별 COUNT 반환 타입이 달라도 안전.
> - `Math.max(1, limit)` — `limit=0` 방어 (`PageRequest` 가 던지는 `IllegalArgumentException` 회피).

### 4-2. REST 라우팅 함정 — `/top` vs `/{id}` 순서

```java
// actor/ActorController.java
@RestController
@RequestMapping("/api/actors")
@RequiredArgsConstructor
public class ActorController {

  private final ActorService actorService;

  @GetMapping
  public List<ActorDto> list(@RequestParam(required = false) String name) {
    return (name == null) ? actorService.findAll() : actorService.searchByName(name);
  }

  /** 최다 출연 배우 랭킹. ({@code /{id}} 보다 위에 둬야 "top" 이 경로변수로 잡히지 않음) */
  @GetMapping("/top")
  public List<ActorRankDto> top(@RequestParam(defaultValue = "10") int limit) {
    return actorService.findTopActors(limit);
  }

  @GetMapping("/{id}")
  public ResponseEntity<ActorDto> detail(@PathVariable("id") Long id) {
    return actorService.findById(id)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /** 배우로 출연 영화 목록 검색. */
  @GetMapping("/{id}/movies")
  public ResponseEntity<List<FilmographyDto>> movies(@PathVariable("id") Long id) {
    List<FilmographyDto> result = actorService.findFilmography(id);
    if (result.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(result);
  }

  /** 함께 출연한 동료 배우 검색. */
  @GetMapping("/{id}/co-actors")
  public List<ActorDto> coActors(@PathVariable("id") Long id) {
    return actorService.findCoActors(id);
  }
}
```

> **포인트** — 라우트 컨벤션:
> - 정적 경로 (`/top`) 를 경로변수 (`/{id}`) **위에** 선언한다.
> - 만약 `/{id}` 가 `String` 타입이었다면 `/top` 호출 시 `"top"` 이 id 로 잡혀 detail 로 흘러갔을 위험. `Long` 타입이라 사고는 막히지만, 컨벤션은 항상 지킨다.

### 4-3. MovieController — 출연진 엔드포인트

```java
// movie/MovieController.java 발췌

/** 특정 영화의 출연진(cast)을 TMDB 에서 동기화. */
@PostMapping("/{id}/credits/sync")
public ResponseEntity<Map<String, Object>> syncCredits(@PathVariable("id") Long id) {
  int saved = actorService.syncCredits(id);
  return ResponseEntity.ok(Map.of("movieId", id, "saved", saved));
}

/** 영화로 출연진 검색 (castOrder 순). */
@GetMapping("/{id}/actors")
public ResponseEntity<List<CastMemberDto>> actors(@PathVariable("id") Long id) {
  List<CastMemberDto> result = actorService.findActorsByMovie(id);
  if (result.isEmpty()) {
    return ResponseEntity.notFound().build();
  }
  return ResponseEntity.ok(result);
}

/** 영화별 출연진 수. */
@GetMapping("/{id}/actors/count")
public Map<String, Object> actorCount(@PathVariable("id") Long id) {
  return Map.of("movieId", id, "count", actorService.countActorsByMovie(id));
}

/**
 * 두 명 이상의 배우가 함께 출연한 영화 검색.
 * 예) /api/movies/popular/shared?actorId=1&actorId=2
 */
@GetMapping("/shared")
public List<TmdbMovieDto> sharedMovies(@RequestParam("actorId") List<Long> actorIds) {
  return movieService.findSharedMovies(actorIds);
}
```

> **포인트**
> - `@RequestParam("actorId") List<Long>` — `?actorId=1&actorId=2` 같은 반복 파라미터를 자동으로 List 로 매핑.
> - 서브리소스 라우팅: `/{id}/actors`, `/{id}/credits/sync` — URL 만 봐도 의미가 드러남.

### 4-4. 실습 — curl 로 전체 API 확인

```bash
# 사전 준비 (Day 1·2 완료 가정)
curl -s -X POST http://localhost:9000/api/genres/sync
curl -s -X POST "http://localhost:9000/api/movies/popular/sync?page=1"

# 영화 id 1개 확인
MOVIE_ID=$(curl -s http://localhost:9000/api/movies/popular | python3 -c "import json,sys; print(json.load(sys.stdin)[0]['id'])")
echo "movie id: $MOVIE_ID"

# 1. 출연진 동기화
curl -s -X POST "http://localhost:9000/api/movies/popular/${MOVIE_ID}/credits/sync"

# 2. 영화별 출연진 (castOrder 순)
curl -s "http://localhost:9000/api/movies/popular/${MOVIE_ID}/actors" | python3 -c "
import json, sys
for c in json.load(sys.stdin)[:5]:
  print(f\"{c['castOrder']:>3} | {c['name']} | {c['character']}\")
"

# 3. 출연진 수
curl -s "http://localhost:9000/api/movies/popular/${MOVIE_ID}/actors/count"

# 4. 배우 id 1개 확인 후 필모그래피 + 동료 배우 + 랭킹
ACTOR_ID=$(curl -s "http://localhost:9000/api/movies/popular/${MOVIE_ID}/actors" | python3 -c "import json,sys; print(json.load(sys.stdin)[0]['id'])")
echo "actor id: $ACTOR_ID"

curl -s "http://localhost:9000/api/actors/${ACTOR_ID}"
curl -s "http://localhost:9000/api/actors/${ACTOR_ID}/movies" | python3 -c "
import json, sys
for f in json.load(sys.stdin)[:5]:
  print(f\"{f['title']} ({f['character']})\")
"
curl -s "http://localhost:9000/api/actors/${ACTOR_ID}/co-actors" | python3 -c "
import json, sys
for a in json.load(sys.stdin)[:5]:
  print(a['name'])
"

# 5. 최다 출연 랭킹
curl -s "http://localhost:9000/api/actors/top?limit=5" | python3 -c "
import json, sys
for r in json.load(sys.stdin):
  print(f\"{r['movieCount']}편 | {r['name']}\")
"

# 6. 배우 이름 검색
curl -s "http://localhost:9000/api/actors?name=tom"

# 7. 공동 출연작 (배우 ID 2개)
ACTOR_ID2=$(curl -s "http://localhost:9000/api/movies/popular/${MOVIE_ID}/actors" | python3 -c "import json,sys; print(json.load(sys.stdin)[1]['id'])")
curl -s "http://localhost:9000/api/movies/popular/shared?actorId=${ACTOR_ID}&actorId=${ACTOR_ID2}" | python3 -c "
import json, sys
for m in json.load(sys.stdin):
  print(m['title'])
"
```

### 4-5. 슬라이스 테스트 — @DataJpaTest + H2

```java
// src/test/java/com/example/movie/movie/MovieActorRepositoryTest.java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:movie_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class MovieActorRepositoryTest {

  @Autowired private TestEntityManager em;
  @Autowired private MovieActorRepository movieActorRepository;

  private Actor a1, a2, a3;
  private Movie m1, m2, m3;

  @BeforeEach
  void setUp() {
    a1 = persistActor(10L, "Alice", 90.0);
    a2 = persistActor(20L, "Bob", 80.0);
    a3 = persistActor(30L, "Carol", 70.0);

    m1 = persistMovie(1L, "Movie One", 50.0);
    m2 = persistMovie(2L, "Movie Two", 100.0);
    m3 = persistMovie(3L, "Movie Three", 10.0);

    // M1: A1(주연), A2 / M2: A1, A3 / M3: A2
    link(m1, a1, "Hero", 0);
    link(m1, a2, "Villain", 1);
    link(m2, a1, "Detective", 0);
    link(m2, a3, "Sidekick", 1);
    link(m3, a2, "Lead", 0);

    em.flush();
    em.clear();
  }

  @Test
  @DisplayName("영화로 출연진 검색: castOrder 오름차순으로 actor 가 함께 로딩된다")
  void findByMovieIdWithActor() {
    List<MovieActor> cast = movieActorRepository.findByMovieIdWithActor(1L);
    assertThat(cast).hasSize(2);
    assertThat(cast).extracting(ma -> ma.getActor().getName())
        .containsExactly("Alice", "Bob");
  }

  @Test
  @DisplayName("함께 출연한 동료 배우: 본인은 제외하고 중복 없이 반환된다")
  void findCoActors() {
    List<Actor> coActors = movieActorRepository.findCoActors(10L);
    assertThat(coActors).extracting(Actor::getName)
        .containsExactlyInAnyOrder("Bob", "Carol");
    assertThat(coActors).extracting(Actor::getId).doesNotContain(10L);
  }

  @Test
  @DisplayName("함께 출연한 영화: 전달한 배우가 모두 출연한 영화만 교집합으로 반환된다")
  void findSharedMovieIds() {
    assertThat(movieActorRepository.findSharedMovieIds(List.of(10L, 20L), 2))
        .containsExactly(1L);
    assertThat(movieActorRepository.findSharedMovieIds(List.of(10L, 30L), 2))
        .containsExactly(2L);
    assertThat(movieActorRepository.findSharedMovieIds(List.of(20L, 30L), 2))
        .isEmpty();
  }

  // persistActor, persistMovie, link 헬퍼는 본문 코드 참조
}
```

| 항목 | `@SpringBootTest` | `@DataJpaTest` |
|------|-------------------|----------------|
| 로딩 범위 | 전체 컨텍스트 | JPA 빈만 |
| DB | application.yaml (MySQL) | embedded (기본 H2) |
| TMDB 토큰 | 필요 | 불필요 |
| 트랜잭션 | 기본 없음 | 자동 rollback |
| 속도 | 느림 | 빠름 |

> **포인트**
> - `MODE=MySQL` — H2 가 MySQL 방언을 흉내냄 (JPQL 검증엔 충분).
> - `@AutoConfigureTestDatabase(replace = ANY)` — application.yaml 의 MySQL 설정 무시하고 강제 H2.
> - `em.flush(); em.clear();` — 1차 캐시 비워야 실제 SELECT 가 발생.

**실행:**

```bash
./gradlew test --tests "com.example.movie.movie.MovieActorRepositoryTest"
```

---

## 전체 API 정리 (Day 1·2·3 누적)

| 메서드 | 경로 | 설명 | Day |
|--------|------|------|-----|
| POST | /api/genres/sync | 장르 동기화 | 기존 |
| GET | /api/genres | 장르 전체 조회 | 기존 |
| GET | /api/genres/stats | 장르별 영화 수 통계 | Day 2 |
| POST | /api/movies/popular/sync | 영화 동기화 | 기존 |
| GET | /api/movies/popular | 전체/필터/정렬 | Day 1·2 |
| GET | /api/movies/popular/{id}/genres | 영화의 장르 | Day 2 |
| DELETE | /api/movies/popular/{id} | 영화 삭제 | 기존 |
| POST | /api/movies/popular/{id}/credits/sync | TMDB credits 동기화 | **Day 3** |
| GET | /api/movies/popular/{id}/actors | 영화별 출연진 (castOrder 순) | **Day 3** |
| GET | /api/movies/popular/{id}/actors/count | 영화별 출연진 수 | **Day 3** |
| GET | /api/movies/popular/shared?actorId=...&actorId=... | 공동 출연작 (교집합) | **Day 3** |
| GET | /api/actors | 전체 배우 / 이름 검색 | **Day 3** |
| GET | /api/actors/top?limit=N | 최다 출연 배우 랭킹 | **Day 3** |
| GET | /api/actors/{id} | 배우 단건 | **Day 3** |
| GET | /api/actors/{id}/movies | 배우 필모그래피 | **Day 3** |
| GET | /api/actors/{id}/co-actors | 동료 배우 (self-join) | **Day 3** |

---

## 자주 나오는 질문

| 질문 | 답변 |
|------|------|
| Actor 와 MovieActor 를 양방향으로 연결해야 하나요? | 필요 없습니다. 양방향은 동기화 부담만 늘립니다. 배우→영화 검색은 리파지토리 쿼리로 해결합니다 |
| 왜 `Actor` 에 `cascade` 가 없나요? | 배우는 독립 도메인입니다. 영화 삭제로 배우 정보가 사라지면 다른 영화에서 참조가 깨집니다 |
| `@ManyToMany` 에 `@JoinTable` 로 컬럼을 못 추가하나요? | 못 합니다. 그래서 관계 속성이 필요하면 반드시 중간 엔티티가 필요합니다 |
| `toMap` 의 merge 함수는 왜 필요한가요? | 같은 key 가 두 번 들어오면 기본 동작은 `IllegalStateException`. merge 함수가 충돌 처리 규칙입니다 |
| 슬라이스 테스트로 충분한가요, 통합 테스트도 필요한가요? | 권장은 둘 다. 슬라이스로 쿼리 정확성, 통합으로 트랜잭션·API 흐름 검증 |
| `findSharedMovies` 를 한 쿼리로 줄일 수 있나요? | 가능하지만 가독성이 떨어집니다. 현재 두 단계 분리가 디버깅·재사용에 유리 |
| TMDB 의 crew(감독)도 같이 저장하려면? | 별도 `MovieCrew` 엔티티 권장. cast 와 의미가 다른 도메인이라 같은 테이블에 합치면 비효율 |

---

## 직접 풀어볼 실습 문제 (해설 없음)

### 문제 1 — co-actors 결과 수 제한

`GET /api/actors/{id}/co-actors?limit=5` — 상위 5명만 반환하도록 확장하라.
정렬은 기존과 동일하게 `popularity DESC`.

> 힌트: `MovieActorRepository.findCoActors` 에 `Pageable` 파라미터 추가.

### 문제 2 — 배역명(character) 검색 API

`GET /api/actors/search-by-character?keyword=Tony` — 배역명에 키워드를 포함한 출연 정보 목록을 반환하라.

응답 형식은 자유롭게 설계하되, 최소한 다음 정보가 보여야 한다:
- 영화 제목
- 배우 이름
- 맡은 배역

> 힌트: 새로운 DTO 를 만들고 JPQL 에서 두 엔티티를 fetch join.

### 문제 3 — 멱등성 깨기 실험

`ActorService.syncCredits` 에서 `movieActorRepository.deleteByMovieId(movieId);` 줄을 주석 처리한 뒤,
같은 영화 id 로 동기화를 2회 호출하여 에러를 재현하라.

- 어떤 에러가 발생하는가?
- 에러 메시지에 어떤 제약 이름이 나타나는가?
- 복구 후 다시 두 번 호출해 정상 동작을 확인하라.

### 문제 4 — `HAVING COUNT(*)` 와 `HAVING COUNT(DISTINCT)` 차이 검증

`MovieActorRepository.findSharedMovieIds` 에서 `COUNT(DISTINCT ma.actor.id)` 를 `COUNT(*)` 로 바꾼 뒤,
다음 시나리오에 대한 슬라이스 테스트를 작성하라.

- 배우 A 가 영화 M 에서 1인 2역으로 등장 (movie_actor 에 같은 actor_id 가 두 번 직접 insert)
- 입력: `findSharedMovieIds([A], 1)`

→ 두 버전에서 결과가 어떻게 다른가? 어느 쪽이 맞는가?

### 문제 4 — 평점 가중 랭킹

`GET /api/actors/top?weight=vote` — 단순 출연 편수가 아니라 출연 영화들의 `voteAverage` 합으로 정렬한 랭킹을 반환하라.

> 힌트: JPQL `SUM(ma.movie.voteAverage)` 사용. 결과는 `ActorRankDto` 에 `totalScore` 필드 추가하거나 새 DTO 작성.
