package com.example.community.common.error;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 컨트롤러 단계 예외 → 표준 오류 응답 (SEC-10, 01 문서 4.5절).
 *
 * <p>원칙: 예외 메시지를 응답에 옮기지 않는다. 프레임워크 예외의 메시지에는 클래스명·입력값·SQL이 들어 있을 수 있다. 보안 필터 단계(인증·CSRF)의 오류는
 * {@code SecurityErrorResponses}가 같은 형식으로 만든다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ErrorResponse> handleApi(ApiException e) {
    return respond(e.code(), e.fields());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
    List<String> fields =
        e.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getField)
            .distinct()
            .sorted()
            .toList();
    return respond(ErrorCode.VALIDATION_FAILED, fields);
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ErrorResponse> handleUnreadable(Exception e) {
    return respond(ErrorCode.INVALID_REQUEST);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException e) {
    return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException e) {
    return respond(ErrorCode.METHOD_NOT_ALLOWED);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException e) {
    return respond(ErrorCode.NOT_FOUND);
  }

  // 컨트롤러 안에서 던져진 보안 예외가 아래 Exception 처리기에 잡혀 500이 되지 않도록 한다.
  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException e) {
    return respond(ErrorCode.UNAUTHORIZED);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
    return respond(ErrorCode.FORBIDDEN);
  }

  /** 예상하지 못한 오류: 서버 로그에는 원인을, 응답에는 고정 문장만. 이 코드에는 비밀번호·토큰을 담는 예외가 없다 (SEC-11). */
  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
    log.error("처리되지 않은 예외: {}", e.getClass().getName(), e);
    return respond(ErrorCode.INTERNAL_ERROR);
  }

  private static ResponseEntity<ErrorResponse> respond(ErrorCode code) {
    return ResponseEntity.status(code.status()).body(ErrorResponse.of(code));
  }

  private static ResponseEntity<ErrorResponse> respond(ErrorCode code, List<String> fields) {
    return ResponseEntity.status(code.status()).body(ErrorResponse.of(code, fields));
  }
}
