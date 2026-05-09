# movie

TMDB 인기 영화 목록을 가져와 MySQL 단일 테이블에 저장하는 Spring Boot 백엔드 실습 프로젝트.

수업 초반 단계라 단일 테이블 + 단순한 3개 엔드포인트만 갖춘 최소 구성입니다. 이후 단원에서 장르 / 평점 등을 별도 테이블로 분리하여 조인 학습으로 확장합니다.

## 스택

- Java 21, Spring Boot 3.5.x, Gradle
- Spring Web, Spring Data JPA, Spring Validation
- MySQL (`localhost:3306/movie`)
- Lombok, Jackson, RestClient

## 사전 준비

### 1. MySQL DB 생성

```sql
CREATE DATABASE IF NOT EXISTS movie DEFAULT CHARACTER SET utf8mb4;
```

기본 접속 정보는 `application.yaml` 의 다음 값과 일치해야 합니다 (학습용 기본값).

```
url      : jdbc:mysql://localhost:3306/movie
username : root
password : 1234
```

테이블(`popular_movie`)은 `spring.jpa.hibernate.ddl-auto: create` 로 앱 기동 시 자동 생성됩니다.

### 2. TMDB Bearer 토큰 발급 및 환경변수 설정

[https://www.themoviedb.org/](https://www.themoviedb.org/) 에서 계정 생성 → Settings → API → **API Read Access Token** 발급 후 환경변수로 주입합니다.

```bash
export TMDB_BEARER_TOKEN="<발급받은_API_Read_Access_Token>"
```

설정하지 않으면 기본값 `CHANGE_ME` 가 들어가 TMDB 호출이 401 로 실패합니다.

## 실행

```bash
./gradlew bootRun
```

기본 포트는 `9000` 입니다 (`application.yaml` 의 `server.port` 에서 변경 가능).

## 엔드포인트

| 메서드 | 경로 | 설명 |
|---|---|---|
| `POST` | `/api/movies/popular/sync?page=1` | TMDB 인기 영화 목록을 가져와 DB 에 저장(upsert) |
| `GET`  | `/api/movies/popular`              | 저장된 영화 전체를 JSON 배열로 조회 |
| `DELETE` | `/api/movies/popular/{id}`       | 특정 영화를 id 로 삭제 (없는 id 는 조용히 무시) |

### 호출 예시

```bash
# 동기화
curl -X POST "http://localhost:9000/api/movies/popular/sync?page=1"
# → {"page":1,"saved":20}

# 조회
curl http://localhost:9000/api/movies/popular

# 삭제
curl -X DELETE http://localhost:9000/api/movies/popular/1226863
# → 204 No Content
```

## 단일 테이블 설계

`popular_movie` 한 테이블에 모든 필드를 담습니다. `genre_ids` 는 `List<Integer>.toString()` 결과 (`"[10751, 35, 12]"`) 그대로 `VARCHAR(500)` 에 보관하고, 응답 시 `MovieService.parseGenreIds()` 로 다시 `List<Integer>` 로 복원합니다.

이후 단원에서 `genre`, `movie_genre` 테이블로 분리하면서 `@OneToMany` / `@ManyToMany` 매핑 학습으로 확장 예정입니다.

## 디렉토리 구조

```
src/main/java/com/example/movie
├── MovieApplication.java
├── config/RestClientConfig.java
└── movie/
    ├── Movie.java                Entity
    ├── MovieRepository.java      JpaRepository
    ├── MovieService.java         TMDB 호출 / DTO ↔ Entity 변환
    ├── MovieController.java      REST 엔드포인트
    └── dto/
        ├── TmdbPopularResponse.java
        └── TmdbMovieDto.java
```
