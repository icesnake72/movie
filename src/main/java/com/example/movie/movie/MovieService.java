package com.example.movie.movie;

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

    List<Movie> movies = response.getResults().stream()
        .map(this::toEntity)
        .toList();

    movieRepository.saveAll(movies);
    log.info("popular movie 저장 완료: page={}, count={}", page, movies.size());
    return movies.size();
  }

  @Transactional(readOnly = true)
  public List<TmdbMovieDto> findAll() {
    return movieRepository.findAll().stream()
        .map(this::toDto)
        .toList();
  }

  @Transactional
  public void deleteById(Long id) {
    movieRepository.deleteById(id);
    log.info("movie 삭제 요청: id={}", id);
  }

  private Movie toEntity(TmdbMovieDto dto) {
    return Movie.builder()
        .id(dto.getId())
        .adult(dto.isAdult())
        .backdropPath(dto.getBackdropPath())
        .genreIds(dto.getGenreIds() == null ? "" : dto.getGenreIds().toString())
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
  }

  private TmdbMovieDto toDto(Movie m) {
    return new TmdbMovieDto(
        m.getId(),
        m.isAdult(),
        m.getBackdropPath(),
        parseGenreIds(m.getGenreIds()),
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
        null);
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

  private List<Integer> parseGenreIds(String value) {
    if (value == null || value.isBlank()) {
      return Collections.emptyList();
    }
    String inner = value.replace("[", "").replace("]", "").replace(" ", "");
    if (inner.isEmpty()) {
      return Collections.emptyList();
    }
    return Arrays.stream(inner.split(","))
        .map(Integer::parseInt)
        .toList();
  }
}
