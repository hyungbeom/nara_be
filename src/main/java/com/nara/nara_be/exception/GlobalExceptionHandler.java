package com.nara.nara_be.exception;

import com.nara.nara_be.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        return ResponseEntity
                .status(e.getStatus())
                .body(ApiResponse.fail(e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("Invalid request body: {}", e.getMessage());
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.fail("요청 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler({
            ClientAbortException.class,
            AsyncRequestNotUsableException.class
    })
    public void handleClientAbort(Exception e) {
        log.debug("Client disconnected before response completed: {}", e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotWritableException.class)
    public void handleHttpMessageNotWritable(HttpMessageNotWritableException e) {
        if (isClientAbort(e)) {
            log.debug("Client disconnected during JSON serialization: {}", e.getMessage());
            return;
        }
        log.error("Failed to write HTTP response", e);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        if (isClientAbort(e)) {
            log.debug("Client disconnected: {}", e.getMessage());
            return null;
        }
        log.error("Unhandled server error", e);
        return ResponseEntity
                .internalServerError()
                .body(ApiResponse.fail("서버 오류가 발생했습니다."));
    }

    private boolean isClientAbort(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ClientAbortException
                    || current instanceof AsyncRequestNotUsableException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
