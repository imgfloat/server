package dev.kruhlmann.imgfloat.controller;

import dev.kruhlmann.imgfloat.model.api.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class RestExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(RestExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleStatusException(
        ResponseStatusException exception,
        HttpServletRequest request
    ) {
        String path = request.getRequestURI();
        HttpStatusCode statusCode = exception.getStatusCode();
        LOG.warn(
            "Request {} {} failed with status {}: {}",
            request.getMethod(),
            path,
            statusCode.value(),
            exception.getReason(),
            exception
        );
        String message = exception.getReason();
        if (message == null || message.isBlank()) {
            message = "Request failed with status " + statusCode.value();
        }
        return ResponseEntity.status(statusCode).body(new ErrorResponse(statusCode.value(), message, path));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception, HttpServletRequest request) {
        String path = request.getRequestURI();
        LOG.error(
            "Unhandled exception while processing {} {}: {}",
            request.getMethod(),
            path,
            exception.getMessage(),
            exception
        );
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(
                new ErrorResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "An unexpected error occurred while handling the request.",
                    path
                )
            );
    }
}
