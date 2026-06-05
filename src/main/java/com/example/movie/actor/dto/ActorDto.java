package com.example.movie.actor.dto;

import lombok.Builder;
import lombok.Getter;

/** 배우 단건 응답. */
@Getter
@Builder
public class ActorDto {

  private Long id;
  private String name;
  private String profilePath;
  private Integer gender;
  private Double popularity;
}
