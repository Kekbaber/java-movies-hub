package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;

class MoviesHandler extends BaseHttpHandler {
    private static final int MIN_VALID_YEAR = 1888;
    private static final int MAX_VALID_YEAR = Year.now().getValue() + 1;
    private static final int MAX_TITLE_LENGTH = 100;

    private final MoviesStore store;
    private final Gson gson;

    MoviesHandler(MoviesStore store) {
        this.store = store;
        this.gson = new Gson();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            URI uri = exchange.getRequestURI();
            Endpoint endpoint = getEndpoint(uri, method);

            switch (endpoint) {
                case GET_ALL -> handleGetAll(exchange);
                case GET_BY_ID -> handleGetById(exchange);
                case GET_BY_YEAR -> handleGetByYear(exchange);
                case POST -> handlePost(exchange);
                case DELETE -> handleDelete(exchange);
                default -> sendMethodNotAllowed(exchange);
            }
        } catch (NumberFormatException e) {
            sendBadRequest(exchange, "Некорректный числовой параметр");
        } catch (IllegalArgumentException e) {
            sendBadRequest(exchange, e.getMessage());
        } catch (Exception e) {
            sendInternalError(exchange, "Внутренняя ошибка сервера");
        }
    }

    private enum Endpoint {
        GET_ALL, GET_BY_ID, GET_BY_YEAR, POST, DELETE, UNKNOWN
    }

    private Endpoint getEndpoint(URI uri, String method) {
        String path = uri.getPath();
        String query = uri.getQuery();
        String[] segments = path.split("/");

        if ("GET".equalsIgnoreCase(method)) {
            if (segments.length == 2) {
                if (query != null && query.startsWith("year=")) {
                    return Endpoint.GET_BY_YEAR;
                } else if (query == null) {
                    return Endpoint.GET_ALL;
                }
            }
            if (segments.length == 3 && segments[1].equals("movies")) {
                return Endpoint.GET_BY_ID;
            }
        } else if ("POST".equalsIgnoreCase(method) && segments.length == 2 && "movies".equals(segments[1])) {
            return Endpoint.POST;
        } else if ("DELETE".equalsIgnoreCase(method) && segments.length == 3 && "movies".equals(segments[1])) {
            return Endpoint.DELETE;
        }
        return Endpoint.UNKNOWN;
    }

    private void handleGetAll(HttpExchange exchange) throws IOException {
        List<Movie> movies = store.getStore();
        String json = gson.toJson(movies);
        sendJson(exchange, 200, json);
    }

    private void handleGetById(HttpExchange exchange) throws IOException {
        long id = extractIdFromPath(exchange);
        Optional<Movie> movieOpt = store.getMovieByIdOptional(id);

        if (movieOpt.isPresent()) {
            sendJson(exchange, 200, gson.toJson(movieOpt.get()));
        } else {
            sendNotFound(exchange, "Фильм не найден");
        }
    }

    private void handleGetByYear(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        if (query == null || !query.startsWith("year=")) {
            sendNotFound(exchange, "Не найден параметр year");
            return;
        }

        int year;
        try {
            year = Integer.parseInt(query.substring(5));
        } catch (NumberFormatException e) {
            sendBadRequest(exchange, "Некорректный параметр 'year'");
            return;
        }

        if (!isValidYear(year)) {
            sendBadRequest(exchange, "Год должен быть между " + MIN_VALID_YEAR + " и " + MAX_VALID_YEAR);
            return;
        }

        List<Movie> filtered = store.getStore().stream()
                .filter(m -> m.getYear() == year)
                .collect(Collectors.toList());
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

    private void handleDelete(HttpExchange exchange) throws IOException {
        long id = extractIdFromPath(exchange);
        if (store.containsMovie(id)) {
            store.deleteMovie(id);
            sendNoContent(exchange, 204);
        } else {
            sendNotFound(exchange, "Фильм не найден");
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

    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    private boolean isContentTypeJson(HttpExchange exchange) {
        Headers headers = exchange.getRequestHeaders();
        List<String> contentType = headers.get("Content-Type");
        return contentType != null && contentType.stream().anyMatch(CT_JSON::equalsIgnoreCase);
    }

    private boolean isValidYear(int year) {
        return year >= MIN_VALID_YEAR && year <= MAX_VALID_YEAR;
    }

    private List<String> validateMovie(Movie movie) {
        List<String> errors = new ArrayList<>();

        if (movie.getTitle() == null || movie.getTitle().trim().isEmpty()) {
            errors.add("Название не должно быть пустым");
        } else if (movie.getTitle().length() > MAX_TITLE_LENGTH) {
            errors.add("Название не должно превышать " + MAX_TITLE_LENGTH + " символов");
        }

        if (!isValidYear(movie.getYear())) {
            errors.add("Год должен быть между " + MIN_VALID_YEAR + " и " + MAX_VALID_YEAR);
        }

        return errors;
    }

    private void sendMethodNotAllowed(HttpExchange exchange) throws IOException {
        sendJson(exchange, 405, "405 Method Not Allowed");
    }

    private void sendBadRequest(HttpExchange exchange, String message) throws IOException {
        sendJson(exchange, 400, "{\"error\":\"" + message + "\"}");
    }

    private void sendNotFound(HttpExchange exchange, String message) throws IOException {
        sendJson(exchange, 404, message);
    }

    private void sendUnsupportedMediaType(HttpExchange exchange) throws IOException {
        sendNoContent(exchange, 415);
    }

    private void sendInternalError(HttpExchange exchange, String message) throws IOException {
        sendJson(exchange, 500, "{\"error\":\"" + message + "\"}");
    }
}