package com.example.community.common.web;

import java.io.IOException;
import java.util.Set;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * SPA 정적 파일 + 화면 경로 포워딩 (02 문서 5.8절 T-18, 03 문서 HD-30).
 *
 * <p>프론트는 브라우저 주소 방식 라우팅이라 {@code /posts/3}을 새로고침하면 서버가 그 경로를 받는다. 규칙: 파일이 있으면 그 파일, 없고 화면 경로이면
 * {@code index.html}, 그 밖에는 없음(→ 표준 404 JSON). Spring Boot 기본 정적 매핑은 끄고({@code
 * spring.web.resources.add-mappings=false}) 이 처리기 하나만 둔다.
 *
 * <p>컨트롤러가 처리하는 경로({@code /api/**} 등)는 이 처리기보다 먼저 매칭되므로, 여기에 오는 {@code /api/…}는 "없는 API"다 → HTML로
 * 가리지 않는다.
 */
@Configuration
public class SpaForwardingConfig implements WebMvcConfigurer {

  /** 첫 경로 조각이 이것이면 화면 경로가 아니다. */
  static final Set<String> NON_SPA_ROOTS = Set.of("api", "actuator");

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/**")
        .addResourceLocations("classpath:/static/")
        .resourceChain(true)
        .addResolver(new SpaFallbackResolver());
  }

  /** 화면 경로: 점이 없고(파일 아님) {@code api}·{@code actuator}로 시작하지 않는다. {@code ""}는 {@code /}. */
  static boolean isSpaRoute(String resourcePath) {
    String root = resourcePath.split("/", 2)[0];
    return !resourcePath.contains(".") && !NON_SPA_ROOTS.contains(root);
  }

  /** 없는 파일 요청 중 화면 경로만 index.html로 바꾼다. 파일 탐색·경로 검사는 상위 클래스(Spring) 그대로 쓴다. */
  static final class SpaFallbackResolver extends PathResourceResolver {

    @Override
    protected Resource getResource(String resourcePath, Resource location) throws IOException {
      Resource requested = super.getResource(resourcePath, location);
      if (requested != null) {
        return requested;
      }
      return isSpaRoute(resourcePath) ? super.getResource("index.html", location) : null;
    }
  }
}
