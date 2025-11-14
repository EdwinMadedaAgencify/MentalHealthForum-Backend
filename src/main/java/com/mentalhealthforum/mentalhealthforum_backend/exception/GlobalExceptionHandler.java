package com.mentalhealthforum.mentalhealthforum_backend.exception;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ErrorDetail;
import com.mentalhealthforum.mentalhealthforum_backend.dto.StandardErrorResponse;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.ApiException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.support.WebExchangeBindException; // Reactive Validation Exception
import org.springframework.web.server.MethodNotAllowedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange; // Reactive Context
import org.springframework.web.server.UnsupportedMediaTypeStatusException;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;


@ControllerAdvice
public class GlobalExceptionHandler {
    private final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Helper to get the request path from the reactive context.
     */
    private String getPath(ServerWebExchange exchange) {
        return exchange.getRequest().getURI().getPath();
    }

    /**
     * 1. Handles Custom Business Exceptions (ApiException)
     * Returns Mono<ResponseEntity>
     */
    @ExceptionHandler(ApiException.class)
    public Mono<ResponseEntity<StandardErrorResponse>> handleApiException(
            ApiException ex,
            ServerWebExchange exchange){
        String path = getPath(exchange);
        logger.error("Api Exception occurred at path '{}': {}", path, ex.getMessage(), ex);
        StandardErrorResponse response = new StandardErrorResponse(
                ex.getMessage(),
                ex.getErrorCode(),
                path,
                ex.getDetails()
        );
        return Mono.just(ResponseEntity.status(ex.getErrorCode().getHttpStatus()).body(response));
    }
    /**
     * 2. Handles DTO Validation Failures (@Valid annotation failure in WebFlux)
     * Uses WebExchangeBindException (WebFlux native) instead of MethodArgumentNotValidException (MVC native).
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<StandardErrorResponse>> handleWebExchangeBindException(
            WebExchangeBindException ex,
            ServerWebExchange exchange){

        String path = getPath(exchange);
        logger.warn("Validation Exception occured at path '{}'", path);

        List<ErrorDetail> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new ErrorDetail(
                        fieldError.getField(),
                        fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage(): "Invalid value"
                ))
                .toList();

        StandardErrorResponse response = new StandardErrorResponse(
                ErrorCode.VALIDATION_FAILED.getDescription(),
                ErrorCode.VALIDATION_FAILED,
                path,
                errors
        );
        return Mono.just(ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getHttpStatus()).body(response));
    }
    /**
     * 3. Handles Path/Query Parameter Type Mismatches
     * Delegates to the reactive WebExchangeBindException handler.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Mono<ResponseEntity<StandardErrorResponse>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex,
            ServerWebExchange exchange){
        // FIX APPLIED HERE: We now pass the MethodParameter (ex.getParameter()) and BindingResult
        return handleWebExchangeBindException(new WebExchangeBindException(ex.getParameter(), ex.getBindingResult()), exchange);
    }

    /**
     * 4a. Handles HttpMediaTypeNotSupportedException (NEW: Unsupported Content-Type, returns 415)
     */
    @ExceptionHandler(UnsupportedMediaTypeStatusException.class)
    public Mono<ResponseEntity<StandardErrorResponse>> handleUnsupportedMediaTypeStatusException(
            UnsupportedMediaTypeStatusException ex,
            ServerWebExchange exchange){

        String path = getPath(exchange);
        logger.warn("Media Type Not Supported Exception occurred at path '{}': {}", path, ex.getMessage());

        StandardErrorResponse response = new StandardErrorResponse(
                ErrorCode.UNSUPPORTED_MEDIA_TYPE.getDescription(),
                ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                path,
                List.of(new ErrorDetail("ContentType", ex.getMessage()))
        );
        return Mono.just(ResponseEntity.status(ErrorCode.UNSUPPORTED_MEDIA_TYPE.getHttpStatus()).body(response));
    }
    /**
     * 4b. Handles JSON/Request Body Readability Issues (HttpMessageNotReadableException)
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Mono<ResponseEntity<StandardErrorResponse>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex,
            ServerWebExchange exchange){

        String path = getPath(exchange);
        logger.warn("Message Not Readable Exception occured at path '{}'", path);

        ErrorDetail detail;
        Throwable cause = ex.getCause();

        switch (cause) {
            case InvalidFormatException invalidFormatException -> {
                String fieldName = invalidFormatException.getPath().stream()
                        .map(JsonMappingException.Reference::getFieldName)
                        .filter(Objects::nonNull)
                        .findFirst().orElse("unknown");

                String expectedValues = "";
                if (invalidFormatException.getTargetType() != null && invalidFormatException.getTargetType().isEnum()) {

                    List<String> enumNames = Arrays.stream(invalidFormatException.getTargetType().getEnumConstants())
                            .map(Object::toString)
                            .toList();

                    expectedValues = "Expected one of: [" + String.join(", ", enumNames) + "]";
                }

                detail = new ErrorDetail(
                        fieldName,
                        String.format("Invalid value '%s' for field '%s'. %s", invalidFormatException.getValue(), fieldName, expectedValues)
                );
            }
            case MismatchedInputException mismatchedInputException -> {
                String fieldName = mismatchedInputException.getPath().stream()
                        .map(JsonMappingException.Reference::getFieldName)
                        .filter(Objects::nonNull)
                        .findFirst().orElse("body");
                detail = new ErrorDetail(
                        fieldName,
                        String.format("Missing or incorrect value for field '%s'. Expected a valid input type.", fieldName)
                );
            }
            case JsonParseException jsonParseException -> detail = new ErrorDetail(
                    "body",
                    "Malformed JSON: " + jsonParseException.getOriginalMessage()
            );
            case JsonMappingException jsonMappingException -> detail = new ErrorDetail(
                    "body",
                    "JSON Mapping Error: " + jsonMappingException.getOriginalMessage()
            );
            case null, default -> detail = new ErrorDetail(
                    "body",
                    "Malformed request body or invalid input format."
            );
        }

        StandardErrorResponse response = new StandardErrorResponse(
                ErrorCode.VALIDATION_FAILED.getDescription(),
                ErrorCode.VALIDATION_FAILED,
                path,
                List.of(detail)
        );
        return Mono.just(ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getHttpStatus()).body(response));
    }
    /**
     * 5. Handles ConstraintViolationException (Spring @Validated failures on method arguments).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Mono<ResponseEntity<StandardErrorResponse>> handleConstraintViolationException(
            ConstraintViolationException ex,
            ServerWebExchange exchange){

        String path = getPath(exchange);
        logger.warn("Constraint Violation Exception occurred at path '{}'", path);

        List<ErrorDetail> errors = ex.getConstraintViolations().stream()
                .map(violation -> {
                    String field = violation.getPropertyPath().toString();
                    String simpleField = field.substring(field.lastIndexOf('.')+1);

                    return new ErrorDetail(simpleField, violation.getMessage());
                })
                .toList();

        StandardErrorResponse response = new StandardErrorResponse(
                ErrorCode.VALIDATION_FAILED.getDescription(),
                ErrorCode.VALIDATION_FAILED,
                path,
                errors
        );
        return Mono.just(ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getHttpStatus()).body(response));
    }
    /**
     * 6. Handles WebFlux specific exceptions like 404/405 (Not Found / Method Not Allowed)
     * Handles exceptions that are wrapped by WebFlux like NoResourceFoundException and MissingServletRequestParameterException.
     */
    @ExceptionHandler({MethodNotAllowedException.class, ResponseStatusException.class})
    public Mono<ResponseEntity<StandardErrorResponse>> handleWebFluxErrors(
            ResponseStatusException ex,
            ServerWebExchange exchange){

        String path = getPath(exchange);
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        ErrorCode errorCode;
        String message;

        if(status == HttpStatus.NOT_FOUND){
            errorCode = ErrorCode.RESOURCE_NOT_FOUND;
            message = String.format("The requested resource '%s' was not found.", path);
        }
        else if(status == HttpStatus.METHOD_NOT_ALLOWED){
            errorCode = ErrorCode.METHOD_NOT_ALLOWED;
            message = String.format("Method not allowed on resource '%s'", path);
        }
        else {
            // Catch other ResponseStatusExceptions (e.g., 400 Bad Request if thrown explicitly)
            errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
            message = ex.getReason() != null? ex.getReason(): "An unexpected server error occurred.";
        }

        StandardErrorResponse response = new StandardErrorResponse(
                message,
                errorCode,
                path,
                null
        );

        return Mono.just(ResponseEntity.status(status != null? status: HttpStatus.INTERNAL_SERVER_ERROR).body(response));
    }
    /**
     * 7. Handles All Other Uncaught Exceptions (Fallback 500 handler)
     */
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<StandardErrorResponse>> handleGenericException(
            Exception ex,
            ServerWebExchange exchange
    ){

        String path = getPath(exchange);
        logger.error("An uncaught error occurred at path '{}'", path, ex);

        StandardErrorResponse response = new StandardErrorResponse(
                ErrorCode.INTERNAL_SERVER_ERROR.getDescription(),
                ErrorCode.INTERNAL_SERVER_ERROR,
                path,
                null
        );
        return Mono.just(ResponseEntity.internalServerError().body(response));
    }
}