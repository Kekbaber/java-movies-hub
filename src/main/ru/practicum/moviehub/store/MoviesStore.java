package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class MoviesStore {

    private final List<Movie> store;
    private long nextId = 1;

    public MoviesStore() {
        store = new ArrayList<>();
    }

    public void add(Movie movie) {
        movie.setId(nextId++);
        store.add(movie);
    }

    public List<Movie> getStore() {
        return new ArrayList<>(store);
    }

    public Optional<Movie> getMovieByIdOptional(long id) {
        return store.stream()
                .filter(movie -> movie.getId() == id)
                .findFirst();
    }

    public void deleteMovie(long id) {
        store.stream()
                .filter(movie -> movie.getId() == id)
                .findFirst()
                .ifPresent(store::remove);
    }

    public List<Movie> getMoviesByYear(int year) {
        return store.stream()
                .filter(movie -> movie.getYear() == year)
                .collect(Collectors.toList());
    }

    public boolean containsMovie(long id) {
        return store.stream()
                .anyMatch(movie -> movie.getId() == id);
    }

    public void clear() {
        store.clear();
        nextId = 1;
    }
}