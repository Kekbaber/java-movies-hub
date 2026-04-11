package ru.practicum.moviehub.http;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import ru.practicum.moviehub.model.Movie;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public abstract class BaseHttpHandler implements HttpHandler {
    protected static final String CT_JSON = "application/json; charset=UTF-8";
    protected static final int MIN_VALID_YEAR = 1888;
    protected static final int MAX_VALID_YEAR = Year.now().getValue() + 1;
    protected static final int MAX_TITLE_LENGTH = 100;

    protected void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", CT_JSON);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    protected void sendNoContent(HttpExchange ex, int status) throws java.io.IOException {
        ex.getResponseHeaders().set("Content-Type", CT_JSON);
        ex.sendResponseHeaders(status, -1);
    }

    protected void sendMethodNotAllowed(HttpExchange exchange) throws IOException {
        sendJson(exchange, 405, "405 Method Not Allowed");
    }

    protected void sendBadRequest(HttpExchange exchange, String message) throws IOException {
        sendJson(exchange, 400, "{\"error\":\"" + message + "\"}");
    }

    protected void sendNotFound(HttpExchange exchange, String message) throws IOException {
        sendJson(exchange, 404, message);
    }

    protected void sendUnsupportedMediaType(HttpExchange exchange) throws IOException {
        sendNoContent(exchange, 415);
    }

    protected void sendInternalError(HttpExchange exchange) throws IOException {
        sendJson(exchange, 500, "{\"error\":\"" + "Внутренняя ошибка сервера" + "\"}");
    }

    protected String readRequestBody(HttpExchange exchange) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    protected boolean isContentTypeJson(HttpExchange exchange) {
        Headers headers = exchange.getRequestHeaders();
        List<String> contentType = headers.get("Content-Type");
        return contentType != null && contentType.stream().anyMatch(CT_JSON::equalsIgnoreCase);
    }

    protected boolean isNotValidYear(int year) {
        return year < MIN_VALID_YEAR || year > MAX_VALID_YEAR;
    }

    protected List<String> validateMovie(Movie movie) {
        List<String> errors = new ArrayList<>();

        if (movie.getTitle() == null || movie.getTitle().trim().isEmpty()) {
            errors.add("Название не должно быть пустым");
        } else if (movie.getTitle().length() > MAX_TITLE_LENGTH) {
            errors.add("Название не должно превышать " + MAX_TITLE_LENGTH + " символов");
        }

        if (isNotValidYear(movie.getYear())) {
            errors.add("Год должен быть между " + MIN_VALID_YEAR + " и " + MAX_VALID_YEAR);
        }

        return errors;
    }

    protected Method getMethod(HttpExchange exchange) {
        String method = exchange.getRequestMethod();
        if ("GET".equalsIgnoreCase(method)) {
            return Method.GET;
        } else if ("POST".equalsIgnoreCase(method)) {
            return Method.POST;
        } else if ("DELETE".equalsIgnoreCase(method)) {
            return Method.DELETE;
        }
        return Method.UNKNOWN;
    }

    protected enum Method {
        GET, POST, DELETE, UNKNOWN
    }
}