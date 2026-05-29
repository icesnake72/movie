package com.example.movie.movie;

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

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
    movieService.deleteById(id);
    return ResponseEntity.noContent().build();
  }
}
