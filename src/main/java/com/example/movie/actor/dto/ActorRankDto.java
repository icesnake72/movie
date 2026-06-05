package com.example.movie.actor.dto;

import lombok.Builder;
import lombok.Getter;

/** "최다 출연 배우" 랭킹 응답. */
@Getter
@Builder
public class ActorRankDto {

  private Long id;
  private String name;
  private String profilePath;
  private Double popularity;
  private long movieCount;
}
