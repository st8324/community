package com.example.community.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 API (02 문서 5.3절). 컨트롤러는 DTO 매핑과 {@code @Valid}만 한다 (02 문서 5.2절). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  /** AUTH-01: 201, 본문 없음, 토큰 발급 없음 (D-10). */
  @PostMapping("/signup")
  public ResponseEntity<Void> signup(@Valid @RequestBody SignupRequest request) {
    authService.signup(request);
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }
}
