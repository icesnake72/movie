package com.example.movie.movie;

import com.example.movie.actor.Actor;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovieActorRepository extends JpaRepository<MovieActor, Long> {

  /** 영화 → 출연진. castOrder 오름차순(주연 우선)으로 actor 까지 fetch join. */
  @Query("SELECT ma FROM MovieActor ma "
      + "JOIN FETCH ma.actor "
      + "WHERE ma.movie.id = :movieId "
      + "ORDER BY ma.castOrder ASC")
  List<MovieActor> findByMovieIdWithActor(@Param("movieId") Long movieId);

  /** 배우 → 출연 영화 목록. 영화 인기순으로 movie 까지 fetch join. */
  @Query("SELECT ma FROM MovieActor ma "
      + "JOIN FETCH ma.movie "
      + "WHERE ma.actor.id = :actorId "
      + "ORDER BY ma.movie.popularity DESC")
  List<MovieActor> findByActorIdWithMovie(@Param("actorId") Long actorId);

  @Query("SELECT COUNT(ma) FROM MovieActor ma WHERE ma.movie.id = :movieId")
  long countByMovieId(@Param("movieId") Long movieId);

  /**
   * 함께 출연한 동료 배우 검색.
   * 같은 영화(ma1.movie = ma2.movie)에 출연했지만 본인이 아닌(actor.id &lt;&gt; :actorId) 배우를
   * 중복 제거하여 반환한다. 중간 테이블을 자기 자신과 조인하는 self-join 응용.
   */
  @Query("SELECT DISTINCT ma2.actor FROM MovieActor ma1, MovieActor ma2 "
      + "WHERE ma1.movie = ma2.movie "
      + "AND ma1.actor.id = :actorId "
      + "AND ma2.actor.id <> :actorId "
      + "ORDER BY ma2.actor.popularity DESC")
  List<Actor> findCoActors(@Param("actorId") Long actorId);

  /**
   * 주어진 배우들이 <b>모두</b> 함께 출연한 영화의 id 목록.
   * GROUP BY + HAVING COUNT(DISTINCT) 로 "교집합" 조건을 표현한다.
   * (전달된 배우 수와 매칭된 배우 수가 같은 영화만 남긴다.)
   */
  @Query("SELECT ma.movie.id FROM MovieActor ma "
      + "WHERE ma.actor.id IN :actorIds "
      + "GROUP BY ma.movie.id "
      + "HAVING COUNT(DISTINCT ma.actor.id) = :count")
  List<Long> findSharedMovieIds(@Param("actorIds") List<Long> actorIds,
      @Param("count") long count);

  /** 최다 출연 배우 랭킹. {@code [Actor, 출연편수]} 형태의 Object[] 로 반환. */
  @Query("SELECT ma.actor, COUNT(ma) FROM MovieActor ma "
      + "GROUP BY ma.actor "
      + "ORDER BY COUNT(ma) DESC")
  List<Object[]> findTopActorsByMovieCount(Pageable pageable);

  @Modifying
  @Query("DELETE FROM MovieActor ma WHERE ma.movie.id = :movieId")
  void deleteByMovieId(@Param("movieId") Long movieId);
}
