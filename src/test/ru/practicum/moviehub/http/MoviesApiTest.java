package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import org.junit.jupiter.api.*;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Year;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MoviesApiTest {

    private static final String BASE_URL = "http://localhost:8080";
    private static final int PORT = 8080;
    private static final String JSON_CONTENT_TYPE = "application/json; charset=UTF-8";
    private static final String MOVIE_HANDLER_PATH = "/movies";

    private static MoviesServer server;
    private static HttpClient client;
    private static MoviesStore store;
    private static Gson gson;

    @BeforeAll
    static void beforeAll() {
        gson = new Gson();
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        store = new MoviesStore();
        server = new MoviesServer(store, PORT);
        server.start();
    }

    @BeforeEach
    void beforeEach() {
        store.clear();
    }

    @AfterAll
    static void afterAll() {
        server.stop();
    }

    private HttpRequest.Builder requestBuilder(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Content-Type", JSON_CONTENT_TYPE)
                .timeout(Duration.ofSeconds(5));
    }

    private HttpResponse<String> sendGet(String path) throws IOException, InterruptedException {
        HttpRequest request = requestBuilder(path).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> sendPost(String jsonBody) throws IOException, InterruptedException {
        HttpRequest request = requestBuilder(MOVIE_HANDLER_PATH)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> sendDelete(String path) throws IOException, InterruptedException {
        HttpRequest request = requestBuilder(path).DELETE().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private void assertSuccessResponse(HttpResponse<String> response, int expectedStatusCode) {
        assertEquals(expectedStatusCode, response.statusCode(),
                "Unexpected status code for successful request");
        assertEquals(JSON_CONTENT_TYPE, response.headers().firstValue("Content-Type").orElse(""),
                "Content-Type header should be application/json; charset=UTF-8");
    }

    private void assertErrorResponse(HttpResponse<String> response, int expectedStatusCode) {
        assertEquals(expectedStatusCode, response.statusCode(), "Unexpected error status code");
    }

    private Movie addMovieToStore(String title, int year) {
        Movie movie = new Movie(0, title, year);
        store.add(movie);
        return store.getStore().stream()
                .filter(m -> m.getTitle().equals(title) && m.getYear() == year)
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("GET /movies should return empty array when store is empty")
    void shouldReturnEmptyArray_whenNoMovies() throws Exception {
        HttpResponse<String> response = sendGet("/movies");

        assertSuccessResponse(response, 200);
        String body = response.body().trim();
        assertTrue(body.startsWith("[") && body.endsWith("]"), "Response body should be JSON array");
        assertEquals("[]", body, "Body should be empty array");
    }

    @Test
    @DisplayName("GET /movies should return all movies when store has data")
    void shouldReturnAllMovies_whenStoreHasMovies() throws Exception {
        Movie movie1 = addMovieToStore("Movie 1", 2000);
        Movie movie2 = addMovieToStore("Movie 2", 2001);

        HttpResponse<String> response = sendGet("/movies");

        assertSuccessResponse(response, 200);
        String expectedJson = gson.toJson(List.of(movie1, movie2));
        assertEquals(expectedJson, response.body(), "Response should contain all movies");
    }

    @Test
    @DisplayName("GET /movies/{id} should return 404 when movie not found")
    void shouldReturn404_whenMovieNotFound() throws Exception {
        HttpResponse<String> response = sendGet("/movies/999");
        assertErrorResponse(response, 404);
    }

    @Test
    @DisplayName("GET /movies/{id} should return 400 when id is invalid")
    void shouldReturn400_whenMovieIdIsInvalid() throws Exception {
        HttpResponse<String> response = sendGet("/movies/abc");
        assertErrorResponse(response, 400);
    }

    @Test
    @DisplayName("GET /movies/{id} should return movie when exists")
    void shouldReturnMovie_whenExists() throws Exception {
        Movie added = addMovieToStore("Test Movie", 2020);
        long id = added.getId();

        HttpResponse<String> response = sendGet("/movies/" + id);

        assertSuccessResponse(response, 200);
        Movie actual = gson.fromJson(response.body(), Movie.class);
        assertEquals(added, actual, "Returned movie should match stored one");
    }

    @Test
    @DisplayName("POST /movies should create movie and return 201 with generated id")
    void shouldCreateMovie_whenValidData() throws Exception {
        String jsonBody = "{\"title\":\"New Movie\",\"year\":1999}";
        HttpResponse<String> response = sendPost(jsonBody);

        assertSuccessResponse(response, 201);
        Movie created = gson.fromJson(response.body(), Movie.class);
        assertTrue(created.getId() > 0, "ID should be generated positive number");
        assertEquals("New Movie", created.getTitle());
        assertEquals(1999, created.getYear());

        assertEquals(1, store.getStore().size());
        assertEquals(created, store.getStore().getFirst());
    }

    @Test
    @DisplayName("POST /movies should return 422 when year is out of valid range")
    void shouldReturn422_whenYearOutOfRange() throws Exception {
        String jsonBody = "{\"title\":\"Old Movie\",\"year\":1885}";
        HttpResponse<String> response = sendPost(jsonBody);
        assertErrorResponse(response, 422);
    }

    @Test
    @DisplayName("POST /movies should return 422 when title is too long")
    void shouldReturn422_whenTitleTooLong() throws Exception {
        String veryLongTitle = "a".repeat(300);
        String jsonBody = String.format("{\"title\":\"%s\",\"year\":1996}", veryLongTitle);
        HttpResponse<String> response = sendPost(jsonBody);
        assertErrorResponse(response, 422);
    }

    @Test
    @DisplayName("POST /movies should return 415 when Content-Type header is missing")
    void shouldReturn415_whenContentTypeMissing() throws Exception {
        String jsonBody = "{\"title\":\"No Content-Type\",\"year\":1999}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertErrorResponse(response, 415);
    }

    @Test
    @DisplayName("POST /movies should return 422 when title is empty")
    void shouldReturn422_whenTitleIsEmpty() throws Exception {
        String jsonBody = "{\"title\":\"\",\"year\":2000}";
        HttpResponse<String> response = sendPost(jsonBody);
        assertErrorResponse(response, 422);

        ErrorResponse errorResponse = gson.fromJson(response.body(), ErrorResponse.class);
        assertEquals("Ошибка валидации", errorResponse.getError());
        assertTrue(errorResponse.getDetails().stream().anyMatch(d -> d.contains("Название не должно быть пустым")));
    }

    @Test
    @DisplayName("POST /movies should return 422 when title contains only whitespaces")
    void shouldReturn422_whenTitleIsWhitespace() throws Exception {
        String jsonBody = "{\"title\":\"   \",\"year\":2000}";
        HttpResponse<String> response = sendPost(jsonBody);
        assertErrorResponse(response, 422);

        ErrorResponse errorResponse = gson.fromJson(response.body(), ErrorResponse.class);
        assertEquals("Ошибка валидации", errorResponse.getError());
        assertTrue(errorResponse.getDetails().stream().anyMatch(d -> d.contains("Название не должно быть пустым")));
    }

    @Test
    @DisplayName("POST /movies should return 400 when JSON is malformed")
    void shouldReturn400_whenJsonIsMalformed() throws Exception {
        String invalidJson = "{\"title\":\"Broken\", \"year\":1999"; // отсутствует закрывающая скобка
        HttpResponse<String> response = sendPost(invalidJson);
        assertErrorResponse(response, 400);
    }

    @Test
    @DisplayName("POST /movies should return 400 when request body is empty")
    void shouldReturn400_whenBodyIsEmpty() throws Exception {
        HttpResponse<String> response = sendPost("");
        assertErrorResponse(response, 400);
    }

    @Test
    @DisplayName("DELETE /movies/{id} should return 204 when movie deleted")
    void shouldReturn204_whenDeletingExistingMovie() throws Exception {
        Movie movie = addMovieToStore("To Delete", 2000);
        long id = movie.getId();

        HttpResponse<String> response = sendDelete("/movies/" + id);

        assertSuccessResponse(response, 204);
        assertTrue(store.getStore().isEmpty(), "Movie should be removed from store");
    }

    @Test
    @DisplayName("DELETE /movies/{id} should return 400 when id is invalid")
    void shouldReturn400_whenDeletingWithInvalidId() throws Exception {
        HttpResponse<String> response = sendDelete("/movies/abc");
        assertErrorResponse(response, 400);
    }

    @Test
    @DisplayName("DELETE /movies/{id} should return 404 when movie does not exist")
    void shouldReturn404_whenDeletingNonExistentMovie() throws Exception {
        HttpResponse<String> response = sendDelete("/movies/1");
        assertErrorResponse(response, 404);
    }

    @Test
    @DisplayName("GET /movies?year=YYYY should return movies filtered by year")
    void shouldReturnMoviesFilteredByYear_whenYearValid() throws Exception {
        Movie addedFirst = addMovieToStore("Movie A", 1999);
        Movie addedSecond = addMovieToStore("Movie B", 1999);
        addMovieToStore("Movie C", 1998);

        HttpResponse<String> response = sendGet("/movies?year=1999");

        assertSuccessResponse(response, 200);
        Movie[] result = gson.fromJson(response.body(), Movie[].class);
        assertEquals(2, result.length, "Should return exactly two movies from 1999");
        assertTrue(List.of(result).containsAll(List.of(addedFirst, addedSecond)),
                "Filtered movies should match stored ones");
    }

    @Test
    @DisplayName("GET /movies?year=YYYY should return 400 when year parameter is invalid")
    void shouldReturn400_whenYearParameterInvalid() throws Exception {
        HttpResponse<String> response = sendGet("/movies?year=invalidStr");
        assertErrorResponse(response, 400);
    }

    @Test
    @DisplayName("GET /movies?year=YYYY should return empty array when no movies for that year")
    void shouldReturnEmptyArray_whenNoMoviesForYear() throws Exception {
        addMovieToStore("Movie", 2000);
        HttpResponse<String> response = sendGet("/movies?year=1999");
        assertSuccessResponse(response, 200);
        assertEquals("[]", response.body(), "Should return empty array when no matches");
    }

    @Test
    @DisplayName("GET /movies?year=YYYY should return 400 when year parameter has no value")
    void shouldReturn400_whenYearParameterHasNoValue() throws Exception {
        HttpResponse<String> response = sendGet("/movies?year=");
        assertErrorResponse(response, 400);
    }

    @Test
    @DisplayName("PUT /movies should return 405 Method Not Allowed")
    void shouldReturn405_whenMethodNotAllowed() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertErrorResponse(response, 405);
    }

    @Test
    @DisplayName("DELETE /movies with non-numeric id returns 400")
    void shouldReturn400_whenDeletingWithNonNumericId() throws Exception {
        HttpResponse<String> response = sendDelete("/movies/abc");
        assertErrorResponse(response, 400);
    }

    @Test
    @DisplayName("GET /movies?year=YYYY should return movies for boundary years (1888 and current+1)")
    void shouldReturnMoviesForBoundaryYears() throws Exception {
        int minYear = 1888;
        int maxYear = Year.now().getValue() + 1;

        Movie minMovie = addMovieToStore("Min Year Movie", minYear);
        Movie maxMovie = addMovieToStore("Max Year Movie", maxYear);

        HttpResponse<String> responseMin = sendGet("/movies?year=" + minYear);
        assertSuccessResponse(responseMin, 200);
        Movie[] minResult = gson.fromJson(responseMin.body(), Movie[].class);
        assertEquals(1, minResult.length);
        assertEquals(minMovie, minResult[0]);

        HttpResponse<String> responseMax = sendGet("/movies?year=" + maxYear);
        assertSuccessResponse(responseMax, 200);
        Movie[] maxResult = gson.fromJson(responseMax.body(), Movie[].class);
        assertEquals(1, maxResult.length);
        assertEquals(maxMovie, maxResult[0]);
    }
}