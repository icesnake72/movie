package com.example.movie.actor;

import com.example.movie.actor.dto.ActorDto;
import com.example.movie.actor.dto.ActorRankDto;
import com.example.movie.actor.dto.FilmographyDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/actors")
@RequiredArgsConstructor
public class ActorController {

  private final ActorService actorService;

  /** 전체 배우 조회 또는 이름 검색({@code ?name=...}). */
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
