package com.example.movie.movie.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TmdbMovieDto {

  /** TMDB 가 부여한 영화 고유 ID. 우리 DB 의 PK 로도 그대로 사용한다. */
  private Long id;

  /** 성인(19금) 콘텐츠 여부. true 면 일반 사용자에게 노출 제외 대상. */
  private boolean adult;

  /**
   * 배경(backdrop) 이미지 경로. 영화 상세 페이지 상단 배경 등에 쓰이는 가로형 이미지.
   * 경로만 내려오므로 실제 URL 은 {@code https://image.tmdb.org/t/p/{size}{path}} 형태로 조립해야 한다.
   * 예) {@code https://image.tmdb.org/t/p/w780/9Z2uDYXqJrlmePznQQJhL6d92Rq.jpg}
   */
  @JsonProperty("backdrop_path")
  private String backdropPath;

  /**
   * 장르 ID 목록. 각 숫자는 TMDB 의 장르 코드(예: 28=Action, 35=Comedy, 16=Animation 등).
   * 장르명은 별도 {@code /genre/movie/list} API 에서 조회한 코드 → 이름 매핑 테이블이 필요하다.
   */
  @JsonProperty("genre_ids")
  private List<Integer> genreIds;

  /** 요청 언어(language=ko-KR)에 해당하는 영화 제목. 번역본이 없으면 원어 제목으로 채워질 수 있다. */
  private String title;

  /** 원작 언어 코드(ISO 639-1, 두 글자). 예: en, ko, ja, fr. */
  @JsonProperty("original_language")
  private String originalLanguage;

  /** 원작 제목. 번역되지 않은, 제작국가 언어 그대로의 제목. */
  @JsonProperty("original_title")
  private String originalTitle;

  /** 영화 줄거리/시놉시스. 길이가 길 수 있어 DB 매핑 시 TEXT 타입으로 저장한다. */
  private String overview;

  /** TMDB 자체 알고리즘이 산정한 인기도 점수(조회수·검색량 등 기반). 값이 클수록 인기. */
  private Double popularity;

  /**
   * 포스터 이미지 경로. 영화 카드/리스트 썸네일에 쓰이는 세로형 이미지.
   * backdropPath 와 마찬가지로 TMDB 이미지 베이스 URL 과 합쳐 사용한다.
   */
  @JsonProperty("poster_path")
  private String posterPath;

  /**
   * 개봉일. TMDB 응답에서는 {@code "2026-04-01"} 형태의 문자열로 내려오므로
   * 일단 String 으로 받고, 엔티티 변환 시 {@link java.time.LocalDate} 로 파싱한다.
   * 미개봉작/정보 부재 시 빈 문자열일 수 있다.
   */
  @JsonProperty("release_date")
  private String releaseDate;

  /** softcore(선정성) 콘텐츠 표시 플래그. TMDB 가 비교적 최근 추가한 필드. */
  private boolean softcore;

  /** 영상물(예고편 등 비디오 자체) 여부. 일반 영화 레코드는 대부분 false. */
  private boolean video;

  /** 평균 평점(0.0 ~ 10.0). 투표가 적으면 신뢰도가 낮다. */
  @JsonProperty("vote_average")
  private Double voteAverage;

  /** 평점에 참여한 투표 수. voteAverage 의 신뢰도를 가늠하는 보조 지표. */
  @JsonProperty("vote_count")
  private Integer voteCount;
}
