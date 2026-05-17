package com.example.movie.movie;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MovieRepository extends JpaRepository<Movie, Long> {

  @Query("SELECT DISTINCT m FROM Movie m LEFT JOIN FETCH m.movieGenres mg LEFT JOIN FETCH mg.genre")
  List<Movie> findAllWithGenres();
}
