package com.example.movie.actor.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** TMDB {@code /movie/{id}/credits} 응답. crew 필드는 매핑하지 않아 무시된다(감독/제작진 제외). */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TmdbCreditsResponse {

  /** credits 의 기준이 되는 영화 id. */
  private Long id;

  private List<TmdbCastDto> cast;
}
