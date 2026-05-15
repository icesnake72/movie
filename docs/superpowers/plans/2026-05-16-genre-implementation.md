# Genre 테이블 구현 계획 (교육용)

> **학생 여러분:** 이 계획은 작은 단계로 나누어져 있습니다. 각 단계를 차근차근 따라하면서 이해하고 진행하세요.

**목표:** TMDB에서 장르 데이터를 받아 데이터베이스에 저장하고, 영화 조회 시 장르 정보를 함께 보여주기

**구조:** 
- `Genre` 테이블: 장르 ID와 이름 저장
- `GenreService`: TMDB API에서 장르 데이터 가져오기
- `GenreController`: 사용자가 호출할 REST API 엔드포인트
- `genre_movie` 테이블: 향후 영화-장르 관계 표현용 (지금은 비어있음)

**사용 기술:** Spring Boot, Spring Data JPA, TMDB API

---

## 파일 구조 (어떤 파일을 만들고 수정할 것인가)

**새로 만들 파일:**
```
src/main/java/com/example/movie/genre/
├── Genre.java                    ← 장르 정보를 저장하는 Entity (테이블과 대응)
├── GenreRepository.java          ← 데이터베이스에서 장르 데이터를 읽고 쓰기
├── GenreService.java             ← TMDB에서 장르를 받아 DB에 저장하는 비즈니스 로직
├── GenreController.java          ← 사용자 요청을 처리하는 API 엔드포인트
└── dto/
    ├── TmdbGenreDto.java         ← TMDB에서 받은 장르 정보 (ID, 이름)
    └── TmdbGenreResponse.java    ← TMDB가 보내주는 전체 응답 (여러 장르들의 리스트)

src/test/java/com/example/movie/genre/
└── GenreIntegrationTest.java     ← API가 제대로 작동하는지 테스트
```

**수정할 파일:**
```
src/main/java/com/example/movie/movie/
├── MovieService.java             ← 영화 조회 시 장르 정보도 함께 넣기
└── dto/TmdbMovieDto.java         ← 영화 응답에 장르 정보 추가
```

---

## Task 1: Genre Entity 만들기

**설명:** Entity는 데이터베이스 테이블을 Java 클래스로 표현한 것입니다. 
- 테이블: `genre` (id, name 두 개의 컬럼)
- 클래스: `Genre` (id, name 두 개의 필드)

**파일 생성:** `src/main/java/com/example/movie/genre/Genre.java`

- [ ] **Step 1: Genre.java 파일 생성하기**

```java
package com.example.movie.genre;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// @Entity: 이 클래스가 데이터베이스 테이블과 대응됨을 선언
// @Table(name = "genre"): 데이터베이스 테이블 이름은 'genre'
@Entity
@Table(name = "genre")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Genre {

  // @Id: 이 필드가 테이블의 주키(고유 식별자)
  @Id
  @Column(name = "id", nullable = false)
  private Long id;  // TMDB에서 받은 장르 ID (예: 28)

  // 장르 이름 (예: "Action", "Comedy")
  @Column(name = "name", length = 100, nullable = false)
  private String name;
}
```

**설명:**
- `@Entity`: JPA가 이 클래스를 데이터베이스 테이블로 인식
- `@Id`: 주키 선언 (각 장르의 고유번호)
- `@Column`: 데이터베이스 컬럼 설정
  - `length = 100`: 최대 100글자 저장 가능
  - `nullable = false`: 반드시 값이 있어야 함 (빈 값 불가)
- Lombok 어노테이션들: 보일러플레이트 코드 자동 생성

- [ ] **Step 2: GenreRepository.java 생성하기**

```java
package com.example.movie.genre;

import org.springframework.data.jpa.repository.JpaRepository;

// JpaRepository를 상속받으면 기본 CRUD 기능 자동 제공
// - save(): 데이터 저장
// - findById(): ID로 검색
// - findAll(): 모든 데이터 조회
// - deleteById(): ID로 삭제
public interface GenreRepository extends JpaRepository<Genre, Long> {
  // Genre: 이 Repository가 관리하는 Entity
  // Long: Genre의 ID 타입
}
```

**설명:**
- Repository는 데이터베이스와 통신하는 인터페이스
- JpaRepository를 상속하면 별도 구현 없이도 자동으로 데이터베이스 기능 사용 가능

- [ ] **Step 3: 커밋하기**

```bash
git add src/main/java/com/example/movie/genre/Genre.java \
        src/main/java/com/example/movie/genre/GenreRepository.java
git commit -m "feat: Genre 엔티티와 Repository 생성"
```

---

## Task 2: TMDB 장르 DTO 만들기

**설명:** DTO(Data Transfer Object)는 TMDB API에서 받은 JSON 데이터를 Java 객체로 변환하기 위한 클래스입니다.

**파일 생성:**
- `src/main/java/com/example/movie/genre/dto/TmdbGenreDto.java`
- `src/main/java/com/example/movie/genre/dto/TmdbGenreResponse.java`

- [ ] **Step 1: TmdbGenreDto.java 생성하기**

TMDB API의 응답 예시:
```json
{
  "genres": [
    {"id": 28, "name": "Action"},
    {"id": 35, "name": "Comedy"}
  ]
}
```

개별 장르를 표현하는 DTO:

```java
package com.example.movie.genre.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// TMDB에서 보내주는 하나의 장르 정보를 받기 위한 클래스
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TmdbGenreDto {

  private Long id;    // 장르 ID (예: 28)
  private String name; // 장르명 (예: "Action")
}
```

- [ ] **Step 2: TmdbGenreResponse.java 생성하기**

TMDB가 보내주는 전체 응답을 받기 위한 DTO:

```java
package com.example.movie.genre.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// TMDB의 장르 목록 응답을 받는 클래스
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TmdbGenreResponse {

  // TMDB가 보내주는 장르들의 리스트
  // 예: [{"id": 28, "name": "Action"}, {"id": 35, "name": "Comedy"}, ...]
  private List<TmdbGenreDto> genres;
}
```

- [ ] **Step 3: 커밋하기**

```bash
git add src/main/java/com/example/movie/genre/dto/
git commit -m "feat: TMDB 장르 DTO 추가"
```

---

## Task 3: GenreService 구현하기

**설명:** Service는 비즈니스 로직을 담당합니다. 
- TMDB API 호출
- 받은 데이터를 Genre 엔티티로 변환
- 데이터베이스에 저장

**파일 생성:** `src/main/java/com/example/movie/genre/GenreService.java`

- [ ] **Step 1: GenreService.java 생성하기**

```java
package com.example.movie.genre;

import com.example.movie.genre.dto.TmdbGenreResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

// @Service: 이 클래스가 비즈니스 로직을 담당함을 선언
@Service
@RequiredArgsConstructor
@Slf4j
public class GenreService {

  // RestClient: TMDB API를 호출하기 위한 HTTP 클라이언트
  private final RestClient tmdbRestClient;

  // GenreRepository: 데이터베이스에 저장하기 위한 Repository
  private final GenreRepository genreRepository;

  // @Transactional: 데이터베이스 트랜잭션 처리 (저장 중 에러 발생 시 모두 롤백)
  @Transactional
  public int syncGenres() {
    // TMDB API 호출: /genre/movie/list 엔드포인트
    // 이 엔드포인트는 모든 영화 장르 목록을 반환합니다
    TmdbGenreResponse response = tmdbRestClient.get()
        .uri(uriBuilder -> uriBuilder.path("/genre/movie/list")
            .build())
        .retrieve()
        .body(TmdbGenreResponse.class);

    // API 응답이 없거나 비어있으면 처리 중단
    if (response == null || response.getGenres() == null || response.getGenres().isEmpty()) {
      log.warn("TMDB에서 받은 장르 데이터가 비어있습니다");
      return 0;
    }

    // TMDB에서 받은 각 장르를 하나씩 처리
    response.getGenres().forEach(genreDto -> {
      // DTO를 Entity로 변환
      Genre genre = Genre.builder()
          .id(genreDto.getId())
          .name(genreDto.getName())
          .build();

      // 데이터베이스에 저장 (같은 ID 있으면 업데이트, 없으면 삽입)
      genreRepository.save(genre);
    });

    // 저장된 장르 개수 로깅
    int count = response.getGenres().size();
    log.info("장르 저장 완료: 총 {} 개", count);
    return count;
  }
}
```

**설명:**
- `syncGenres()`: TMDB에서 장르를 받아 데이터베이스에 저장하는 메서드
- RestClient를 사용해 HTTP GET 요청을 `/genre/movie/list`로 전송
- TMDB 응답을 `TmdbGenreResponse` 객체로 자동 변환 (Jackson의 JSON 매핑)
- 각 장르를 `Genre` 엔티티로 변환 후 저장
- `@Transactional`: 데이터베이스 저장 작업의 일관성 보장

- [ ] **Step 2: 커밋하기**

```bash
git add src/main/java/com/example/movie/genre/GenreService.java
git commit -m "feat: GenreService 구현 - TMDB 장르 동기화"
```

---

## Task 4: GenreController 구현하기

**설명:** Controller는 사용자(API 클라이언트)의 요청을 받아서 처리하고 응답하는 부분입니다.

**파일 생성:** `src/main/java/com/example/movie/genre/GenreController.java`

- [ ] **Step 1: GenreController.java 생성하기**

```java
package com.example.movie.genre;

import com.example.movie.genre.dto.TmdbGenreDto;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// @RestController: 이 클래스가 REST API 엔드포인트를 제공함
// @RequestMapping("/api/genres"): 모든 메서드의 기본 경로가 /api/genres
@RestController
@RequestMapping("/api/genres")
@RequiredArgsConstructor
public class GenreController {

  private final GenreService genreService;
  private final GenreRepository genreRepository;

  // 요청: POST /api/genres/sync
  // 기능: TMDB에서 장르 데이터를 받아 데이터베이스에 저장
  // 응답: {"synced": 저장된_장르_개수}
  @PostMapping("/sync")
  public ResponseEntity<Map<String, Object>> sync() {
    int synced = genreService.syncGenres();
    return ResponseEntity.ok(Map.of("synced", synced));
  }

  // 요청: GET /api/genres
  // 기능: 데이터베이스에 저장된 모든 장르 조회
  // 응답: [{"id": 28, "name": "Action"}, {"id": 35, "name": "Comedy"}, ...]
  @GetMapping
  public List<TmdbGenreDto> list() {
    return genreRepository.findAll().stream()
        // 각 Genre 엔티티를 TmdbGenreDto로 변환 (응답용)
        .map(g -> TmdbGenreDto.builder()
            .id(g.getId())
            .name(g.getName())
            .build())
        .toList();
  }
}
```

**설명:**
- `sync()`: TMDB에서 장르를 받아 저장하는 POST 엔드포인트
  - 사용 예: `curl -X POST http://localhost:9000/api/genres/sync`
  - 응답: `{"synced": 10}`

- `list()`: 저장된 장르 목록을 조회하는 GET 엔드포인트
  - 사용 예: `curl http://localhost:9000/api/genres`
  - 응답: 장르 목록 배열

- [ ] **Step 2: 커밋하기**

```bash
git add src/main/java/com/example/movie/genre/GenreController.java
git commit -m "feat: GenreController 추가 - 장르 동기화 및 조회 API"
```

---

## Task 5: TmdbMovieDto에 genres 필드 추가하기

**설명:** 영화를 조회할 때 장르 정보도 함께 보여주기 위해, 영화 응답 DTO에 genre 정보를 추가합니다.

**파일 수정:** `src/main/java/com/example/movie/movie/dto/TmdbMovieDto.java`

- [ ] **Step 1: TmdbMovieDto.java 수정하기**

**기존 코드:**
```java
// ... 기존 필드들 ...
@JsonProperty("vote_count")
@Column(name = "vote_count")
private Integer voteCount;
```

**수정할 부분: 클래스 끝에 다음을 추가**

```java
package com.example.movie.movie.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TmdbMovieDto {

  // ... 기존 필드들 (id, adult, title 등) ...

  @JsonProperty("vote_count")
  private Integer voteCount;

  // ===== 새로 추가하는 부분 =====
  
  // 이 영화가 속한 장르들
  // 예: [{"id": 28, "name": "Action"}, {"id": 12, "name": "Adventure"}]
  private List<GenreInfo> genres;

  // 장르 정보를 표현하는 내부 클래스
  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class GenreInfo {
    private Long id;      // 장르 ID
    private String name;  // 장르명
  }
}
```

**설명:**
- 영화 응답에 genres 필드 추가
- `GenreInfo` 내부 클래스: 장르 ID와 이름을 함께 표현
- 이렇게 하면 영화를 조회할 때 장르 정보도 함께 받을 수 있음

- [ ] **Step 2: 커밋하기**

```bash
git add src/main/java/com/example/movie/movie/dto/TmdbMovieDto.java
git commit -m "feat: TmdbMovieDto에 genres 필드 추가"
```

---

## Task 6: MovieService 수정 - 장르 정보 포함하기

**설명:** 영화 조회 시, 영화의 genre_ids로부터 실제 장르 정보(ID와 이름)를 데이터베이스에서 찾아서 응답에 포함시킵니다.

**파일 수정:** `src/main/java/com/example/movie/movie/MovieService.java`

- [ ] **Step 1: GenreRepository 주입 추가하기**

**기존 코드:**
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class MovieService {

  private final RestClient tmdbRestClient;
  private final MovieRepository movieRepository;
```

**수정:**
```java
package com.example.movie.movie;

import com.example.movie.genre.GenreRepository;
import com.example.movie.movie.dto.TmdbMovieDto;
import com.example.movie.movie.dto.TmdbPopularResponse;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class MovieService {

  private final RestClient tmdbRestClient;
  private final MovieRepository movieRepository;
  private final GenreRepository genreRepository;  // 추가: 장르 데이터 조회용

  // ... 나머지 코드는 그대로 ...
```

- [ ] **Step 2: toDto() 메서드 수정하기**

**기존 코드:**
```java
private TmdbMovieDto toDto(Movie m) {
  return new TmdbMovieDto(
      m.getId(),
      m.isAdult(),
      m.getBackdropPath(),
      parseGenreIds(m.getGenreIds()),
      m.getTitle(),
      // ... 나머지 필드들 ...
      m.getVoteCount());
}
```

**수정된 코드:**
```java
private TmdbMovieDto toDto(Movie m) {
  // 1단계: 저장된 genre_ids 문자열을 정수 리스트로 변환
  // 예: "[28, 12, 35]" → [28, 12, 35]
  List<Integer> genreIds = parseGenreIds(m.getGenreIds());

  // 2단계: 각 genre ID로 데이터베이스에서 장르 정보 조회
  // 그 결과를 GenreInfo 객체로 변환
  // 예: 28 → {"id": 28, "name": "Action"}
  List<TmdbMovieDto.GenreInfo> genres = genreIds.stream()
      // 각 genreId에 대해:
      .map(genreId -> genreRepository.findById(genreId.longValue())
          // 데이터베이스에서 찾은 Genre를 GenreInfo로 변환
          .map(g -> TmdbMovieDto.GenreInfo.builder()
              .id(g.getId())
              .name(g.getName())
              .build())
          // 못 찾으면 null 반환
          .orElse(null))
      // null인 항목 제거 (찾지 못한 장르)
      .filter(g -> g != null)
      .toList();

  // 3단계: 영화 정보와 장르 정보를 함께 반환
  return new TmdbMovieDto(
      m.getId(),
      m.isAdult(),
      m.getBackdropPath(),
      genreIds,  // 장르 ID 리스트
      m.getTitle(),
      m.getOriginalLanguage(),
      m.getOriginalTitle(),
      m.getOverview(),
      m.getPopularity(),
      m.getPosterPath(),
      m.getReleaseDate() == null ? null : m.getReleaseDate().toString(),
      m.isSoftcore(),
      m.isVideo(),
      m.getVoteAverage(),
      m.getVoteCount(),
      genres);  // 새로 추가: 장르 정보
}
```

**설명:**
- Stream의 map(), filter(), toList() 사용
- Optional.map()과 orElse()를 사용해 데이터베이스 조회 결과 처리
- null 체크로 조회 실패한 항목 제거

- [ ] **Step 3: 커밋하기**

```bash
git add src/main/java/com/example/movie/movie/MovieService.java
git commit -m "feat: MovieService - 영화 조회 시 장르 정보 포함"
```

---

## Task 7: 수동 테스트하기

**설명:** API가 제대로 작동하는지 직접 호출해서 확인합니다.

- [ ] **Step 1: 애플리케이션 시작**

```bash
./gradlew bootRun
```

포트 9000에서 서버가 시작됩니다.

- [ ] **Step 2: 장르 동기화 테스트**

```bash
curl -X POST "http://localhost:9000/api/genres/sync"
```

**예상 응답:**
```json
{"synced": 10}
```

**설명:** TMDB에서 10개의 장르를 받아 데이터베이스에 저장했다는 의미

- [ ] **Step 3: 저장된 장르 목록 조회**

```bash
curl "http://localhost:9000/api/genres"
```

**예상 응답:**
```json
[
  {"id": 28, "name": "Action"},
  {"id": 12, "name": "Adventure"},
  {"id": 16, "name": "Animation"},
  ...
]
```

- [ ] **Step 4: 영화 목록 조회 (장르 포함)**

기존에 영화가 저장되어 있다면:
```bash
curl "http://localhost:9000/api/movies/popular"
```

**예상 응답 (일부):**
```json
[
  {
    "id": 1226863,
    "title": "Deadpool & Wolverine",
    "genreIds": [28, 35, 12],
    "genres": [
      {"id": 28, "name": "Action"},
      {"id": 35, "name": "Comedy"},
      {"id": 12, "name": "Adventure"}
    ],
    ...
  },
  ...
]
```

**설명:** 이제 영화 조회 시 genreIds와 함께 genres(장르 정보 객체)도 함께 반환됨

- [ ] **Step 5: 완전한 흐름 테스트**

```bash
# 1. 영화 동기화
curl -X POST "http://localhost:9000/api/movies/popular/sync?page=1"

# 2. 장르 동기화
curl -X POST "http://localhost:9000/api/genres/sync"

# 3. 영화 목록 조회 (장르 정보 포함되어야 함)
curl "http://localhost:9000/api/movies/popular"
```

---

## Task 8: 최종 커밋 및 정리

- [ ] **Step 1: 모든 변경사항 확인**

```bash
git status
```

- [ ] **Step 2: 로그 확인**

```bash
git log --oneline -10
```

**예상 출력:**
```
6789abc feat: MovieService - 영화 조회 시 장르 정보 포함
5678def feat: TmdbMovieDto에 genres 필드 추가
4567cde feat: GenreController 추가 - 장르 동기화 및 조회 API
3456bcd feat: GenreService 구현 - TMDB 장르 동기화
2345abc feat: TMDB 장르 DTO 추가
1234def feat: Genre 엔티티와 Repository 생성
```

- [ ] **Step 3: 최종 확인 메시지**

모든 커밋이 완료되었습니다! 
다음 학습 주제: Movie-Genre 관계를 ManyToOne으로 설정하기 (향후 단원)

---

## 학습 포인트 정리

### 배운 개념들:
1. **Entity**: 데이터베이스 테이블을 Java 클래스로 표현
2. **Repository**: 데이터베이스와 통신하는 인터페이스
3. **DTO**: 외부 API 또는 클라이언트와 데이터 교환용 객체
4. **Service**: 비즈니스 로직을 담당하는 계층
5. **Controller**: 사용자 요청을 받아 처리하는 계층
6. **RestClient**: 외부 API 호출
7. **Stream API**: 컬렉션 데이터 변환 및 필터링

### 아키텍처 흐름:
```
사용자 요청
    ↓
Controller (요청 받기)
    ↓
Service (비즈니스 로직)
    ↓
Repository (데이터베이스 접근)
    ↓
Entity (데이터베이스 테이블)
```

### 다음 단계 (향후 학습):
- Movie-Genre 관계: @ManyToOne / @OneToMany 매핑
- genre_movie 테이블 활용
- JOIN 쿼리를 통한 효율적인 데이터 조회
