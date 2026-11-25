package com.dineflow.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

/** Mọi lỗi đều trả JSON cùng một khuôn (giống phần withExceptions trong bootstrap/app.php). */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException e) {
        return ApiResponse.error(e.getMessage(), e.getStatus(), e.getErrors());
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> handleNotFound(Exception e) {
        return ApiResponse.error("Không tìm thấy dữ liệu yêu cầu.", 404, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethod(Exception e) {
        return ApiResponse.error("Phương thức không được hỗ trợ.", 405, null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleBadJson(Exception e) {
        return ApiResponse.error("Dữ liệu gửi lên không phải JSON hợp lệ.", 400, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception e) {
        log.error("Lỗi không mong muốn", e);
        return ApiResponse.error("Đã có lỗi xảy ra.", 500, null);
    }
}
