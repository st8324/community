package com.example.community.support;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 하네스 명세 CSV 읽기 도우미. 값에 쉼표를 쓰지 않는 단순 형식만 지원한다(쉼표가 섞이면 열 개수 검사에서 실패).
 *
 * <p>'#'으로 시작하는 줄과 빈 줄은 주석으로 무시한다.
 */
public final class SpecCsv {

  private SpecCsv() {}

  public record Table(List<String> header, List<Map<String, String>> rows) {}

  public static Table read(String classpathResource) {
    try (InputStream in = SpecCsv.class.getResourceAsStream(classpathResource)) {
      if (in == null) {
        throw new IllegalStateException("명세 파일이 없다: " + classpathResource);
      }
      BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
      List<String> header = null;
      List<Map<String, String>> rows = new ArrayList<>();
      int lineNo = 0;
      for (String line = reader.readLine(); line != null; line = reader.readLine()) {
        lineNo++;
        String trimmed = line.strip();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
          continue;
        }
        List<String> cells = Arrays.stream(trimmed.split(",", -1)).map(String::strip).toList();
        if (header == null) {
          header = cells;
          continue;
        }
        if (cells.size() != header.size()) {
          throw new IllegalStateException(
              "%s %d행: 열 개수 %d, 헤더 %d (값에 쉼표가 있는지 확인)"
                  .formatted(classpathResource, lineNo, cells.size(), header.size()));
        }
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < header.size(); i++) {
          row.put(header.get(i), cells.get(i));
        }
        rows.add(row);
      }
      if (header == null) {
        throw new IllegalStateException("헤더가 없다: " + classpathResource);
      }
      return new Table(header, rows);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** 하네스 상태 요약을 build/harness/에 남긴다. CI가 이 파일을 Job Summary에 붙인다. */
  public static void writeReport(String fileName, String markdown) {
    String dir = System.getProperty("harness.reportDir");
    if (dir == null) {
      return;
    }
    try {
      Path path = Path.of(dir, fileName);
      Files.createDirectories(path.getParent());
      Files.writeString(path, markdown, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static boolean strictMode() {
    return Boolean.parseBoolean(System.getProperty("harness.strict", "false"));
  }
}
