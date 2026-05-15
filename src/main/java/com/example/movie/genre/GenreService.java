package com.example.movie.genre;

import com.example.movie.genre.dto.TmdbGenreResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class GenreService {

  private final RestClient tmdbRestClient;
  private final GenreRepository genreRepository;

  @Transactional
  public int syncGenres() {
    TmdbGenreResponse response = tmdbRestClient.get()
        .uri(uriBuilder -> uriBuilder.path("/genre/movie/list")
            .build())
        .retrieve()
        .body(TmdbGenreResponse.class);

    if (response == null || response.getGenres() == null || response.getGenres().isEmpty()) {
      log.warn("TMDB에서 받은 장르 데이터가 비어있습니다");
      return 0;
    }

    response.getGenres().forEach(genreDto -> {
      Genre genre = Genre.builder()
          .id(genreDto.getId())
          .name(genreDto.getName())
          .build();
      genreRepository.save(genre);
    });

    int count = response.getGenres().size();
    log.info("장르 저장 완료: 총 {} 개", count);
    return count;
  }
}
