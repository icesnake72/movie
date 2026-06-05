package com.example.movie.actor.dto;

import lombok.Builder;
import lombok.Getter;

/** "배우로 출연 영화 검색" 응답. 영화 요약 + 그 영화에서의 배역/출연순서. */
@Getter
@Builder
public class FilmographyDto {

  private Long movieId;
  private String title;
  private String posterPath;
  private String releaseDate;
  private Double popularity;
  private Double voteAverage;
  private String character;
  private Integer castOrder;
}
