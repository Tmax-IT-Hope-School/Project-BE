package com.ithopeschool.projectbe.domain.variant.generate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;

// 프롬프트 파일 하나를 읽어 칸(section)별로 나누고, {{이름}} 자리를 채워 주는 도구입니다.
// 프롬프트는 코드에 적지 않고 src/main/resources/prompts/variant/ 의 버전 파일로만 관리합니다.
//
// 파일 모양
//   <!-- 맨 위 설명 (AI 에게 보내지 않음) -->
//   === system ===
//   ... {{source_problem_text}} ...
//   === user ===
//   ...
//
// 채우기 규칙
//   - 그 칸에 있는 {{이름}} 의 값을 하나라도 안 주면 에러입니다. (빈 칸이 그대로 AI 에게 가지 않게)
//   - 넣은 값 안의 글자는 다시 바꾸지 않습니다. (문제 글에 { } 가 있어도 안전)
//   - 자리 표시를 {{ }} 두 겹으로 한 이유: Symja 의 목록 {x, k} 이나 LaTeX 의 \frac{1}{2} 와 헷갈리지 않게
public final class PromptTemplate {

  private static final Pattern SECTION = Pattern.compile("(?m)^=== (.+?) ===\\s*$");
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-z_]+)}}");

  // 파일 이름에서 확장자를 뺀 것 (예: variant_v2). 변형 테이블의 promptVersion 에 남깁니다
  private final String version;
  private final Map<String, String> sections;

  private PromptTemplate(String version, Map<String, String> sections) {
    this.version = version;
    this.sections = sections;
  }

  // classpath 의 prompts/variant/{version}.md 를 읽습니다. 없으면 서버가 켜지지 않게 에러를 냅니다.
  public static PromptTemplate load(String version) {
    String path = "prompts/variant/" + version + ".md";
    try (InputStream in = new ClassPathResource(path).getInputStream()) {
      return parse(version, new String(in.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new IllegalStateException("프롬프트 파일을 읽지 못했어요: " + path, e);
    }
  }

  // 글을 칸별로 나눕니다. (테스트에서도 씁니다)
  static PromptTemplate parse(String version, String text) {
    String body = text.replaceFirst("(?s)^\\s*<!--.*?-->", "");
    Map<String, String> sections = new LinkedHashMap<>();
    Matcher matcher = SECTION.matcher(body);
    String name = null;
    int start = 0;
    while (matcher.find()) {
      if (name != null) {
        sections.put(name, body.substring(start, matcher.start()).strip());
      }
      name = matcher.group(1).strip();
      start = matcher.end();
    }
    if (name != null) {
      sections.put(name, body.substring(start).strip());
    }
    if (sections.isEmpty()) {
      throw new IllegalStateException("프롬프트 " + version + " 에 칸(=== 이름 ===)이 없어요");
    }
    return new PromptTemplate(version, Map.copyOf(sections));
  }

  public String version() {
    return version;
  }

  // 칸 하나를 값으로 채워 돌려줍니다.
  public String render(String section, Map<String, String> values) {
    String text = sections.get(section);
    if (text == null) {
      throw new IllegalStateException("프롬프트 " + version + " 에 '" + section + "' 칸이 없어요");
    }
    Set<String> missing = new HashSet<>();
    Matcher matcher = PLACEHOLDER.matcher(text);
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      String value = values.get(matcher.group(1));
      if (value == null) {
        missing.add(matcher.group(1));
        value = "";
      }
      matcher.appendReplacement(out, Matcher.quoteReplacement(value));
    }
    matcher.appendTail(out);
    if (!missing.isEmpty()) {
      throw new IllegalStateException("프롬프트 " + version + " 의 값이 빠졌어요: " + missing);
    }
    return out.toString();
  }

  // 그 칸이 있는지 (레벨 규칙 칸 확인용)
  public boolean hasSection(String section) {
    return sections.containsKey(section);
  }
}
