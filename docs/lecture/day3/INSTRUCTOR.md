# 강의 자료 — Day 3: 출연자(Actor) N:M 심화 — 관계 속성을 가진 중간 엔티티 & JPQL 집계 응용 (강사용)

**과정명**: Spring Boot 실무 — N:M 심화, 관계 속성 중간 엔티티, JPQL 집계·self-join, 슬라이스 테스트
**대상**: Day 1·2 완료자 (MovieGenre 중간 엔티티, JPQL GROUP BY, OpenAPI 이해)
**총 소요시간**: 4시간 (60분 × 4세션)
**선수 지식**: Day 1·2 내용, `@OneToMany`/`@ManyToOne`, JPQL `JOIN FETCH`/`GROUP BY`/`HAVING`

---

## 학습 목표

- 중간 엔티티가 **관계 자체의 속성**(배역명, 출연순서)을 가져야 하는 경우를 식별하고 설계할 수 있다 (분석)
- TMDB `/movie/{id}/credits` 응답을 Actor + MovieActor 로 분리 저장하고, 재동기화 시 unique 제약을 위반하지 않는 동기화 로직을 작성할 수 있다 (적용)
- JPQL 의 self-join (`FROM MovieActor ma1, MovieActor ma2`) 과 `GROUP BY ... HAVING COUNT(DISTINCT)` 패턴으로 "동료 배우", "공동 출연작" 같은 집합 연산을 표현할 수 있다 (적용)
- `Pageable` + `Object[]` 프로젝션으로 랭킹 쿼리를 구현하고, 결과를 DTO 로 변환할 수 있다 (적용)
- `@DataJpaTest` + H2(MODE=MySQL) 슬라이스 테스트로 N:M 쿼리를 외부 의존 없이 검증할 수 있다 (적용)
- REST 라우팅에서 정적 경로(`/top`)와 경로변수(`/{id}`) 충돌을 회피하는 선언 순서를 적용할 수 있다 (적용)

## 최종 산출물

- `Actor`, `MovieActor` 엔티티 및 리파지토리
- `POST /api/movies/popular/{id}/credits/sync` — TMDB credits 동기화 API
- `GET /api/movies/popular/{id}/actors` — 영화별 출연진 (castOrder 순)
- `GET /api/movies/popular/{id}/actors/count` — 영화별 출연진 수
- `GET /api/actors`, `GET /api/actors?name=...` — 배우 조회/검색
- `GET /api/actors/{id}/movies` — 필모그래피 (인기순)
- `GET /api/actors/{id}/co-actors` — 동료 배우 (self-join)
- `GET /api/actors/top?limit=10` — 최다 출연 배우 랭킹
- `GET /api/movies/popular/shared?actorId=1&actorId=2` — 공동 출연작 (교집합)
- `@DataJpaTest` 기반 H2 슬라이스 테스트 — `MovieActorRepositoryTest`

---

## Session 1. 관계 속성을 가진 중간 엔티티 설계 (60분)

### 1-1. Day 1 복습 — MovieGenre 는 "연결만 하는" 중간 엔티티 (10분)

먼저 학생들에게 질문을 던진다.

> "Day 1 에서 만든 `MovieGenre` 엔티티에는 어떤 컬럼이 있었죠?"

판서:

```
movie_genre
  id          ← 대리키
  movie_id    ← FK
  genre_id    ← FK
```

→ **두 엔티티를 연결하는 것 외에는 아무 정보도 없다.** 이런 경우 `@ManyToMany` 로 대체해도 큰 차이가 없다(다만 확장성 때문에 중간 엔티티로 유지).

> **강의 포인트**
> - 학생들에게 "장르에 `등록일자`, `주요장르여부` 가 필요하다면?" 물어본다.
> - 그러면 `MovieGenre` 가 그 컬럼을 가져야 한다 → `@ManyToMany` 로는 표현 불가.
> - 이것이 오늘 다룰 `MovieActor` 의 핵심이다.

---

### 1-2. 영화-배우 관계는 "어떻게 출연하는지" 가 필요하다 (15분)

질문으로 시작:

> "어벤져스에 로버트 다우니 주니어가 출연한다. 영화↔배우 두 정보만으로 충분합니까?"

학생들이 깨닫게 유도한다.

```
실제로 필요한 정보:
  - 배역명 (character):   "토니 스타크 / 아이언맨"
  - 출연 순서 (cast_order): 0  ← 0 에 가까울수록 주연
```

→ **이 두 컬럼은 영화에도, 배우에도 속하지 않는다. "이 영화에 이 배우가 출연한다는 사실" 자체에 속한다.**

판서:

```
영화 (1)  ─< movie_actor >─  (N) 배우
                ↑ 이 자리에 character, cast_order 가 들어간다
```

> **강의 포인트**
> - WHY: 관계의 속성(relationship attribute)이라는 개념. ERD 이론에서도 핵심 주제.
> - PITFALL: 학생들이 종종 `character` 를 `Actor` 엔티티에 넣으려 한다. "그러면 같은 배우가 다른 영화에서 다른 배역을 맡으면 어떻게 되죠?" 라고 되물어준다.
> - PITFALL: `character` 를 `Movie` 에 넣는 것도 마찬가지로 잘못. "한 영화에 배역이 여러 개인데 어디에 저장?"

---

### 1-3. @ManyToMany 가 절대 불가능한 이유 (10분)

```java
// ❌ 절대 안 되는 예 — character/castOrder 를 둘 곳이 없다
@Entity
public class Movie {

  @ManyToMany
  @JoinTable(
      name = "movie_actor",
      joinColumns = @JoinColumn(name = "movie_id"),
      inverseJoinColumns = @JoinColumn(name = "actor_id")
  )
  private List<Actor> actors = new ArrayList<>();
}
```

문제: `@ManyToMany` 의 중간 테이블은 JPA 가 내부적으로 관리한다. 우리가 그 테이블의 엔티티를 갖지 못하므로 `character`, `cast_order` 같은 컬럼을 추가할 자리가 없다.

> **강의 포인트**
> - WHY: Day 1 에서는 "확장성 때문에 중간 엔티티가 좋다" 정도였지만, 오늘은 **중간 엔티티가 아니면 표현 자체가 불가능**한 사례다.
> - WHAT: `@ManyToMany` 가 만드는 join table 도 결국 DB 의 테이블이지만, JPA 가 "감춰서 관리하므로" 외부 컬럼을 추가하는 인터페이스가 없다.

---

### 1-4. MovieActor 엔티티 — 실제 코드 분석 (15분)

`src/main/java/com/example/movie/movie/MovieActor.java`:

```java
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

판서로 강조할 4가지:

| 요소 | 의미 |
|------|------|
| `UNIQUE(movie_id, actor_id)` | "같은 영화 + 같은 배우" 중복 차단 (TMDB 1인 2역 대비 코드와 연결됨) |
| `@ManyToOne(LAZY)` | FK 양쪽 모두 LAZY. EAGER 면 N+1 폭증 |
| `characterName` | 관계 속성 — 영화에도 배우에도 못 둠 |
| `castOrder` | 관계 속성 — TMDB `order` 그대로 사용 |

> **강의 포인트**
> - WHY: `UNIQUE` 제약은 DB 레벨 마지막 방어선. 코드에서도 중복 제거하지만, 어떤 이유든 두 번 들어오면 DB 가 막아준다.
> - PITFALL: 1인 2역(예: `[토니 스타크 / 아이언맨]` 과 `[Tony Stark]` 가 서로 다른 cast row 로 내려오는 경우)이 실제 TMDB 응답에 존재한다. 단순히 `saveAll` 만 하면 unique 제약 위반. 동기화 코드에서 어떻게 처리하는지는 Session 2 에서 다룬다.

---

### 1-5. Actor 엔티티 — 독립 도메인 + TMDB id 를 PK 로 (10분)

`src/main/java/com/example/movie/actor/Actor.java`:

```java
@Entity
@Table(name = "actor")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Actor {

  @Id
  @Column(name = "id", nullable = false)
  private Long id;  // TMDB person id 를 그대로 PK 로 사용 (Genre/Movie 와 동일 전략)

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

**Movie 엔티티의 컬렉션 (Movie.java 발췌):**

```java
@Builder.Default
@OneToMany(mappedBy = "movie",  // MovieActor.movie 가 연관관계 주인
  cascade = CascadeType.ALL,    // Movie 삭제 시 출연 매핑도 함께 삭제
  orphanRemoval = true,
  fetch = FetchType.LAZY)
private List<MovieActor> movieActors = new ArrayList<>();  // Actor 자체는 cascade 대상 아님(독립 도메인)
```

> **강의 포인트**
> - WHY: PK 를 TMDB id 로 직접 쓰는 이유 — Day 1 에서 다룬 전략(Genre/Movie 와 동일). TMDB 가 외부 시스템과 우리 DB 의 동기화 키.
> - WHAT: `cascade` 가 흐르는 경계가 **애그리거트(aggregate)** 경계다.
>   - `Movie → MovieActor`: cascade ALL — 영화 삭제 시 출연 매핑(연결 정보) 도 함께 삭제. 영화의 일부로 본다.
>   - `MovieActor → Actor`: cascade 없음 — 배우는 독립 도메인. 영화가 삭제돼도 배우는 살아남아야 다른 영화에서 계속 참조 가능.
> - PITFALL: 학생들이 종종 `Actor` 에 `@OneToMany List<MovieActor>` 양방향을 추가하려 한다. 양방향은 편의 메서드와 동기화 부담만 늘린다. 단방향(Movie → MovieActor)으로 충분하다. 배우→영화 검색은 리파지토리 쿼리로 해결.

---

## Session 2. TMDB credits 동기화 — unique 제약을 깨지 않는 3단계 패턴 (60분)

### 2-1. TMDB `/movie/{id}/credits` 응답 구조 (10분)

curl 로 실제 응답을 보여준다 (`{TMDB_ID}` 는 영화 id, `{TOKEN}` 은 bearer):

```bash
curl -s "https://api.themoviedb.org/3/movie/{TMDB_ID}/credits?language=ko" \
  -H "Authorization: Bearer {TOKEN}" | python3 -m json.tool | head -40
```

응답 구조 (간략):

```json
{
  "id": 1226863,
  "cast": [
    {"id": 1234, "name": "...", "character": "...", "order": 0, "popularity": 12.3, "gender": 2, "profile_path": "..."},
    {"id": 5678, "name": "...", "character": "...", "order": 1, ...}
  ],
  "crew": [
    {"id": 9999, "job": "Director", ...}
  ]
}
```

**중요:** crew(감독·제작진) 는 우리 도메인의 "출연자" 가 아니다.

`TmdbCreditsResponse.java`:

```java
@Getter @Setter @NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TmdbCreditsResponse {

  private Long id;
  private List<TmdbCastDto> cast;
  // crew 필드를 일부러 매핑하지 않음 → Jackson 이 자동으로 무시
}
```

> **강의 포인트**
> - WHY: "DTO 에 매핑하지 않은 필드는 무시된다" 는 Jackson 기본 동작 + `@JsonIgnoreProperties(ignoreUnknown = true)` 보강.
> - WHAT: 필요 없는 데이터는 받지 않는 것이 가장 좋은 방어. 굳이 받아서 if 로 필터링하는 것보다 깔끔.
> - PITFALL: 학생이 "crew 도 나중에 쓸지 모르니 받아두자" 라고 한다면 — YAGNI. 필요해질 때 추가한다.

---

### 2-2. ActorService.syncCredits() 단계별 분석 (25분)

`src/main/java/com/example/movie/actor/ActorService.java` 의 `syncCredits` 메서드를 줄 단위로 따라간다.

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

  // 3) movie_actor 매핑 생성 — 1인 2역 중복 제거 + castOrder 가 더 앞선 것 채택
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

  log.info("credits 저장 완료: movieId={}, actors={}, mappings={}",
      movieId, actorMap.size(), mappingMap.size());
  return mappingMap.size();
}
```

**3단계 패턴 — 칠판에 그려가며 설명:**

```
[1] Actor upsert
    ─ 동일 인물(같은 TMDB id) 중복은 toMap merge 함수로 제거
    ─ saveAll → @Id 가 이미 존재하면 MERGE(UPDATE), 없으면 INSERT
       (= upsert. PK 를 외부에서 부여하기 때문에 가능)

[2] 기존 movie_actor 매핑 삭제 — deleteByMovieId(movieId)
    ─ 재동기화 대비. 동일 movieId 로 두 번 호출돼도 unique 제약 위반 없음
    ─ @Modifying + JPQL DELETE 로 일괄 실행 (loop 으로 한 건씩 X)

[3] movie_actor 매핑 재생성
    ─ 1인 2역: 같은 actor id 가 cast 배열에 두 번 나타날 수 있음
       → toMap 의 merge 함수로 castOrder 가 더 앞선(주연에 가까운) 매핑 채택
    ─ saveAll 한 번으로 batch insert
```

**toMap merge 함수의 의미:**

```java
Collectors.toMap(
    TmdbCastDto::getId,    // key: actor id
    this::toActor,         // value 생성
    (a, b) -> a)           // 이미 같은 key 가 있다면 어떻게? → 기존 값 유지
```

> **강의 포인트**
> - WHY: `toMap` 에 3번째 인자(merge function)를 안 주면 같은 key 가 들어오는 순간 `IllegalStateException: Duplicate key` 발생. TMDB 응답은 실제로 중복이 들어오므로 반드시 필요.
> - WHAT: PK 를 외부에서 부여하는 엔티티는 `save()` 가 INSERT 가 아니라 MERGE 로 동작한다. 이미 있는 row 면 UPDATE.
> - PITFALL: `deleteByMovieId` 없이 `saveAll` 만 호출하면 두 번째 호출에서 `SQLIntegrityConstraintViolationException` (unique 제약) 발생. 학생들에게 실제로 한 번 실행해 보여주면 학습 효과 극대.
> - PITFALL: `deleteByMovieId` 가 `@Modifying` 어노테이션이 없으면 `InvalidDataAccessApiUsageException: Not supported for DML operations` 에러. 반드시 함께 붙는다.

**`safeOrder` 헬퍼:**

```java
private int safeOrder(Integer order) {
  return order == null ? Integer.MAX_VALUE : order;
}
```

> **강의 포인트**
> - PITFALL: `castOrder` 가 null 인 경우(TMDB 응답 누락) `Integer` ↔ `int` 비교에서 NPE 발생. `MAX_VALUE` 로 치환해 자연스럽게 후순위로 밀어낸다.

---

### 2-3. MovieActorRepository — deleteByMovieId 의 @Modifying (10분)

```java
@Modifying
@Query("DELETE FROM MovieActor ma WHERE ma.movie.id = :movieId")
void deleteByMovieId(@Param("movieId") Long movieId);
```

**Spring Data 가 만들어주는 derived query 와의 차이:**

```java
// 옵션 A — derived query (이름으로 자동 생성)
void deleteByMovie_Id(Long movieId);
// → SELECT 로 먼저 조회 후 한 건씩 DELETE. 영속성 컨텍스트 거침.

// 옵션 B — @Modifying + JPQL (현재 코드)
@Modifying @Query("DELETE FROM MovieActor ma WHERE ma.movie.id = :movieId")
void deleteByMovieId(@Param("movieId") Long movieId);
// → 단일 DELETE 쿼리. 영속성 컨텍스트 거치지 않음.
```

> **강의 포인트**
> - WHY: 대량 삭제 시 옵션 A 는 N+1 DELETE 폭증. 옵션 B 는 SQL 한 방.
> - WHAT: `@Modifying` 은 "이 쿼리는 SELECT 가 아니라 UPDATE/DELETE 다" 라고 Spring Data 에 알려주는 표시. 트랜잭션 안에서만 동작.
> - PITFALL: 옵션 B 는 영속성 컨텍스트를 우회하므로, 같은 트랜잭션에서 직전에 조회한 `MovieActor` 가 메모리에 남아있을 수 있다. 동기화 직후 다시 조회하면 `em.clear()` 또는 새 트랜잭션이 필요할 수 있음. 본 동기화 패턴은 메서드 끝에서 트랜잭션 종료라 무관.

---

### 2-4. 실습 — credits 동기화 실행 + DB 확인 (15분)

```bash
# 0. 사전: 영화 동기화가 되어 있어야 함 (Day 1 이미 완료)
curl -s http://localhost:9000/api/movies/popular | python3 -c "import json,sys; print(len(json.load(sys.stdin)))"

# 1. 특정 영화의 credits 동기화 — id 는 위 응답에서 하나 골라 사용
MOVIE_ID=1226863
curl -s -X POST "http://localhost:9000/api/movies/popular/${MOVIE_ID}/credits/sync"
# → {"movieId": 1226863, "saved": 22}

# 2. 같은 영화 한 번 더 — unique 제약 위반 없이 saved 가 같은 값으로 나옴 (멱등성 확인)
curl -s -X POST "http://localhost:9000/api/movies/popular/${MOVIE_ID}/credits/sync"

# 3. DB 에서 직접 확인
mysql -uroot movie -e "SELECT COUNT(*) FROM actor;"
mysql -uroot movie -e "SELECT COUNT(*) FROM movie_actor WHERE movie_id=${MOVIE_ID};"
mysql -uroot movie -e "SELECT character_name, cast_order FROM movie_actor WHERE movie_id=${MOVIE_ID} ORDER BY cast_order LIMIT 5;"
```

> **강의 포인트**
> - 학생들에게 "왜 두 번 실행해도 깨지지 않는지" 를 강조. 멱등성(idempotency)은 실무 동기화의 핵심.
> - 만약 학생이 `deleteByMovieId` 를 주석 처리하고 두 번 실행하면 → `Duplicate entry ... for key 'movie_actor.UK...'` 에러. 일부러 보여주면 좋다.

---

## Session 3. JPQL 심화 — self-join 과 집합 연산 (60분)

### 3-1. 영화→출연진 / 배우→필모그래피 (15분)

Day 1·2 의 `JOIN FETCH` 복습 + `ORDER BY` 응용.

```java
// MovieActorRepository.java
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

**판서 — N+1 회피 흐름:**

```
findByMovieIdWithActor(1L)
   │
   ▼ SQL 1번:
   SELECT ma.*, a.* FROM movie_actor ma
     INNER JOIN actor a ON a.id = ma.actor_id
   WHERE ma.movie_id = 1
   ORDER BY ma.cast_order ASC

→ MovieActor 한 건당 Actor 가 이미 같이 들어옴
   ma.getActor().getName()  ← 추가 쿼리 없음
```

> **강의 포인트**
> - WHY: `JOIN FETCH ma.actor` 가 없으면, `ma.getActor().getName()` 접근 시점에 배우 수만큼 SELECT 추가 → N+1.
> - WHAT: 이번엔 `DISTINCT` 가 없다. 왜? MovieActor → Actor 가 `@ManyToOne` (단일 객체)이라서 결과 중복이 발생하지 않음. Day 1 에서 `DISTINCT` 가 필요했던 이유는 `@OneToMany` 컬렉션 join 으로 부모 행이 자식 수만큼 중복됐기 때문.
> - PITFALL: 학생이 "Day 1 에선 `DISTINCT` 가 필요했는데 왜 여기선 안 써요?" 질문할 가능성 높음. 이 차이를 명확히 짚어준다.

**Service 코드 — DTO 매핑:**

```java
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

> **강의 포인트**
> - WHAT: 응답 DTO 가 `Actor` 의 모든 필드 + `MovieActor` 의 관계 속성(character, castOrder) 을 합친 형태. **두 엔티티의 정보를 한 화면에 보여줘야 하므로** 별도 DTO 가 필요.

---

### 3-2. 동료 배우 검색 — self-join (theta join) (20분)

**문제 정의:** "배우 A 와 같은 영화에 함께 출연한 배우들을 찾아라."

학생들에게 직접 SQL 을 그려보게 한다.

```sql
-- 사고 과정
SELECT DISTINCT a2.*
FROM movie_actor ma1
JOIN movie_actor ma2 ON ma1.movie_id = ma2.movie_id
JOIN actor a2 ON a2.id = ma2.actor_id
WHERE ma1.actor_id = :actorId
  AND ma2.actor_id <> :actorId;
```

→ **같은 테이블을 자기 자신과 조인 = self-join.**

**JPQL 버전 (실제 코드):**

```java
@Query("SELECT DISTINCT ma2.actor FROM MovieActor ma1, MovieActor ma2 "
    + "WHERE ma1.movie = ma2.movie "
    + "AND ma1.actor.id = :actorId "
    + "AND ma2.actor.id <> :actorId "
    + "ORDER BY ma2.actor.popularity DESC")
List<Actor> findCoActors(@Param("actorId") Long actorId);
```

**JPQL 키워드 해부:**

| 표현 | 의미 |
|------|------|
| `FROM MovieActor ma1, MovieActor ma2` | theta join (콤마 = cross join + WHERE 로 조건 부여) |
| `ma1.movie = ma2.movie` | 같은 영화에서 만난 두 출연 정보 |
| `ma1.actor.id = :actorId` | 기준 배우의 출연 |
| `ma2.actor.id <> :actorId` | 본인 제외 |
| `SELECT DISTINCT ma2.actor` | 여러 영화에서 함께 만났더라도 중복 제거 |

> **강의 포인트**
> - WHY: 같은 의미를 `JOIN ... ON` 으로도 쓸 수 있다 (`FROM MovieActor ma1 JOIN MovieActor ma2 ON ma1.movie = ma2.movie ...`). 어떤 스타일도 가능하지만, 옛 SQL 책에서 자주 보는 콤마 + WHERE 형태(theta join)도 익숙해질 필요가 있다.
> - WHAT: `ma1.movie = ma2.movie` 는 엔티티 객체 비교지만 Hibernate 는 PK 비교 SQL 로 풀어준다 (`ma1.movie_id = ma2.movie_id`).
> - PITFALL: `ma2.actor.id <> :actorId` 를 빼면 결과에 본인이 포함된다 (자기 자신과 같은 영화에 있으니까).
> - PITFALL: `DISTINCT` 가 없으면 A 와 B 가 영화 2편에 함께 출연했을 때 B 가 2번 반환된다.

**테스트로 의미 확인 — `MovieActorRepositoryTest`:**

```java
@Test
@DisplayName("함께 출연한 동료 배우: 본인은 제외하고 중복 없이 반환된다")
void findCoActors() {
  List<Actor> coActors = movieActorRepository.findCoActors(10L);

  assertThat(coActors).extracting(Actor::getName)
      .containsExactlyInAnyOrder("Bob", "Carol");
  assertThat(coActors).extracting(Actor::getId).doesNotContain(10L);
}
```

---

### 3-3. 공동 출연작 — GROUP BY + HAVING COUNT(DISTINCT) (25분, 핵심)

**문제 정의:** "배우 A, B, C 가 **모두 함께** 출연한 영화는?"

학생들에게 사고 과정을 유도한다:

> "어떤 영화 한 편을 고르고, 그 영화에 A 가 출연하고 B 도 출연하고 C 도 출연하는지 확인하면 된다."

→ DB 적 표현: 그 영화의 movie_actor 행 중 `actor_id IN (A, B, C)` 인 것의 수가 정확히 3이면 모두 출연한 것.

**칠판:**

```
movie_actor 테이블 (배우 IN [A, B, C] 필터)

영화1: A         → distinct actor 수 = 1  ✗
영화2: A, B      → distinct actor 수 = 2  ✗
영화3: A, B, C   → distinct actor 수 = 3  ✓  ← 답!
영화4: B, C      → distinct actor 수 = 2  ✗
```

**JPQL (실제 코드):**

```java
@Query("SELECT ma.movie.id FROM MovieActor ma "
    + "WHERE ma.actor.id IN :actorIds "
    + "GROUP BY ma.movie.id "
    + "HAVING COUNT(DISTINCT ma.actor.id) = :count")
List<Long> findSharedMovieIds(@Param("actorIds") List<Long> actorIds,
    @Param("count") long count);
```

**한 줄씩:**

| 줄 | 의미 |
|----|------|
| `WHERE ma.actor.id IN :actorIds` | 후보 배우들의 출연 행만 추림 |
| `GROUP BY ma.movie.id` | 영화별로 묶음 |
| `HAVING COUNT(DISTINCT ma.actor.id) = :count` | 그룹에 들어온 distinct 배우 수가 입력 배우 수와 같으면 "교집합 조건 만족" |

> **강의 포인트** (오늘 가장 중요한 학습 포인트)
> - WHY: 관계대수의 "교집합" 을 SQL/JPQL 로 표현하는 가장 표준적인 패턴. 추천, 권한, 태그 매칭 등 모든 "여러 조건을 모두 만족하는 것 찾기" 에 동일하게 응용된다.
> - WHAT: `COUNT(DISTINCT)` 가 핵심. 만약 같은 배우가 한 영화에서 1인 2역으로 두 번 들어왔어도 `DISTINCT` 가 1로 정정해줌.
> - PITFALL: `HAVING COUNT(*) = :count` 로 쓰면 1인 2역 케이스에서 오탐(같은 사람을 2번 센다). 반드시 `COUNT(DISTINCT ma.actor.id)`.
> - PITFALL: 호출 쪽에서 `actorIds` 의 중복을 제거하지 않으면 `:count` 가 실제 distinct 수보다 크게 되어 결과가 항상 빈 리스트. `MovieService` 에서 `.distinct().toList()` 를 먼저 거치는 이유.

**Service 두 단계 쿼리 (MovieService.findSharedMovies):**

```java
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

**그리고 MovieRepository 의 보조 쿼리:**

```java
@Query("SELECT DISTINCT m FROM Movie m "
    + "LEFT JOIN FETCH m.movieGenres mg "
    + "LEFT JOIN FETCH mg.genre "
    + "WHERE m.id IN :ids "
    + "ORDER BY m.popularity DESC")
List<Movie> findAllByIdInWithGenres(@Param("ids") List<Long> ids);
```

> **강의 포인트**
> - WHY: 1단계에서 교집합 movie id 만 구하고, 2단계에서 그 id 들로 장르까지 fetch join. 한 쿼리로 합칠 수도 있지만 가독성·성능 면에서 분리가 더 깔끔하다.
> - PITFALL: 1단계 결과가 빈 리스트인데 2단계에서 `IN ()` 쿼리를 날리면 일부 DB 에서 syntax error. `if (movieIds.isEmpty())` 가드 필수.

---

## Session 4. 랭킹 쿼리, REST 라우팅, 슬라이스 테스트 (60분)

### 4-1. 최다 출연 배우 — Pageable + Object[] 프로젝션 (15분)

```java
// MovieActorRepository.java
@Query("SELECT ma.actor, COUNT(ma) FROM MovieActor ma "
    + "GROUP BY ma.actor "
    + "ORDER BY COUNT(ma) DESC")
List<Object[]> findTopActorsByMovieCount(Pageable pageable);
```

**Service 의 결과 변환:**

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

> **강의 포인트**
> - WHY: SELECT 절에 엔티티 1개 + 집계함수가 섞이면 record/DTO Projection 으로 한 번에 받기 까다로움. `Object[]` 가 가장 단순. 그리고 Service 에서 깔끔한 DTO 로 변환.
> - WHAT: `Pageable` 을 인자로 받으면 JPQL 에 `LIMIT` 가 자동으로 붙는다. `PageRequest.of(0, 10)` 이면 상위 10건.
> - WHAT: `((Number) row[1]).longValue()` — `COUNT(...)` 의 반환 타입은 DB/dialect 에 따라 `Long`/`BigInteger` 다양. `Number` 로 받아 `longValue()` 가 안전.
> - PITFALL: `Math.max(1, limit)` — `limit=0` 이 넘어오면 `PageRequest` 가 `IllegalArgumentException` 던진다. 방어 코드.

---

### 4-2. REST 라우팅 함정 — `/top` vs `/{id}` 순서 (10분)

`ActorController.java`:

```java
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

  @GetMapping("/{id}/movies")
  public ResponseEntity<List<FilmographyDto>> movies(@PathVariable("id") Long id) {
    List<FilmographyDto> result = actorService.findFilmography(id);
    if (result.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(result);
  }

  @GetMapping("/{id}/co-actors")
  public List<ActorDto> coActors(@PathVariable("id") Long id) {
    return actorService.findCoActors(id);
  }
}
```

> **강의 포인트** (실무에서 정말 자주 실수)
> - WHY: Spring MVC 는 핸들러 매핑에서 정적 경로(`/top`)와 변수 경로(`/{id}`)의 우선순위를 자동으로 분류해 정적이 먼저 매칭되도록 한다 — 하지만 **이는 동일 어노테이션 안에서의 얘기**고, 코드 가독성과 안전성을 위해 **개발자가 정적 경로를 위에 두는 컨벤션**이 표준이다.
> - PITFALL: 만약 `/{id}` 가 `Long id` 가 아니라 `String id` 였다면, `/top` 호출 시 `"top"` 이 경로변수로 잡혀 의도치 않게 detail 핸들러로 흘러갈 수 있다. `Long` 타입이라 `NumberFormatException` 으로 막혀 결과적으로 400 이 나오지만, 의도와 다른 동작.
> - HOW: 라우트 정의 순서 = 정적 경로 → 경로변수 순서. 이 컨벤션을 지키면 사고가 없다.

---

### 4-3. MovieController — 출연진 엔드포인트 (10분)

`MovieController.java` 의 신규 메서드들:

```java
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

> **강의 포인트**
> - WHY: `/{id}/actors`, `/{id}/credits/sync` — 서브리소스 라우팅. "영화 안의 출연진" 이라는 의미를 URL 만 봐도 알 수 있다.
> - WHAT: `@RequestParam("actorId") List<Long>` — 같은 이름의 쿼리 파라미터를 반복하면 자동으로 리스트로 매핑. `?actorId=1&actorId=2` 형태.
> - PITFALL: 일부 클라이언트는 `?actorId=1,2` 콤마 구분을 보내기도 한다. 그건 별도 컨버터가 필요. 표준은 반복 파라미터.
> - PITFALL: `/shared` 도 `/{id}` 와 충돌 위험이 있는 정적 경로. `MovieController` 는 `@DeleteMapping("/{id}")` 가 있으므로 메서드별로 분리되어 사실상 안전하지만, 동일 메서드(GET) 안에서도 늘 정적 경로 우선 원칙을 지킨다.

---

### 4-4. 슬라이스 테스트 — @DataJpaTest + H2 (15분)

`MovieActorRepositoryTest.java`:

```java
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

  // ... setUp() 에서 Actor 3명 + Movie 3편 + MovieActor 5건 영속화

  @Test
  @DisplayName("함께 출연한 영화: 전달한 배우가 모두 출연한 영화만 교집합으로 반환된다")
  void findSharedMovieIds() {
    assertThat(movieActorRepository.findSharedMovieIds(List.of(10L, 20L), 2))
        .containsExactly(1L);  // A1+A2 동시 출연은 M1
    assertThat(movieActorRepository.findSharedMovieIds(List.of(10L, 30L), 2))
        .containsExactly(2L);  // A1+A3 동시 출연은 M2
    assertThat(movieActorRepository.findSharedMovieIds(List.of(20L, 30L), 2))
        .isEmpty();            // A2+A3 함께 출연한 영화 없음
  }
}
```

**왜 `@SpringBootTest` 가 아니라 `@DataJpaTest` 인가:**

| 항목 | `@SpringBootTest` | `@DataJpaTest` |
|------|-------------------|----------------|
| 로딩 범위 | 전체 컨텍스트 | JPA 관련 빈만 |
| DB | application.yaml (MySQL) | embedded (기본 H2) |
| TMDB 토큰 | 필요 | 불필요 |
| 트랜잭션 | 기본 없음 | 자동 rollback |
| 속도 | 느림 | 빠름 |
| 용도 | E2E, 통합 | Repository 단위 |

> **강의 포인트**
> - WHY: 리파지토리 쿼리만 검증하면 되는 시점에 전체 컨텍스트를 띄울 필요 없음. CI 환경에서 MySQL 띄우고 TMDB 토큰 주입하는 부담을 슬라이스 테스트로 제거.
> - WHAT: `MODE=MySQL` — H2 가 MySQL 방언을 흉내내게 한다. JPQL 은 DB 독립적이지만 ddl-auto 가 만드는 DDL/제약명 등에서 차이 줄이기 위함.
> - WHAT: `@AutoConfigureTestDatabase(replace = ANY)` — `application.yaml` 의 MySQL 설정을 강제로 무시하고 위 `@TestPropertySource` 로 덮어쓰기.
> - PITFALL: `@DataJpaTest` 는 기본적으로 트랜잭션 안에서 실행되고 끝나면 롤백한다. 그래서 `em.flush()` + `em.clear()` 로 1차 캐시를 비워야 실제 SELECT 쿼리가 발생함을 검증할 수 있다.
> - PITFALL: 처음엔 `@SpringBootTest` 로 시도하면 MySQL 미기동 또는 `tmdb.bearer-token` 미설정으로 실패한다. 학생들이 직접 한 번 실패시키고 슬라이스 테스트로 전환하는 흐름이 교육적.

---

### 4-5. 전체 API 정리 + 다음 차시 예고 (10분)

**Day 3 신규 API:**

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | /api/movies/popular/{id}/credits/sync | TMDB credits 동기화 |
| GET | /api/movies/popular/{id}/actors | 영화별 출연진 (castOrder 순) |
| GET | /api/movies/popular/{id}/actors/count | 영화별 출연진 수 |
| GET | /api/movies/popular/shared?actorId=1&actorId=2 | 공동 출연작 (교집합) |
| GET | /api/actors | 전체 배우 |
| GET | /api/actors?name=... | 배우 이름 검색 |
| GET | /api/actors/top?limit=10 | 최다 출연 배우 랭킹 |
| GET | /api/actors/{id} | 배우 단건 |
| GET | /api/actors/{id}/movies | 배우 필모그래피 (인기순) |
| GET | /api/actors/{id}/co-actors | 동료 배우 (self-join) |

**3일 누적 요약 판서:**

```
Day 1 — N:M 의 기본 설계
└── MovieGenre: 연결만 하는 중간 엔티티

Day 2 — JPQL 집계 + 문서화
└── GROUP BY, COUNT, DTO Projection, OpenAPI

Day 3 — N:M 심화: "관계 속성" 을 가진 중간 엔티티
├── MovieActor: characterName, castOrder ← @ManyToMany 불가
├── 멱등 동기화 3단계: actor upsert → mapping delete → mapping insert
├── JPQL self-join (theta join) — 동료 배우
├── GROUP BY + HAVING COUNT(DISTINCT) — 교집합 = 공동 출연작
├── Pageable + Object[] — 랭킹 쿼리
└── @DataJpaTest + H2(MODE=MySQL) — 슬라이스 테스트
```

> **다음 차시 예고**
>
> "지금 `/api/actors/{id}` 에서 존재하지 않는 id 를 넘기면 404 는 반환하지만, 본문은 비어있죠.
> 그리고 `co-actors` 에서 id 가 없는 경우엔 그냥 빈 리스트가 나옵니다 — 잘못된 요청과
> '없음' 을 구분하지 못합니다.
> 다음 시간에는 `@RestControllerAdvice` 로 통일된 에러 응답 포맷을 만들고,
> 입력값 검증(`@Valid` + Bean Validation)을 도입합니다."

---

## 트러블슈팅 가이드 — Day 3

| 증상 | 원인 | 해결 방법 |
|------|------|-----------|
| `SQLIntegrityConstraintViolationException ... movie_actor.UK` | 재동기화 시 `deleteByMovieId` 누락 | `syncCredits` 의 step 2 (deleteByMovieId) 실행 확인 |
| `IllegalStateException: Duplicate key` | `toMap` 의 merge function 누락 | `(a, b) -> a` 또는 `(a, b) -> ... ? a : b` 추가 |
| `InvalidDataAccessApiUsageException: Not supported for DML operations` | DELETE/UPDATE 쿼리에 `@Modifying` 누락 | 리파지토리 메서드에 `@Modifying` 추가 |
| `findCoActors` 결과에 본인 포함 | `ma2.actor.id <> :actorId` 조건 누락 | WHERE 조건 추가 |
| `findCoActors` 결과 중복 | `DISTINCT` 누락 | `SELECT DISTINCT ma2.actor` |
| `findSharedMovieIds` 항상 빈 리스트 | `actorIds` 중복 미제거로 `:count` 가 distinct 수보다 큼 | 호출 전 `.distinct().toList()` |
| `findSharedMovieIds` 가 1인 2역에서 오탐 | `HAVING COUNT(*)` 사용 | `HAVING COUNT(DISTINCT ma.actor.id)` 로 교체 |
| `Object[] row[1]` 캐스팅 `ClassCastException` | dialect 별 COUNT 반환 타입 차이 | `((Number) row[1]).longValue()` 로 안전 변환 |
| `/api/actors/top` 호출 시 400 또는 detail 로 흘러감 | `/top` 이 `/{id}` 아래에 선언됨 | `/top` 핸들러를 `/{id}` 위로 이동 |
| `@DataJpaTest` 가 MySQL 에 연결을 시도 | `@AutoConfigureTestDatabase(replace = ANY)` 누락 | 어노테이션 추가 |
| `em.flush/clear` 없이 캐시된 객체로 검증되는 문제 | 1차 캐시 미정리 | `setUp` 마지막에 `em.flush(); em.clear();` |

## 자주 나오는 질문

| 질문 | 답변 요약 | 심화 설명 |
|------|-----------|-----------|
| Actor 와 MovieActor 양방향으로 연결해야 하나요? | 필요 없습니다 | 양방향은 편의 메서드와 동기화 부담을 늘립니다. 배우→영화 검색은 `MovieActorRepository.findByActorIdWithMovie` 로 충분. |
| 왜 `Actor` 에 `cascade` 가 없나요? | 배우는 독립 도메인이기 때문 | 영화 1편이 삭제됐다고 배우 정보가 사라지면 다른 영화에서 그 배우 참조가 깨집니다. 애그리거트 경계의 외부. |
| `@ManyToMany` 에 `@JoinTable` 로 컬럼을 못 추가하나요? | 못 합니다 | JPA 스펙상 `@JoinTable` 은 두 FK 외에는 어떤 컬럼도 추가할 수 없습니다. 그래서 중간 엔티티가 필요합니다. |
| `toMap` 대신 `groupingBy` 도 되나요? | 다른 용도입니다 | `toMap` 은 key → 1개 value 매핑 (중복 시 merge), `groupingBy` 는 key → List 매핑. 여기선 중복 제거가 목적이라 `toMap`. |
| 슬라이스 테스트와 통합 테스트 둘 다 필요한가요? | 권장 — 역할 분담 | 슬라이스(@DataJpaTest)로 쿼리 정확성을 빠르게 검증, 통합(@SpringBootTest)으로 트랜잭션·실제 API 흐름 검증. |
| `findSharedMovies` 를 한 쿼리로 줄일 수 있나요? | 가능하지만 가독성 손해 | 서브쿼리로 영화 id 를 구하고 그 결과를 main query 에 IN 으로 넣을 수 있으나, 두 단계로 분리한 현재 코드가 디버깅·재사용에 유리. |
| TMDB 의 crew(감독)도 같이 저장하려면? | 별도 엔티티 `MovieCrew` 권장 | 출연(cast)과 제작진(crew)은 의미가 다른 도메인. 한 테이블에 합치면 SELECT 마다 type 필터가 들어가 비효율. |
| H2 의 `MODE=MySQL` 만으로 충분한가요? | 대부분의 JPQL 검증엔 충분 | 다만 MySQL 특유의 함수(예: `GROUP_CONCAT`)나 윈도우 함수 동작은 100% 동일하지 않으므로, 그런 쿼리는 통합 테스트에서 추가 검증. |

---

## 실습 문제 (강사용 해설 포함)

### 문제 1 — `/api/actors/{id}/co-actors` 의 결과 수 제한

**요구사항:** `?limit=5` 쿼리 파라미터로 상위 5명만 반환하도록 확장하라. 정렬은 기존과 동일하게 `popularity DESC`.

**해설:**

```java
// MovieActorRepository.java 에 추가
@Query("SELECT DISTINCT ma2.actor FROM MovieActor ma1, MovieActor ma2 "
    + "WHERE ma1.movie = ma2.movie "
    + "AND ma1.actor.id = :actorId "
    + "AND ma2.actor.id <> :actorId "
    + "ORDER BY ma2.actor.popularity DESC")
List<Actor> findCoActors(@Param("actorId") Long actorId, Pageable pageable);

// ActorService 수정
@Transactional(readOnly = true)
public List<ActorDto> findCoActors(Long actorId, int limit) {
  return movieActorRepository.findCoActors(actorId, PageRequest.of(0, Math.max(1, limit)))
      .stream().map(this::toDto).toList();
}

// ActorController 수정
@GetMapping("/{id}/co-actors")
public List<ActorDto> coActors(@PathVariable("id") Long id,
    @RequestParam(defaultValue = "10") int limit) {
  return actorService.findCoActors(id, limit);
}
```

> 강의 포인트: `Pageable` 은 어떤 쿼리에도 LIMIT 를 자동으로 붙여준다는 점을 다시 한 번 강조.

---

### 문제 2 — 배역명(character) 검색 API

**요구사항:** `GET /api/actors/search-by-character?keyword=Tony` — 배역명에 키워드를 포함한 출연 정보(배우 + 영화 + 배역) 를 반환하라.

**해설:**

```java
// MovieActorRepository.java
@Query("SELECT ma FROM MovieActor ma "
    + "JOIN FETCH ma.actor "
    + "JOIN FETCH ma.movie "
    + "WHERE LOWER(ma.characterName) LIKE LOWER(CONCAT('%', :keyword, '%')) "
    + "ORDER BY ma.movie.popularity DESC")
List<MovieActor> searchByCharacter(@Param("keyword") String keyword);
```

→ 결과를 어떤 DTO 에 담을지가 토론 포인트. 새로운 `CharacterSearchDto(movieTitle, actorName, character)` 를 만들면 깔끔.

> 강의 포인트: 여러 엔티티의 정보를 함께 보여줄 때 새 DTO 를 주저 없이 만드는 습관 강조.

---

### 문제 3 — 멱등성 깨기 실험

**요구사항:** `ActorService.syncCredits` 에서 `movieActorRepository.deleteByMovieId(movieId);` 줄을 주석 처리한 뒤, 같은 영화로 동기화를 2회 호출하여 실패를 재현하라. 이후 다시 복구하라.

**기대 에러:** `SQLIntegrityConstraintViolationException: Duplicate entry '1226863-1234' for key 'movie_actor.UK...`

> 강의 포인트: 학생들이 직접 실패를 보면 unique 제약과 멱등 동기화의 의미가 체화된다. 복구 후 다시 두 번 호출해 멱등성이 회복되는 것을 보여준다.

---

### 문제 4 — `findSharedMovieIds` 에 `HAVING COUNT(*)` 를 쓰면 어떤 케이스가 깨지는가

**요구사항:** 쿼리에서 `COUNT(DISTINCT ma.actor.id)` 를 `COUNT(*)` 로 바꾼 뒤, 다음 시나리오에서 결과가 어떻게 달라지는지 슬라이스 테스트로 검증하라.

- 배우 A 가 영화 M 에서 1인 2역으로 등장 (movie_actor 에 같은 actor_id 가 두 번)
- 입력: `findSharedMovieIds([A], 1)` — 1명만 함께 출연한 영화

**기대 결과:**
- `COUNT(*) = 1` 조건: 1인 2역 영화는 카운트가 2 가 되어 결과에서 누락됨
- `COUNT(DISTINCT ma.actor.id) = 1`: 정상적으로 포함됨

> 강의 포인트: 현재 동기화 코드가 `deleteByMovieId` + dedup 으로 1인 2역을 한 row 로 합치지만, **그렇지 않은 시스템이라면** 이 차이가 실제 버그가 된다. 방어적 쿼리의 중요성을 보여준다.
