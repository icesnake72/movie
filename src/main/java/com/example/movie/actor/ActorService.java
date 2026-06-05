package com.example.movie.actor;

import com.example.movie.actor.dto.ActorDto;
import com.example.movie.actor.dto.ActorRankDto;
import com.example.movie.actor.dto.CastMemberDto;
import com.example.movie.actor.dto.FilmographyDto;
import com.example.movie.actor.dto.TmdbCastDto;
import com.example.movie.actor.dto.TmdbCreditsResponse;
import com.example.movie.movie.Movie;
import com.example.movie.movie.MovieActor;
import com.example.movie.movie.MovieActorRepository;
import com.example.movie.movie.MovieRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActorService {

  private final RestClient tmdbRestClient;
  private final MovieRepository movieRepository;
  private final ActorRepository actorRepository;
  private final MovieActorRepository movieActorRepository;

  @Value("${tmdb.default-language}")
  private String defaultLanguage;

  /**
   * 특정 영화의 출연진(cast)을 TMDB 에서 받아 Actor + MovieActor 로 저장한다.
   * crew(감독/제작진)는 응답 DTO 에 매핑하지 않으므로 자동으로 제외된다.
   *
   * @return 저장된 movie_actor 매핑 수. 영화가 없거나 cast 가 비면 0.
   */
  @Transactional
  public int syncCredits(Long movieId) {
    Movie movie = movieRepository.findById(movieId).orElse(null);
    if (movie == null) {
      log.warn("credits 동기화 대상 영화가 존재하지 않음. movieId={}", movieId);
      return 0;
    }

    TmdbCreditsResponse response = tmdbRestClient.get()
        .uri(uriBuilder -> uriBuilder.path("/movie/{id}/credits")
            .queryParam("language", defaultLanguage)
            .build(movieId))
        .retrieve()
        .body(TmdbCreditsResponse.class);

    if (response == null || response.getCast() == null || response.getCast().isEmpty()) {
      log.warn("TMDB credits 응답이 비어있음. movieId={}", movieId);
      return 0;
    }

    // 1) 동일 인물 중복 제거 후 Actor upsert (PK = TMDB id 이므로 save 는 merge 로 동작)
    Map<Long, Actor> actorMap = response.getCast().stream()
        .collect(Collectors.toMap(
            TmdbCastDto::getId,
            this::toActor,
            (a, b) -> a));
    actorRepository.saveAll(actorMap.values());

    // 2) 재동기화 대비: 기존 매핑 제거 (unique(movie_id, actor_id) 충돌 방지)
    movieActorRepository.deleteByMovieId(movieId);

    // 3) movie_actor 매핑 생성. 한 배우가 1인 2역으로 중복 등장할 수 있어
    //    actor id 기준으로 중복 제거하고 더 앞선 castOrder 를 채택한다.
    Map<Long, MovieActor> mappingMap = response.getCast().stream()
        .collect(Collectors.toMap(
            TmdbCastDto::getId,
            cast -> MovieActor.builder()
                .movie(movie)
                .actor(actorMap.get(cast.getId()))
                .characterName(cast.getCharacter())
                .castOrder(cast.getOrder())
                .build(),
            (a, b) -> safeOrder(a.getCastOrder()) <= safeOrder(b.getCastOrder()) ? a : b));
    movieActorRepository.saveAll(mappingMap.values());

    log.info("credits 저장 완료: movieId={}, actors={}, mappings={}",
        movieId, actorMap.size(), mappingMap.size());
    return mappingMap.size();
  }

  @Transactional(readOnly = true)
  public List<ActorDto> findAll() {
    return actorRepository.findAll().stream().map(this::toDto).toList();
  }

  @Transactional(readOnly = true)
  public List<ActorDto> searchByName(String name) {
    return actorRepository.findByNameContaining(name).stream().map(this::toDto).toList();
  }

  @Transactional(readOnly = true)
  public Optional<ActorDto> findById(Long id) {
    return actorRepository.findById(id).map(this::toDto);
  }

  /** 영화로 출연진 검색 (castOrder 순). */
  @Transactional(readOnly = true)
  public List<CastMemberDto> findActorsByMovie(Long movieId) {
    return movieActorRepository.findByMovieIdWithActor(movieId).stream()
        .map(ma -> {
          Actor a = ma.getActor();
          return CastMemberDto.builder()
              .id(a.getId())
              .name(a.getName())
              .profilePath(a.getProfilePath())
              .gender(a.getGender())
              .popularity(a.getPopularity())
              .character(ma.getCharacterName())
              .castOrder(ma.getCastOrder())
              .build();
        })
        .toList();
  }

  @Transactional(readOnly = true)
  public long countActorsByMovie(Long movieId) {
    return movieActorRepository.countByMovieId(movieId);
  }

  /** 배우로 출연 영화 목록 검색 (인기순). */
  @Transactional(readOnly = true)
  public List<FilmographyDto> findFilmography(Long actorId) {
    return movieActorRepository.findByActorIdWithMovie(actorId).stream()
        .map(ma -> {
          Movie m = ma.getMovie();
          return FilmographyDto.builder()
              .movieId(m.getId())
              .title(m.getTitle())
              .posterPath(m.getPosterPath())
              .releaseDate(m.getReleaseDate() == null ? null : m.getReleaseDate().toString())
              .popularity(m.getPopularity())
              .voteAverage(m.getVoteAverage())
              .character(ma.getCharacterName())
              .castOrder(ma.getCastOrder())
              .build();
        })
        .toList();
  }

  /** 함께 출연한 동료 배우 검색. */
  @Transactional(readOnly = true)
  public List<ActorDto> findCoActors(Long actorId) {
    return movieActorRepository.findCoActors(actorId).stream().map(this::toDto).toList();
  }

  /** 최다 출연 배우 랭킹. */
  @Transactional(readOnly = true)
  public List<ActorRankDto> findTopActors(int limit) {
    return movieActorRepository.findTopActorsByMovieCount(PageRequest.of(0, Math.max(1, limit)))
        .stream()
        .map(row -> {
          Actor a = (Actor) row[0];
          long count = ((Number) row[1]).longValue();
          return ActorRankDto.builder()
              .id(a.getId())
              .name(a.getName())
              .profilePath(a.getProfilePath())
              .popularity(a.getPopularity())
              .movieCount(count)
              .build();
        })
        .toList();
  }

  private Actor toActor(TmdbCastDto cast) {
    return Actor.builder()
        .id(cast.getId())
        .name(cast.getName())
        .profilePath(cast.getProfilePath())
        .gender(cast.getGender())
        .popularity(cast.getPopularity())
        .build();
  }

  private ActorDto toDto(Actor a) {
    return ActorDto.builder()
        .id(a.getId())
        .name(a.getName())
        .profilePath(a.getProfilePath())
        .gender(a.getGender())
        .popularity(a.getPopularity())
        .build();
  }

  private int safeOrder(Integer order) {
    return order == null ? Integer.MAX_VALUE : order;
  }
}
