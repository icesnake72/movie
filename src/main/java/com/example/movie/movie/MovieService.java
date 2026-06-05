package com.example.movie.movie;

import com.example.movie.genre.Genre;
import com.example.movie.genre.GenreRepository;
import com.example.movie.movie.dto.TmdbMovieDto;
import com.example.movie.movie.dto.TmdbPopularResponse;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
  private final MovieGenreRepository movieGenreRepository;
  private final MovieActorRepository movieActorRepository;
  private final GenreRepository genreRepository;

  @Value("${tmdb.default-language}")
  private String defaultLanguage;

  @Transactional
  public int syncPopularMovies(int page) {
    TmdbPopularResponse response = tmdbRestClient.get()
        .uri(uriBuilder -> uriBuilder.path("/movie/popular")
            .queryParam("language", defaultLanguage)
            .queryParam("page", page)
            .build())
        .retrieve()
        .body(TmdbPopularResponse.class);

    if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
      log.warn("TMDB popular 응답이 비어있음. page={}", page);
      return 0;
    }

    Map<Long, Genre> genreMap = genreRepository.findAll().stream()
        .collect(Collectors.toMap(Genre::getId, g -> g));

    List<Movie> movies = response.getResults().stream()
        .map(dto -> toEntity(dto, genreMap))
        .toList();

    movieRepository.saveAll(movies);
    log.info("popular movie 저장 완료: page={}, count={}", page, movies.size());
    return movies.size();
  }

  @Transactional(readOnly = true)
  public List<TmdbMovieDto> findAll() {
    return movieRepository.findAllWithGenres().stream()
        .map(this::toDto)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<TmdbMovieDto> findByGenreId(Long genreId) {
    return movieRepository.findAllByGenreId(genreId).stream()
        .map(this::toDto).toList();
  }

  @Transactional(readOnly = true)
  public List<TmdbMovieDto> findByTitle(String title) {
    return movieRepository.findAllByTitleContaining(title).stream()
        .map(this::toDto).toList();
  }

  @Transactional(readOnly = true)
  public List<TmdbMovieDto> findAllSorted(String sort) {
    List<Movie> movies = switch (sort) {
      case "popularity"  -> movieRepository.findAllOrderByPopularityDesc();
      case "voteAverage" -> movieRepository.findAllOrderByVoteAverageDesc();
      default            -> movieRepository.findAllWithGenres();
    };
    return movies.stream().map(this::toDto).toList();
  }

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

  @Transactional
  public void deleteById(Long id) {
    movieRepository.deleteById(id);
    log.info("movie 삭제 요청: id={}", id);
  }

  /**
   * 주어진 배우들이 모두 함께 출연한 영화 목록.
   * 1) 중간 테이블에서 GROUP BY + HAVING 으로 교집합 movie id 를 구하고
   * 2) 그 id 들로 장르까지 fetch join 하여 DTO 로 변환한다.
   */
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

  private Movie toEntity(TmdbMovieDto dto, Map<Long, Genre> genreMap) {
    Movie movie = Movie.builder()
        .id(dto.getId())
        .adult(dto.isAdult())
        .backdropPath(dto.getBackdropPath())
        .title(dto.getTitle())
        .originalLanguage(dto.getOriginalLanguage())
        .originalTitle(dto.getOriginalTitle())
        .overview(dto.getOverview())
        .popularity(dto.getPopularity())
        .posterPath(dto.getPosterPath())
        .releaseDate(parseDate(dto.getReleaseDate()))
        .softcore(dto.isSoftcore())
        .video(dto.isVideo())
        .voteAverage(dto.getVoteAverage())
        .voteCount(dto.getVoteCount())
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

  private TmdbMovieDto toDto(Movie m) {
    List<TmdbMovieDto.GenreInfo> genres = m.getMovieGenres().stream()
        .map(mg -> {
          TmdbMovieDto.GenreInfo info = new TmdbMovieDto.GenreInfo();
          info.setId(mg.getGenre().getId());
          info.setName(mg.getGenre().getName());
          return info;
        })
        .toList();

    List<Integer> genreIds = genres.stream()
        .map(g -> g.getId().intValue())
        .toList();

    return new TmdbMovieDto(
        m.getId(),
        m.isAdult(),
        m.getBackdropPath(),
        genreIds,
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
        genres);
  }

  /**
   * cascade 없이 Movie → MovieGenre 를 단계별로 직접 저장하는 예제.
   * Movie 엔티티에 cascade = NONE 이라고 가정했을 때의 수동 구현.
   *
   * 저장 순서:
   *   1) Movie 만 먼저 저장 (movie_genre 컬렉션은 비어 있음)
   *   2) 저장된 Movie ID를 이용해 MovieGenre 엔티티를 별도 생성
   *   3) MovieGenreRepository 로 MovieGenre 를 직접 저장
   */
  @Transactional
  public int syncPopularMoviesNoCascade(int page) {
    TmdbPopularResponse response = tmdbRestClient.get()
        .uri(uriBuilder -> uriBuilder.path("/movie/popular")
            .queryParam("language", defaultLanguage)
            .queryParam("page", page)
            .build())
        .retrieve()
        .body(TmdbPopularResponse.class);

    if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
      log.warn("TMDB popular 응답이 비어있음. page={}", page);
      return 0;
    }

    Map<Long, Genre> genreMap = genreRepository.findAll().stream()
        .collect(Collectors.toMap(Genre::getId, g -> g));

    // Step 1: Movie 엔티티만 생성 — movieGenres 컬렉션에 아무것도 넣지 않는다
    List<Movie> movies = response.getResults().stream()
        .map(dto -> Movie.builder()
            .id(dto.getId())
            .adult(dto.isAdult())
            .backdropPath(dto.getBackdropPath())
            .title(dto.getTitle())
            .originalLanguage(dto.getOriginalLanguage())
            .originalTitle(dto.getOriginalTitle())
            .overview(dto.getOverview())
            .popularity(dto.getPopularity())
            .posterPath(dto.getPosterPath())
            .releaseDate(parseDate(dto.getReleaseDate()))
            .softcore(dto.isSoftcore())
            .video(dto.isVideo())
            .voteAverage(dto.getVoteAverage())
            .voteCount(dto.getVoteCount())
            .build())
        .toList();

    // Step 2: Movie 만 먼저 INSERT — popular_movie 테이블에만 행이 생긴다
    //         cascade 없으므로 movie_genre 테이블에는 아직 아무것도 없음
    List<Movie> savedMovies = movieRepository.saveAll(movies);
    log.info("Movie 저장 완료: count={}", savedMovies.size());

    // Step 3: 저장 완료된 Movie 를 기준으로 MovieGenre 엔티티를 직접 생성
    //         movie.getId() 가 확정된 이후에만 FK 를 가진 MovieGenre 를 만들 수 있다
    List<MovieGenre> movieGenres = response.getResults().stream()
        .flatMap(dto -> {
          // savedMovies 리스트는 순서가 동일하므로 ID 로 매핑
          Movie savedMovie = movieRepository.findById(dto.getId()).orElseThrow();
          if (dto.getGenreIds() == null) return java.util.stream.Stream.empty();
          return dto.getGenreIds().stream()
              .map(genreId -> genreMap.get(genreId.longValue()))
              .filter(genre -> genre != null)
              .map(genre -> MovieGenre.builder()
                  .movie(savedMovie)   // FK movie_id 직접 지정
                  .genre(genre)        // FK genre_id 직접 지정
                  .build());
        })
        .toList();

    // Step 4: MovieGenre 를 별도 리파지토리로 직접 INSERT
    //         cascade 에 의존하지 않고 명시적으로 저장
    movieGenreRepository.saveAll(movieGenres);
    log.info("MovieGenre 저장 완료: count={}", movieGenres.size());

    return savedMovies.size();
  }

  private LocalDate parseDate(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException e) {
      log.warn("release_date 파싱 실패: {}", value);
      return null;
    }
  }
}
