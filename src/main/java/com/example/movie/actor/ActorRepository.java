package com.example.movie.actor;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActorRepository extends JpaRepository<Actor, Long> {

  @Query("SELECT a FROM Actor a "
      + "WHERE LOWER(a.name) LIKE LOWER(CONCAT('%', :name, '%')) "
      + "ORDER BY a.popularity DESC")
  List<Actor> findByNameContaining(@Param("name") String name);
}
