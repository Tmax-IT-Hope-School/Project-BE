package com.ithopeschool.projectbe.domain.variant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ithopeschool.projectbe.domain.variant.dto.SourceProblemCreateRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

// 개발용 시드 원본 문항(src/main/resources/variant/seed-source-problems.json)을 읽습니다. (VAR-03)
// 직접 쓴 30문항이고, 단원 · 유형 코드는 분류기 정확도를 잴 때 정답 라벨로도 씁니다.
// 쓰는 곳: 로컬 시드 등록(VariantSeedInitializer), 품질 평가 테스트, 시드 검산 테스트.
public final class VariantSeedData {

  static final String FILE = "variant/seed-source-problems.json";
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private VariantSeedData() {}

  // 시드 문항 하나. checkCode · checkValue 는 정답 검산용(VariantSeedDataTest)이고 등록할 때는 쓰지 않습니다.
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Problem(
      String problemText,
      List<String> choices,
      String correctAnswer,
      String studentAnswer,
      boolean isCorrect,
      String unitCode,
      String typeCode,
      long solvedAt,
      String checkCode,
      String checkValue) {

    // 등록 요청으로 바꿉니다. withCodes=false 면 코드를 빼서 분류기가 분류하게 합니다.
    public SourceProblemCreateRequest toRequest(boolean withCodes) {
      return new SourceProblemCreateRequest(
          problemText,
          choices,
          correctAnswer,
          studentAnswer,
          isCorrect,
          false,
          withCodes ? unitCode : null,
          withCodes ? typeCode : null,
          solvedAt);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record FileShape(List<Problem> problems) {}

  public static List<Problem> load() {
    try (InputStream in = new ClassPathResource(FILE).getInputStream()) {
      return List.copyOf(JSON.readValue(in, FileShape.class).problems());
    } catch (IOException e) {
      throw new IllegalStateException("시드 파일을 읽지 못했어요: " + FILE, e);
    }
  }
}
