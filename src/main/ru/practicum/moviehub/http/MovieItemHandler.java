package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.util.Optional;

public class MovieItemHandler extends BaseHttpHandler {

    private final MoviesStore store;
    private final Gson gson;

    MovieItemHandler(MoviesStore store) {
        this.store = store;
        this.gson = new Gson();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            Method method = getMethod(exchange);
            long id = extractIdFromPath(exchange);
            switch (method) {
                case GET -> handleGetById(exchange, id);
                case DELETE -> handleDelete(exchange, id);
                default -> sendMethodNotAllowed(exchange);
            }
        } catch (NumberFormatException e) {
            sendBadRequest(exchange, "ID должен быть числом");
        } catch (IllegalArgumentException e) {
            sendNotFound(exchange, "Некорректный путь. Ожидается /movies/{id}");
        } catch (Exception e) {
            sendInternalError(exchange);
        }
    }

    private long extractIdFromPath(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        String[] segments = path.split("/");
        if (segments.length != 3) {
            throw new IllegalArgumentException("Некорректный путь: ожидается /movies/{id}");
        }
        try {
            return Long.parseLong(segments[2]);
        } catch (NumberFormatException e) {
            throw new NumberFormatException("ID должен быть числом");
        }
    }

    private void handleGetById(HttpExchange exchange, long id) throws IOException {
        Optional<Movie> movieOpt = store.getMovieByIdOptional(id);

        if (movieOpt.isPresent()) {
            sendJson(exchange, 200, gson.toJson(movieOpt.get()));
        } else {
            sendNotFound(exchange, "Фильм не найден");
        }
    }

    private void handleDelete(HttpExchange exchange, long id) throws IOException {
        if (store.containsMovie(id)) {
            store.deleteMovie(id);
            sendNoContent(exchange, 204);
        } else {
            sendNotFound(exchange, "Фильм не найден");
        }
    }
}
