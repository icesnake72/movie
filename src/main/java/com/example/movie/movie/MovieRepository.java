package com.example.movie.movie;

import com.example.movie.genre.Genre;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovieRepository extends JpaRepository<Movie, Long> {

  @Query("SELECT DISTINCT m FROM Movie m LEFT JOIN FETCH m.movieGenres mg LEFT JOIN FETCH mg.genre")
  List<Movie> findAllWithGenres();

  @Query("SELECT DISTINCT m FROM Movie m "
      + "LEFT JOIN FETCH m.movieGenres mg "
      + "LEFT JOIN FETCH mg.genre "
      + "WHERE mg.genre.id = :genreId")
  List<Movie> findAllByGenreId(@Param("genreId") Long genreId);

  @Query("SELECT DISTINCT m FROM Movie m "
      + "LEFT JOIN FETCH m.movieGenres mg "
      + "LEFT JOIN FETCH mg.genre "
      + "WHERE LOWER(m.title) LIKE LOWER(CONCAT('%', :title, '%'))")
  List<Movie> findAllByTitleContaining(@Param("title") String title);

  @Query("SELECT DISTINCT m FROM Movie m "
      + "LEFT JOIN FETCH m.movieGenres mg "
      + "LEFT JOIN FETCH mg.genre "
      + "ORDER BY m.popularity DESC")
  List<Movie> findAllOrderByPopularityDesc();

  @Query("SELECT DISTINCT m FROM Movie m "
      + "LEFT JOIN FETCH m.movieGenres mg "
      + "LEFT JOIN FETCH mg.genre "
      + "ORDER BY m.voteAverage DESC")
  List<Movie> findAllOrderByVoteAverageDesc();

  @Query("SELECT mg.genre FROM MovieGenre mg WHERE mg.movie.id = :movieId")
  List<Genre> findGenresByMovieId(@Param("movieId") Long movieId);
}
