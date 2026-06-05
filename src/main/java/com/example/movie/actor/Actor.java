package com.example.movie.actor;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "actor")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Actor {

  @Id
  @Column(name = "id", nullable = false)
  private Long id;  // TMDB person id 를 그대로 PK 로 사용 (Genre/Movie 와 동일 전략)

  @Column(name = "name", length = 200, nullable = false)
  private String name;

  @JsonProperty("profile_path")
  @Column(name = "profile_path", length = 500)
  private String profilePath;

  @Column(name = "gender")
  private Integer gender;  // TMDB 코드: 0=미상, 1=여성, 2=남성, 3=논바이너리

  @Column(name = "popularity")
  private Double popularity;
}
