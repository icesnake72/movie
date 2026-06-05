package com.example.movie.actor.dto;

import lombok.Builder;
import lombok.Getter;

/** "영화로 출연진 검색" 응답. 배우 정보 + 이 영화에서의 배역/출연순서. */
@Getter
@Builder
public class CastMemberDto {

  private Long id;
  private String name;
  private String profilePath;
  private Integer gender;
  private Double popularity;
  private String character;
  private Integer castOrder;
}
