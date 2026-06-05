package com.example.movie.actor.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** TMDB {@code /movie/{id}/credits} 응답의 cast 배열 원소. (crew 는 사용하지 않는다.) */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TmdbCastDto {

  /** TMDB person id. 우리 DB Actor 의 PK 로 그대로 사용. */
  private Long id;

  private String name;

  @JsonProperty("profile_path")
  private String profilePath;

  /** 0=미상, 1=여성, 2=남성, 3=논바이너리. */
  private Integer gender;

  private Double popularity;

  /** 이 영화에서 맡은 배역명. */
  private String character;

  /** 출연 순서. 0 에 가까울수록 주연. */
  private Integer order;
}
