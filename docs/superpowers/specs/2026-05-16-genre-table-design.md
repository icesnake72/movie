# Genre Table Design Spec

**Date:** 2026-05-16  
**Status:** Approved  
**Scope:** Add Genre table and genre_movie junction table; sync genre data from TMDB

---

## Overview

Introduce a separate `Genre` table to store genre metadata from TMDB, with a `genre_movie` junction table for future Movie-Genre relationships via ManyToOne mapping. Currently, `Movie.genre_ids` remains as-is; future updates will migrate to explicit foreign key relationships.

---

## Data Model

### Genre Table
- `id` (Long, PK) - TMDB genre ID
- `name` (String, 255) - Genre name (e.g., "Action", "Comedy")

### genre_movie Junction Table
- `movie_id` (Long, FK to popular_movie)
- `genre_id` (Long, FK to genre)
- Primary Key: (movie_id, genre_id)

**Note:** Initially empty; populated when Movie-Genre ManyToOne relationship is implemented.

### Movie Table
- Remains unchanged
- `genre_ids` column continues storing comma-separated genre IDs as string
- Future migration will transition to foreign key lookup via genre_movie

---

## Components to Implement

### 1. Genre Entity
- `@Entity` mapped to `genre` table
- Fields: `id` (PK), `name`
- Lombok: `@Getter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`

### 2. GenreRepository
- Extends `JpaRepository<Genre, Long>`
- Basic CRUD operations

### 3. TMDB DTOs
- `TmdbGenreResponse` - wraps list of genres
- `TmdbGenreDto` - single genre (id, name)

### 4. GenreService
- `syncGenres()` - calls `/genre/movie/list` endpoint, saves to DB
- Upsert behavior (insert new, update existing by ID)
- Logging for sync results

### 5. GenreController
- `POST /api/genres/sync` - trigger sync from TMDB
  - Returns: `{"synced": <count>}`
- `GET /api/genres` - list all genres
  - Returns: `List<TmdbGenreDto>`

### 6. MovieService Enhancement
- Update `toDto()` method to fetch genre names from Genre table
- Map `genre_ids` string to `List<GenreDto>` in response
- Join logic: parse genre_ids → lookup Genre by id → build genre objects

---

## API Endpoints

| Method | Path | Response |
|---|---|---|
| `POST` | `/api/genres/sync` | `{"synced": 10}` |
| `GET` | `/api/genres` | `[{"id": 28, "name": "Action"}, ...]` |
| `GET` | `/api/movies/popular` | Enhanced with genre objects |

---

## Migration Notes

- Movie-Genre junction table (`genre_movie`) created but not populated
- Explicit foreign key relationship deferred to next phase
- `Movie.genre_ids` remains functional; no schema changes needed now

---

## Testing

- Unit tests for GenreService sync logic
- Integration tests for TMDB API calls
- Verify genre lookup in Movie response

---

## Constraints

- Use existing coding style: 2-space indentation, minimal comments, Lombok
- TMDB API key via environment variable
- Follow Spring Boot patterns (Service, Repository, Controller)
