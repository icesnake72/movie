package com.example.movie.movie;

import com.example.movie.actor.Actor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Movie 와 Actor 의 N:M 관계를 풀어내는 중간 엔티티.
 *
 * <p>MovieGenre 와 동일하게 {@code @ManyToMany} 대신 중간 엔티티로 분리한다.
 * 다만 단순 연결만 하는 MovieGenre 와 달리, 이 엔티티는
 * {@code characterName}(배역명), {@code castOrder}(출연 순서) 같은
 * <b>관계 자체의 속성</b>을 가진다 — {@code @ManyToMany} 로는 표현할 수 없는 부분이며
 * 중간 엔티티 패턴이 필요한 대표적인 이유다.
 */
@Entity
@Table(
    name = "movie_actor",
    uniqueConstraints = @UniqueConstraint(columnNames = {"movie_id", "actor_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class MovieActor {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "movie_id", nullable = false)
  private Movie movie;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "actor_id", nullable = false)
  private Actor actor;

  @Column(name = "character_name", length = 300)
  private String characterName;

  @Column(name = "cast_order")
  private Integer castOrder;  // TMDB order 필드. 0 에 가까울수록 주연
}
