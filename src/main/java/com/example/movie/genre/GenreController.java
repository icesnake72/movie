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

@RestController
@RequestMapping("/api/genres")
@RequiredArgsConstructor
public class GenreController {

  private final GenreService genreService;

  @PostMapping("/sync")
  public ResponseEntity<Map<String, Object>> sync() {
    int synced = genreService.syncGenres();
    return ResponseEntity.ok(Map.of("synced", synced));
  }

  @GetMapping
  public List<TmdbGenreDto> list() {
    return genreService.findAll();
  }
}
