package com.example.movie.movie;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.movie.actor.Actor;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

/**
 * Movie ↔ Actor N:M 중간 테이블(MovieActor) 쿼리 검증.
 * H2(in-memory) 로 동작하도록 datasource/dialect 를 테스트 한정으로 덮어쓴다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:movie_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class MovieActorRepositoryTest {

  @Autowired
  private TestEntityManager em;

  @Autowired
  private MovieActorRepository movieActorRepository;

  private Actor a1;
  private Actor a2;
  private Actor a3;
  private Movie m1;
  private Movie m2;
  private Movie m3;

  @BeforeEach
  void setUp() {
    a1 = persistActor(10L, "Alice", 90.0);
    a2 = persistActor(20L, "Bob", 80.0);
    a3 = persistActor(30L, "Carol", 70.0);

    m1 = persistMovie(1L, "Movie One", 50.0);
    m2 = persistMovie(2L, "Movie Two", 100.0);
    m3 = persistMovie(3L, "Movie Three", 10.0);

    // M1: A1(주연), A2 / M2: A1, A3 / M3: A2
    link(m1, a1, "Hero", 0);
    link(m1, a2, "Villain", 1);
    link(m2, a1, "Detective", 0);
    link(m2, a3, "Sidekick", 1);
    link(m3, a2, "Lead", 0);

    em.flush();
    em.clear();
  }

  @Test
  @DisplayName("영화로 출연진 검색: castOrder 오름차순으로 actor 가 함께 로딩된다")
  void findByMovieIdWithActor() {
    List<MovieActor> cast = movieActorRepository.findByMovieIdWithActor(1L);

    assertThat(cast).hasSize(2);
    assertThat(cast).extracting(ma -> ma.getActor().getName())
        .containsExactly("Alice", "Bob");
    assertThat(cast.get(0).getCharacterName()).isEqualTo("Hero");
  }

  @Test
  @DisplayName("배우로 출연 영화 검색: 영화 인기순으로 반환된다")
  void findByActorIdWithMovie() {
    List<MovieActor> filmography = movieActorRepository.findByActorIdWithMovie(10L);

    assertThat(filmography).extracting(ma -> ma.getMovie().getTitle())
        .containsExactly("Movie Two", "Movie One");  // popularity 100 > 50
  }

  @Test
  @DisplayName("함께 출연한 동료 배우: 본인은 제외하고 중복 없이 반환된다")
  void findCoActors() {
    List<Actor> coActors = movieActorRepository.findCoActors(10L);

    assertThat(coActors).extracting(Actor::getName)
        .containsExactlyInAnyOrder("Bob", "Carol");
    assertThat(coActors).extracting(Actor::getId).doesNotContain(10L);
  }

  @Test
  @DisplayName("함께 출연한 영화: 전달한 배우가 모두 출연한 영화만 교집합으로 반환된다")
  void findSharedMovieIds() {
    assertThat(movieActorRepository.findSharedMovieIds(List.of(10L, 20L), 2))
        .containsExactly(1L);  // A1+A2 동시 출연은 M1
    assertThat(movieActorRepository.findSharedMovieIds(List.of(10L, 30L), 2))
        .containsExactly(2L);  // A1+A3 동시 출연은 M2
    assertThat(movieActorRepository.findSharedMovieIds(List.of(20L, 30L), 2))
        .isEmpty();            // A2+A3 함께 출연한 영화 없음
  }

  @Test
  @DisplayName("최다 출연 배우 랭킹: 출연 편수 내림차순")
  void findTopActorsByMovieCount() {
    List<Object[]> top = movieActorRepository.findTopActorsByMovieCount(PageRequest.of(0, 10));

    assertThat(top).hasSize(3);
    long firstCount = ((Number) top.get(0)[1]).longValue();
    long lastCount = ((Number) top.get(2)[1]).longValue();
    assertThat(firstCount).isEqualTo(2L);  // A1, A2 는 2편
    assertThat(lastCount).isEqualTo(1L);   // A3 는 1편
  }

  @Test
  @DisplayName("영화별 출연진 수 카운트")
  void countByMovieId() {
    assertThat(movieActorRepository.countByMovieId(1L)).isEqualTo(2L);
    assertThat(movieActorRepository.countByMovieId(3L)).isEqualTo(1L);
  }

  @Test
  @DisplayName("영화 기준 매핑 삭제: 재동기화 대비 deleteByMovieId")
  void deleteByMovieId() {
    movieActorRepository.deleteByMovieId(1L);
    em.flush();
    em.clear();

    assertThat(movieActorRepository.countByMovieId(1L)).isZero();
    assertThat(movieActorRepository.countByMovieId(2L)).isEqualTo(2L);
  }

  private Actor persistActor(Long id, String name, Double popularity) {
    return em.persist(Actor.builder()
        .id(id)
        .name(name)
        .gender(2)
        .popularity(popularity)
        .build());
  }

  private Movie persistMovie(Long id, String title, Double popularity) {
    return em.persist(Movie.builder()
        .id(id)
        .title(title)
        .popularity(popularity)
        .voteAverage(7.0)
        .build());
  }

  private void link(Movie movie, Actor actor, String character, int order) {
    em.persist(MovieActor.builder()
        .movie(movie)
        .actor(actor)
        .characterName(character)
        .castOrder(order)
        .build());
  }
}
