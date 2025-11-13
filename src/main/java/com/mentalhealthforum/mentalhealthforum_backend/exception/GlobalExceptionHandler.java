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
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;


@ControllerAdvice
public class GlobalExceptionHandler {
    private final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private String getPath(WebRequest request) {
        if(request instanceof ServletRequestAttributes servletRequestAttributes){
            return servletRequestAttributes.getRequest().getRequestURI();
        }
        return request.getDescription(false);
    }

    /**
     * 1. Handles Custom Business Exceptions (MhfApiException)
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<StandardErrorResponse> handleApiException(
            ApiException ex,
            WebRequest request){
        String path = getPath(request);
        logger.error("Api Exception occurred at path '{}': {}", path, ex.getMessage(), ex);
        StandardErrorResponse response = new StandardErrorResponse(
             ex.getMessage(),
             ex.getErrorCode(),
             path,
             ex.getDetails()
        );
        return ResponseEntity.status(ex.getErrorCode().getHttpStatus()).body(response);
    }
    /**
     * 2. Handles DTO Validation Failures (@Valid annotation failure)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<StandardErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex,
            WebRequest request){
        String path = getPath(request);
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
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getHttpStatus()).body(response);
    }
    /**
     * 3. Handles Path/Query Parameter Type Mismatches
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<StandardErrorResponse> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException ex,
            WebRequest request
    ){
        String path = getPath(request);
        logger.warn("Type mismatch Exception occured at path '{}'", path);

        String expectedType = ex.getRequiredType() != null? ex.getRequiredType().getSimpleName() : "Unknown";
        String param = ex.getParameter().getParameterName() != null ? ex.getParameter().getParameterName() : "Unknown";
        Object value = ex.getValue();

        String message = String.format("Failed to convert parameter '%s'. Expected type '%s', received value: '%s'.",
                param, expectedType, value);


        StandardErrorResponse response = new StandardErrorResponse(
                message,
                ErrorCode.VALIDATION_FAILED,
                path,
                null
        );
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getHttpStatus()).body(response);
    }
    /**
     * 4. Handles JSON/Request Body Readability Issues
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<StandardErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex,
            WebRequest request){
        String path = getPath(request);
        logger.warn("Message Not Readable Exception occured at path '{}'", path);

        ErrorDetail detail;
        Throwable cause = ex.getCause();

        if(cause instanceof InvalidFormatException invalidFormatException){
            String fieldName = invalidFormatException.getPath().stream()
                    .map(JsonMappingException.Reference::getFieldName)
                    .filter(Objects::nonNull)
                    .findFirst().orElse("unknown");

            String expectedValues = "";
            if(invalidFormatException.getTargetType() != null && invalidFormatException.getTargetType().isEnum()){

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
        else if(cause instanceof MismatchedInputException mismatchedInputException){
            String fieldName = mismatchedInputException.getPath().stream()
                    .map(JsonMappingException.Reference::getFieldName)
                    .filter(Objects::nonNull)
                    .findFirst().orElse("body");
            detail = new ErrorDetail(
                    fieldName,
                    String.format("Missing or incorrect value for field '%s'. Expected a valid input type.", fieldName)
            );
        }
        else if(cause instanceof JsonParseException jsonParseException){
            detail = new ErrorDetail(
                    "body",
                    "Malformed JSON: " + jsonParseException.getOriginalMessage()
            );
        }
        else if(cause instanceof JsonMappingException jsonMappingException){
            detail = new ErrorDetail(
                    "body",
                    "JSON Mapping Error: " + jsonMappingException.getOriginalMessage()
            );
        }
        else {
            detail = new ErrorDetail(
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
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getHttpStatus()).body(response);
    }
    /**
     * 5. Handles MissingServletRequestParameterException (missing @RequestParam).
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<StandardErrorResponse> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException ex,
            WebRequest request){

        String path = getPath(request);
        logger.warn("Missing request parameter exception occurred at path '{}'", path);

        String parameterName = ex.getParameterName();
        String parameterType = ex.getParameterType();
        String detailName = String.format("Required parameter '%s' of type '%s' is missing.", parameterName, parameterType);

        ErrorDetail detail = new ErrorDetail(
                parameterName,
                detailName
        );


        StandardErrorResponse response = new StandardErrorResponse(
                ErrorCode.VALIDATION_FAILED.getDescription(),
                ErrorCode.VALIDATION_FAILED,
                path,
                List.of(detail)
        );
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getHttpStatus()).body(response);
    }
    /**
     * 6. Handles ConstraintViolationException (Spring @Validated failures on method arguments).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<StandardErrorResponse> handleConstraintViolationException(
            ConstraintViolationException ex,
            WebRequest request){

        String path = getPath(request);
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
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getHttpStatus()).body(response);
    }
    /**
     * 7. Handles 404 Not Found
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<StandardErrorResponse> handleNoResourceFoundException(
            NoResourceFoundException ex,
            WebRequest request){
        String path = getPath(request);
        logger.warn("No Resource Found Exception occurred at path '{}'", path);

        String message = String.format("The requested resource '%s' was not found.", path);

        StandardErrorResponse response = new StandardErrorResponse(
                ErrorCode.RESOURCE_NOT_FOUND.getDescription(),
                ErrorCode.RESOURCE_NOT_FOUND,
                path,
                null
        );
        return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.getHttpStatus()).body(response);
    }
    /**
     * 8. Handles All Other Uncaught Exceptions (Fallback 500 handler)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<StandardErrorResponse> handleGenericException(
            Exception ex,
            WebRequest request
            ){
        String path = getPath(request);
        logger.error("An uncaught error occurred at path '{}'", path, ex);

        StandardErrorResponse response = new StandardErrorResponse(
                ErrorCode.INTERNAL_SERVER_ERROR.getDescription(),
                ErrorCode.INTERNAL_SERVER_ERROR,
                path,
                null
        );
        return ResponseEntity.internalServerError().body(response);
    }
}
