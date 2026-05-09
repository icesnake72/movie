package com.example.movie.movie;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "popular_movie")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Movie {

  @Id
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "adult", nullable = false)
  private boolean adult;

  @JsonProperty("backdrop_path")
  @Column(name = "backdrop_path", length = 500)
  private String backdropPath;

  @JsonProperty("genre_ids")
  @Column(name = "genre_ids", length = 500)
  private String genreIds;

  @Column(name = "title", length = 500, nullable = false)
  private String title;

  @JsonProperty("original_language")
  @Column(name = "original_language", length = 10)
  private String originalLanguage;

  @JsonProperty("original_title")
  @Column(name = "original_title", length = 500)
  private String originalTitle;

  @Lob
  @Column(name = "overview", columnDefinition = "TEXT")
  private String overview;

  @Column(name = "popularity")
  private Double popularity;

  @JsonProperty("poster_path")
  @Column(name = "poster_path", length = 500)
  private String posterPath;

  @JsonProperty("release_date")
  @Column(name = "release_date")
  private LocalDate releaseDate;

  @Column(name = "softcore", nullable = false)
  private boolean softcore;

  @Column(name = "video", nullable = false)
  private boolean video;

  @JsonProperty("vote_average")
  @Column(name = "vote_average")
  private Double voteAverage;

  @JsonProperty("vote_count")
  @Column(name = "vote_count")
  private Integer voteCount;
}
