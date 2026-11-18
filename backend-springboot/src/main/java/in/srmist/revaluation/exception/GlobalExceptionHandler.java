package in.srmist.revaluation.exception;

import io.camunda.client.api.command.ClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns exceptions into one JSON error shape: {"message": "...", "fieldErrors": {...}}.
 * The React portal shows "message" in a toast and "fieldErrors" next to the inputs.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** jakarta.validation failed on a request body (@Valid). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> invalidBody(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return body(HttpStatus.BAD_REQUEST, "Please correct the highlighted fields.", fields);
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, Object>> api(ApiException ex) {
        return body(ex.getStatus(), ex.getMessage(), Map.of());
    }

    /** Camunda rejected a command (for example: task already completed) or could not be reached. */
    @ExceptionHandler(ClientException.class)
    ResponseEntity<Map<String, Object>> camunda(ClientException ex) {
        log.warn("Camunda call failed: {}", ex.getMessage());
        return body(HttpStatus.BAD_GATEWAY, "Camunda rejected the request: " + ex.getMessage(), Map.of());
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message, Map<String, String> fields) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("fieldErrors", fields);
        return ResponseEntity.status(status).body(body);
    }
}
