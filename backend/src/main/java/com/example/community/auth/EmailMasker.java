package com.example.community.auth;

/**
 * 작성자 표시명 = 마스킹한 이메일 (A-1, DATA-05). 로컬부가 3자 이상이면 앞 2자, 2자면 1자, 1자면 0자만 남긴다.
 *
 * <p>예: {@code abcdef@example.com → ab***@example.com}, {@code a@example.com → ***@example.com}
 */
public final class EmailMasker {

  private EmailMasker() {}

  public static String mask(String email) {
    int at = email.lastIndexOf('@');
    String local = email.substring(0, at);
    int keep = local.length() >= 3 ? 2 : local.length() - 1;
    return local.substring(0, keep) + "***" + email.substring(at);
  }
}
