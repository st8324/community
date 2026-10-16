package com.example.community.support;

/**
 * 요청 본문용 아주 작은 JSON 객체 생성기 (키·값 쌍 순서대로).
 *
 * <p>테스트가 JSON 라이브러리 버전(Jackson 2/3)에 묶이지 않게 직접 만든다. 값은 문자열·불리언·숫자·null만 지원한다.
 */
public final class JsonBodies {

  private JsonBodies() {}

  public static String of(Object... keyValues) {
    if (keyValues.length % 2 != 0) {
      throw new IllegalArgumentException("키·값 쌍이 맞지 않는다");
    }
    StringBuilder sb = new StringBuilder("{");
    for (int i = 0; i < keyValues.length; i += 2) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(quote((String) keyValues[i])).append(':');
      Object value = keyValues[i + 1];
      if (value == null) {
        sb.append("null");
      } else if (value instanceof Boolean || value instanceof Number) {
        sb.append(value);
      } else if (value instanceof String s) {
        sb.append(quote(s));
      } else {
        throw new IllegalArgumentException("지원하지 않는 값 타입: " + value.getClass());
      }
    }
    return sb.append('}').toString();
  }

  private static String quote(String s) {
    StringBuilder sb = new StringBuilder("\"");
    for (char c : s.toCharArray()) {
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    return sb.append('"').toString();
  }
}
