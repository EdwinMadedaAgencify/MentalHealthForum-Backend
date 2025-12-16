package com.mentalhealthforum.mentalhealthforum_backend.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ErrorDetail;
import com.mentalhealthforum.mentalhealthforum_backend.dto.StandardErrorResponse;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.ApiException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.support.WebExchangeBindException; // Reactive Validation Exception
import org.springframework.web.server.*;
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
     * 4b. Handle JSON deserialization / enum mapping errors (WebFlux-specific)
     */
    @ExceptionHandler(ServerWebInputException.class)
    public Mono<ResponseEntity<StandardErrorResponse>> handleServerWebInputException(
            ServerWebInputException ex,
            ServerWebExchange exchange){

        String path = getPath(exchange);
        logger.warn("ServerWebInputException at '{}': {}", path, ex.getMessage());

        Throwable cause = ex.getCause();
        ErrorDetail detail = null;

        // Unwrap DecodingException (WebFlux wrapper for Jackson decoding errors)
        if(cause instanceof org.springframework.core.codec.DecodingException decodingException){
            Throwable decodingCause = decodingException.getCause();

            if(decodingCause instanceof com.fasterxml.jackson.databind.exc.InvalidFormatException invalidFormatException){
                //Handle enum / type mismatch
                String fieldName = invalidFormatException.getPath().stream()
                        .map(JsonMappingException.Reference::getFieldName)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse("body");

                String expected = "";
                if(invalidFormatException.getTargetType() != null && invalidFormatException.getTargetType().isEnum()){
                    // Generic enum handling
                    Class <?> enumClass = invalidFormatException.getTargetType();
                    Object[] constants = enumClass.getEnumConstants();

                    // show user-friendly enum paths instead of raw names
                    expected = "Expected one of: [" +
                            Arrays.stream(constants)
                                    .map(Object::toString)
                                    .toList()
                            + "]";
                }

                detail = new ErrorDetail(
                        fieldName,
                        String.format("Invalid value '%s' for field '%s'. %s",
                                invalidFormatException.getValue(), fieldName, expected)
                );
            }
            else if(decodingCause instanceof com.fasterxml.jackson.databind.exc.MismatchedInputException mismatchedInputException){
                // Generic type mismatch / missing field
                String fieldName = mismatchedInputException.getPath().stream()
                        .map(JsonMappingException.Reference::getFieldName)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse("body");

                detail = new ErrorDetail(fieldName, "Missing or Invalid value.");
            }
            else {
                // Other decoding problems
                detail = new ErrorDetail("body", decodingException.getMessage());
            }
        }
        else {
            // Fallback for other ServerWebInputException causes
            detail = new ErrorDetail("body", cause != null? cause.getMessage(): ex.getMessage());
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
     * 6. Handles Method-Level Security AuthorizationDeniedException (403)  Exception
     */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public Mono<ResponseEntity<StandardErrorResponse>> handleAuthorizationDeniedException(
            AuthorizationDeniedException ex,
            ServerWebExchange exchange){

        String path = getPath(exchange);
        logger.warn("AuthorizationDeniedException occurred at '{}': {}", path, ex.getMessage());

        StandardErrorResponse response = new StandardErrorResponse(
                "Forbidden: Insufficient permissions for this resource.",
                ErrorCode.FORBIDDEN,
                path,
                null
        );

        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(response));
    }
    /**
     * 7. Handles WebFlux specific exceptions like 404/405 (Not Found / Method Not Allowed)
     * Handles exceptions that are wrapped by WebFlux like NoResourceFoundException and MissingServletRequestParameterException.
     */
    @ExceptionHandler({MethodNotAllowedException.class, ResponseStatusException.class})
    public Mono<ResponseEntity<StandardErrorResponse>> handleWebFluxErrors(
            ResponseStatusException ex,
            ServerWebExchange exchange){

        String path = getPath(exchange);
        logger.warn("MethodNotAllowedException occurred at path '{}'", path);
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
        else if(status == HttpStatus.BAD_REQUEST){
            errorCode = ErrorCode.VALIDATION_FAILED;
            message = ex.getReason() != null? ex.getReason(): ErrorCode.VALIDATION_FAILED.getDescription();
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
     * 8. Handle external service connection errors (Connection refused, host down, etc.)
     * Returns 503 Service Unavailable
     */
    @ExceptionHandler({
            jakarta.ws.rs.ProcessingException.class,
            org.springframework.web.reactive.function.client.WebClientRequestException.class,
            java.net.ConnectException.class,
            java.net.SocketException.class,
    })
    public Mono<ResponseEntity<StandardErrorResponse>> handleDownstreamConnectionErrors(
            Exception ex, ServerWebExchange exchange) {

        String path = getPath(exchange);
        logger.error("Downstream service connection error at '{}': {}", path, ex.getMessage());

        StandardErrorResponse response = new StandardErrorResponse(
                "Downstream service is temporarily unavailable. Please try again later.",
                ErrorCode.SERVICE_UNAVAILABLE,
                path,
                null
        );

        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response));
    }
    /**
     * 9. Handle external service timeout errors
     * Returns 504 Gateway Timeout
     */
    @ExceptionHandler({
           java.net.SocketTimeoutException.class,
           java.util.concurrent.TimeoutException.class
    })
    public Mono<ResponseEntity<StandardErrorResponse>> handleDownstreamServiceTimeoutErrors(
            Exception ex, ServerWebExchange exchange) {

        String path = getPath(exchange);
        logger.error("Downstream service timeout error at '{}': {}", path, ex.getMessage());

        StandardErrorResponse response = new StandardErrorResponse(
                "Downstream service is taking too long to respond. Please try again.",
                ErrorCode.GATEWAY_TIMEOUT,
                path,
                null
        );

        return Mono.just(ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(response));
    }
    /**
     * 11. Handles All Other Uncaught Exceptions (Fallback 500 handler)
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
