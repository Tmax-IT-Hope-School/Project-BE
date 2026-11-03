package com.ithopeschool.projectbe.domain.variant;

import com.ithopeschool.projectbe.domain.variant.dto.SourceProblemResponse;
import com.ithopeschool.projectbe.domain.variant.generate.SourceClassifier;
import com.ithopeschool.projectbe.domain.variant.generate.VariantPipeline;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// 변형 생성 품질 평가를 실제로 돌리는 도구입니다. (VAR-10, docs/VARIANT-SPEC.md 10장, docs/VARIANT-EVAL.md)
// 시드 원본 × 레벨 × N 번 파이프라인을 돌려, 한 번 돌릴 때마다 CSV 한 줄을 쓰고 요약을 만듭니다.
// 사람 검수 칸(정답 정확 · 레벨 적합 · 자연스러움)은 빈칸으로 둡니다. 사람이 채웁니다.
//
// 실제 AI 로 돌리는 곳: VariantGenerationEvalTest (LLM_LIVE_TESTS=1 일 때만)
// 이 도구 자체가 제대로 도는지는 가짜 AI 로 VariantEvalRunnerTest 가 봅니다.
public class VariantEvalRunner {

  private final SourceProblemService sourceProblemService;
  private final SourceProblemRepository sourceProblemRepository;
  private final VariantRepository variantRepository;
  private final VariantPipeline pipeline;
  private final SourceClassifier classifier;

  public VariantEvalRunner(
      SourceProblemService sourceProblemService,
      SourceProblemRepository sourceProblemRepository,
      VariantRepository variantRepository,
      VariantPipeline pipeline,
      SourceClassifier classifier) {
    this.sourceProblemService = sourceProblemService;
    this.sourceProblemRepository = sourceProblemRepository;
    this.variantRepository = variantRepository;
    this.pipeline = pipeline;
    this.classifier = classifier;
  }

  // 평가 결과: CSV 파일, 요약 파일, 요약 글
  public record Report(Path csv, Path summary, String summaryText) {}

  // userId: 평가용 학생 번호 (실제 학생과 겹치지 않게)
  public Report run(
      Long userId, int limit, List<Integer> levels, int n, boolean classify, Path outDir)
      throws IOException {
    List<VariantSeedData.Problem> seeds = VariantSeedData.load();
    seeds = seeds.subList(0, Math.min(limit, seeds.size()));

    List<String> rows = new ArrayList<>();
    rows.add(
        csv(
            "source_id",
            "source_text",
            "level",
            "result",
            "attempts",
            "rejections",
            "format",
            "problem_text",
            "choices_latex",
            "answer_latex",
            "solution",
            "human_answer_correct_YN",
            "human_level_fit_YN",
            "human_naturalness_1to5"));

    // 레벨 → [시도한 수, 성공 수, 쓴 시도 합]
    Map<Integer, int[]> perLevel = new LinkedHashMap<>();
    // 검사 이름(G1 ~ G7) → 떨어진 수
    Map<String, Integer> gateFailures = new LinkedHashMap<>();

    for (VariantSeedData.Problem seed : seeds) {
      SourceProblemResponse registered =
          sourceProblemService.registerFor(userId, seed.toRequest(true));
      SourceProblem source =
          sourceProblemRepository.findById(Long.valueOf(registered.id())).orElseThrow();
      for (int level : levels) {
        for (int i = 0; i < n; i++) {
          long before =
              variantRepository
                  .findBySourceProblemIdAndLevelOrderByIdAsc(source.getId(), level)
                  .size();
          Optional<Variant> made = pipeline.generate(source, level);
          List<Variant> tries =
              variantRepository.findBySourceProblemIdAndLevelOrderByIdAsc(source.getId(), level);
          List<Variant> thisRun = tries.subList((int) before, tries.size());

          List<String> rejections = new ArrayList<>();
          for (Variant attempt : thisRun) {
            if (attempt.getStatus() == VariantStatus.REJECTED
                && attempt.getRejectionReason() != null) {
              rejections.add(attempt.getRejectionReason());
              gateFailures.merge(attempt.getRejectionReason().split(" ")[0], 1, Integer::sum);
            }
          }
          int[] stats = perLevel.computeIfAbsent(level, key -> new int[3]);
          stats[0]++;
          stats[2] += thisRun.size();
          if (made.isPresent()) {
            stats[1]++;
          }
          Variant v = made.orElse(null);
          rows.add(
              csv(
                  String.valueOf(source.getId()),
                  source.getProblemText(),
                  String.valueOf(level),
                  made.isPresent() ? "VERIFIED" : "FAILED",
                  String.valueOf(thisRun.size()),
                  String.join(" | ", rejections),
                  v == null || v.getFormat() == null ? "" : v.getFormat().name(),
                  v == null ? "" : v.getProblemText(),
                  v == null
                      ? ""
                      : String.join(
                          " | ", v.getChoices().stream().map(VariantChoice::getLatex).toList()),
                  v == null ? "" : v.answerLatex(),
                  v == null ? "" : v.getSolution(),
                  "",
                  "",
                  ""));
        }
      }
    }

    StringBuilder summary = new StringBuilder();
    summary.append("# 변형 생성 품질 평가 요약\n\n");
    summary.append("| 레벨 | 시도 | 성공 | 최종 성공률 | 평균 시도 횟수 |\n|---|---|---|---|---|\n");
    int totalRuns = 0;
    int totalSuccess = 0;
    for (Map.Entry<Integer, int[]> entry : perLevel.entrySet()) {
      int[] s = entry.getValue();
      totalRuns += s[0];
      totalSuccess += s[1];
      summary.append(
          "| L%d | %d | %d | %.1f%% | %.2f |\n"
              .formatted(
                  entry.getKey(),
                  s[0],
                  s[1],
                  percent(s[1], s[0]),
                  s[0] == 0 ? 0.0 : (double) s[2] / s[0]));
    }
    summary.append(
        "\n전체 최종 성공률: %.1f%% (기준 85%% 이상)\n\n".formatted(percent(totalSuccess, totalRuns)));
    summary.append("검사별 떨어진 수: ").append(gateFailures.isEmpty() ? "없음" : gateFailures).append("\n");

    if (classify) {
      int unitHits = 0;
      int typeHits = 0;
      for (VariantSeedData.Problem seed : seeds) {
        SourceClassifier.Result result =
            classifier.classify(userId, seed.problemText(), seed.choices());
        if (seed.unitCode().equals(result.unitCode())) {
          unitHits++;
        }
        if (seed.typeCode().equals(result.typeCode())) {
          typeHits++;
        }
      }
      summary.append(
          "\n분류기 정확도: 단원 %.1f%% (기준 95%%), 유형 %.1f%% (기준 80%%), 문항 %d개\n"
              .formatted(
                  percent(unitHits, seeds.size()), percent(typeHits, seeds.size()), seeds.size()));
    }

    Files.createDirectories(outDir);
    String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    Path csvFile = outDir.resolve("eval_" + stamp + ".csv");
    Path summaryFile = outDir.resolve("eval_" + stamp + "_summary.md");
    // 엑셀에서 한글이 깨지지 않게 맨 앞에 BOM 을 붙입니다.
    Files.writeString(csvFile, "﻿" + String.join("\n", rows) + "\n", StandardCharsets.UTF_8);
    Files.writeString(summaryFile, summary.toString(), StandardCharsets.UTF_8);
    return new Report(csvFile, summaryFile, summary.toString());
  }

  private static double percent(int part, int whole) {
    return whole == 0 ? 0.0 : 100.0 * part / whole;
  }

  // CSV 한 줄. 칸마다 따옴표로 감싸고, 칸 안의 따옴표는 두 번 씁니다.
  private static String csv(String... cells) {
    List<String> quoted = new ArrayList<>();
    for (String cell : cells) {
      String value = cell == null ? "" : cell.replace("\r", " ").replace("\n", " ");
      quoted.add("\"" + value.replace("\"", "\"\"") + "\"");
    }
    return String.join(",", quoted);
  }
}
