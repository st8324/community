package com.example.community.auth;

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
 * SEC-03 비밀번호 검증. {@code forSignup=true}면 가입 정책 전체, false면 로그인용 상한 검사만 (AUTH-02 ⑤).
 *
 * <p>오류 메시지에 입력값을 넣지 않는다 (SEC-10, SEC-11).
 */
@Documented
@Constraint(validatedBy = ValidPassword.Validator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

  boolean forSignup();

  String message() default "비밀번호는 8~64자, 72바이트 이하여야 합니다.";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  class Validator implements ConstraintValidator<ValidPassword, String> {
    private boolean forSignup;

    @Override
    public void initialize(ValidPassword annotation) {
      this.forSignup = annotation.forSignup();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
      return forSignup
          ? PasswordRules.isValidForSignup(value)
          : PasswordRules.isWithinUpperBounds(value);
    }
  }
}
