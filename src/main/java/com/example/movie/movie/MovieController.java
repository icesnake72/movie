package com.example.movie.movie;

import com.example.movie.actor.ActorService;
import com.example.movie.actor.dto.CastMemberDto;
import com.example.movie.movie.dto.TmdbMovieDto;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/movies/popular")
@RequiredArgsConstructor
public class MovieController {

  private final MovieService movieService;
  private final ActorService actorService;

  @PostMapping("/sync")
  public ResponseEntity<Map<String, Object>> sync(
      @RequestParam(name = "page", defaultValue = "1") int page) {
    int saved = movieService.syncPopularMovies(page);
    return ResponseEntity.ok(Map.of("page", page, "saved", saved));
  }

  @GetMapping
  public List<TmdbMovieDto> list(
      @RequestParam(required = false) Long genreId,
      @RequestParam(required = false) String title,
      @RequestParam(required = false) String sort) {
    if (genreId != null) return movieService.findByGenreId(genreId);
    if (title != null)   return movieService.findByTitle(title);
    if (sort != null)    return movieService.findAllSorted(sort);
    return movieService.findAll();
  }

  @GetMapping("/{id}/genres")
  public ResponseEntity<List<TmdbMovieDto.GenreInfo>> genres(@PathVariable("id") Long id) {
    List<TmdbMovieDto.GenreInfo> result = movieService.findGenresByMovieId(id);
    if (result.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(result);
  }

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
   * 예) {@code /api/movies/popular/shared?actorId=1&actorId=2}
   */
  @GetMapping("/shared")
  public List<TmdbMovieDto> sharedMovies(@RequestParam("actorId") List<Long> actorIds) {
    return movieService.findSharedMovies(actorIds);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
    movieService.deleteById(id);
    return ResponseEntity.noContent().build();
  }
}
