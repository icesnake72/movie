-- genre_movie: 영화와 장르의 다대다 관계 테이블 (향후 ManyToOne 관계 설정 시 사용)
CREATE TABLE IF NOT EXISTS genre_movie (
  movie_id BIGINT NOT NULL,
  genre_id BIGINT NOT NULL,
  PRIMARY KEY (movie_id, genre_id),
  CONSTRAINT fk_genre_movie_movie
    FOREIGN KEY (movie_id) REFERENCES popular_movie (id) ON DELETE CASCADE,
  CONSTRAINT fk_genre_movie_genre
    FOREIGN KEY (genre_id) REFERENCES genre (id) ON DELETE CASCADE
);
