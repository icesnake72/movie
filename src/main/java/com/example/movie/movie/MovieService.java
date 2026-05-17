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

  @Transactional
  public void deleteById(Long id) {
    movieRepository.deleteById(id);
    log.info("movie 삭제 요청: id={}", id);
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
