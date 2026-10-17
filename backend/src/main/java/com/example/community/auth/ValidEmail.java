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
 * AUTH-01 ⑥ 이메일 형식 검증. 값은 요청 레코드 생성자에서 이미 정규화돼 있어야 한다.
 *
 * <p>대상은 FIELD만: 레코드 컴포넌트에 붙였을 때 필드와 접근자에 중복 적용돼 오류가 두 번 나오는 것을 막는다.
 */
@Documented
@Constraint(validatedBy = ValidEmail.Validator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidEmail {

  String message() default "이메일 형식이 올바르지 않습니다.";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  class Validator implements ConstraintValidator<ValidEmail, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
      return EmailRules.isValid(value);
    }
  }
}
