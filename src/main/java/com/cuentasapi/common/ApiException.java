package com.cuentasapi.common;

import org.springframework.http.HttpStatus;

/** An error the client caused; rendered as application/problem+json with the given status. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String title) {
        super(title);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static ApiException notFound(String title) {
        return new ApiException(HttpStatus.NOT_FOUND, title);
    }

    public static ApiException conflict(String title) {
        return new ApiException(HttpStatus.CONFLICT, title);
    }

    public static ApiException unprocessable(String title) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, title);
    }

    public static ApiException badRequest(String title) {
        return new ApiException(HttpStatus.BAD_REQUEST, title);
    }

    public static ApiException unauthorized(String title) {
        return new ApiException(HttpStatus.UNAUTHORIZED, title);
    }
}
