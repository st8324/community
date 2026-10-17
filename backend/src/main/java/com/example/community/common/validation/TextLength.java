package com.example.community.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 글자 수 검사 — <b>유니코드 문자(코드 포인트)</b> 기준 (01 문서 D-22). {@code null}은 실패로 본다.
 *
 * <p>{@code @Size}는 UTF-16 단위로 세서 이모지 1개를 2자로 센다. MySQL {@code VARCHAR(n)}(utf8mb4)는 문자 수 기준이므로 이
 * 검사와 DB 한도가 일치한다. 공백 제거(strip)는 요청 레코드 생성자에서 먼저 하고, 이 검사는 제거된 값을 본다. 오류 메시지에 입력값을 넣지 않는다 (SEC-10).
 */
@Documented
@Constraint(validatedBy = TextLength.Validator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TextLength {

  int min();

  int max();

  String message() default "글자 수가 허용 범위를 벗어났습니다.";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  class Validator implements ConstraintValidator<TextLength, String> {
    private int min;
    private int max;

    @Override
    public void initialize(TextLength annotation) {
      this.min = annotation.min();
      this.max = annotation.max();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
      if (value == null) {
        return false;
      }
      // 코드 포인트 수 ≤ UTF-16 길이 ≤ 코드 포인트 수 × 2. 아주 긴 입력은 세기 전에 거른다.
      if (value.length() > max * 2) {
        return false;
      }
      int count = value.codePointCount(0, value.length());
      return count >= min && count <= max;
    }
  }
}
