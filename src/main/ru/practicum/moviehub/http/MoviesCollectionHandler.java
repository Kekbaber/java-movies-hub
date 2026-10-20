package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.util.List;

public class MoviesCollectionHandler extends BaseHttpHandler {
    private final MoviesStore store;
    private final Gson gson;

    public MoviesCollectionHandler(MoviesStore store) {
        this.store = store;
        this.gson = new Gson();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            Method method = getMethod(exchange);
            switch (method) {
                case GET -> handleGet(exchange);
                case POST -> handlePost(exchange);
                default -> sendMethodNotAllowed(exchange);
            }
        } catch (NumberFormatException e) {
            sendBadRequest(exchange, "Некорректный числовой параметр");
        } catch (IllegalArgumentException e) {
            sendBadRequest(exchange, e.getMessage());
        } catch (Exception e) {
            sendInternalError(exchange);
        }
    }

    private void handleGet(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        if (query != null && query.startsWith("year=")) {
            handleGetByYear(exchange, query);
        } else {
            handleGetAll(exchange);
        }
    }

    private void handleGetAll(HttpExchange exchange) throws IOException {
        List<Movie> movies = store.getStore();
        String json = gson.toJson(movies);
        sendJson(exchange, 200, json);
    }

    private void handleGetByYear(HttpExchange exchange, String query) throws IOException {
        int year;
        try {
            year = Integer.parseInt(query.substring(5));
            if (isNotValidYear(year)) {
                sendBadRequest(exchange, "Год должен быть между " + MIN_VALID_YEAR + " и " + MAX_VALID_YEAR);
                return;
            }
        } catch (NumberFormatException e) {
            sendBadRequest(exchange, "Некорректный параметр 'year'");
            return;
        }

        List<Movie> filtered = store.getMoviesByYear(year);
        sendJson(exchange, 200, gson.toJson(filtered));
    }

    private void handlePost(HttpExchange exchange) throws IOException {
        if (!isContentTypeJson(exchange)) {
            sendUnsupportedMediaType(exchange);
            return;
        }

        String jsonText = readRequestBody(exchange);
        if (jsonText.isBlank()) {
            sendBadRequest(exchange, "Тело запроса не должно быть пустым");
            return;
        }

        Movie movie;
        try {
            movie = gson.fromJson(jsonText, Movie.class);
        } catch (JsonSyntaxException e) {
            sendBadRequest(exchange, "Некорректный JSON");
            return;
        }

        if (movie == null) {
            sendBadRequest(exchange, "Некорректное тело запроса");
            return;
        }

        List<String> validationErrors = validateMovie(movie);
        if (!validationErrors.isEmpty()) {
            ErrorResponse error = new ErrorResponse(422, "Ошибка валидации", validationErrors);
            sendJson(exchange, 422, gson.toJson(error));
            return;
        }

        store.add(movie);
        sendJson(exchange, 201, gson.toJson(movie));
    }
}
