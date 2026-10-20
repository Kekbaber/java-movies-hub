package ru.practicum.moviehub.api;

import java.util.ArrayList;
import java.util.List;

public class ErrorResponse {
    private final String error;
    private final List<String> details;
    private final int code;

    public ErrorResponse(int code, String error) {
        this.code = code;
        this.error = error;
        this.details = new ArrayList<>();
    }

    public ErrorResponse(int code, String error, List<String> details) {
        this.code = code;
        this.error = error;
        this.details = details;
    }

    public int getCode() {
        return code;
    }

    public String getError() {
        return error;
    }

    public List<String> getDetails() {
        return new ArrayList<>(details);
    }
}