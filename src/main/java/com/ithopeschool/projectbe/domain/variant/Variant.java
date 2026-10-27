package com.ithopeschool.projectbe.domain.variant;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 변형 문항 테이블. 한 줄 = AI 에게 변형 하나를 만들게 한 시도 하나. (docs/VARIANT-SPEC.md 4 · 6장)
//
// 검사를 모두 통과한 것(VERIFIED)과 떨어진 것(REJECTED)을 모두 남깁니다.
// 떨어진 것도 남기는 이유: 어느 검사에서 얼마나 떨어지는지 보고 프롬프트를 고치기 위해서입니다. (품질 평가)
// ⚠️ 학생에게는 VERIFIED 만 나갑니다. 과제(assignments)는 VERIFIED 변형만 가리킵니다.
//
// MC5 의 선지는 딸린 테이블 variant_choices 에 있습니다. (섞은 뒤 순서)
@Entity
@Table(
    name = "variants",
    indexes = {
      @Index(
          name = "idx_variants_source_problem_id_level_status",
          columnList = "source_problem_id, level, status")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Variant {

  // 글자 수 한도 (G1 검사와 같은 값)
  public static final int MAX_PROBLEM_TEXT_LENGTH = 2000;
  public static final int MAX_SOLUTION_LENGTH = 4000;
  public static final int MAX_CODE_LENGTH = 2000;
  public static final int MAX_LOG_LENGTH = 4000;
  public static final int MAX_REASON_LENGTH = 500;
  public static final int MAX_LATEX_LENGTH = 1000;

  // 변형 번호
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 누구의 원본에서 나왔는지 (users.id). 탈퇴할 때 같이 지우려고 둡니다
  @Column(nullable = false)
  private Long userId;

  // 어느 원본의 변형인지 (source_problems.id)
  @Column(nullable = false)
  private Long sourceProblemId;

  // 레벨 (-1 비계, 0 재인출, 1 변형, 2 심화)
  @Column(nullable = false)
  private int level;

  // 형식 (MC5 · SHORT). 떨어진 시도는 AI 가 형식을 안 줬으면 비어 있을 수 있습니다
  @Enumerated(EnumType.STRING)
  @Column(length = 10)
  private VariantFormat format;

  // 문제 글
  @Column(nullable = false, length = MAX_PROBLEM_TEXT_LENGTH)
  private String problemText;

  // MC5 선지 (섞은 뒤 순서). SHORT 면 빈 목록
  // 5개뿐이라 변형을 읽을 때 같이 읽습니다(EAGER). 응답을 만들 때 DB 연결이 닫혀 있어도 쓸 수 있게 하려고입니다.
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "variant_choices", joinColumns = @JoinColumn(name = "variant_id"))
  @OrderColumn(name = "position")
  private List<VariantChoice> choices = new ArrayList<>();

  // MC5 정답 선지 번호 (0 ~ 4, 섞은 뒤). SHORT 면 비어 있음
  private Integer answerIndex;

  // SHORT 정답 (1 ~ 999). MC5 면 비어 있음
  private Integer shortAnswer;

  // 샌드박스가 계산한 정답의 LaTeX (정답을 보여 줄 때 씁니다)
  @Column(length = MAX_LATEX_LENGTH)
  private String computedLatex;

  // 학생이 답을 낸 뒤 보여 줄 해설
  @Column(nullable = false, length = MAX_SOLUTION_LENGTH)
  private String solution;

  // AI 가 쓴 Symja 계산 코드 (정답 검증용). 학생에게는 절대 보내지 않습니다
  @Column(nullable = false, length = MAX_CODE_LENGTH)
  private String verificationCode;

  // 검사(G1 ~ G7)마다 통과했는지와 사유 (JSON 글자)
  @Column(nullable = false, length = MAX_LOG_LENGTH)
  private String verificationLog;

  // 같은 문제인지 비교하는 지문 (VariantFingerprint)
  @Column(nullable = false, length = 64)
  private String fingerprint;

  // VERIFIED · REJECTED
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private VariantStatus status;

  // 떨어진 이유 (REJECTED 일 때)
  @Column(length = MAX_REASON_LENGTH)
  private String rejectionReason;

  // 만든 AI 모델 이름
  @Column(nullable = false, length = 100)
  private String model;

  // 쓴 프롬프트 파일 버전 (예: variant_v2)
  @Column(nullable = false, length = 40)
  private String promptVersion;

  // 몇 번째 시도였는지 (1 ~ 3)
  @Column(nullable = false)
  private int generationAttempt;

  // 쓴 토큰 수. 지금 AiClient 가 알려 주지 않아서 비어 있습니다 (docs/VARIANT-SPEC.md 0-1)
  private Integer inputTokens;

  private Integer outputTokens;

  // 만든 순간
  @Column(nullable = false)
  private Instant createdAt;

  private Variant(
      Long userId,
      Long sourceProblemId,
      int level,
      VariantFormat format,
      String problemText,
      String solution,
      String verificationCode,
      String verificationLog,
      VariantStatus status,
      String model,
      String promptVersion,
      int generationAttempt,
      Instant createdAt) {
    this.userId = userId;
    this.sourceProblemId = sourceProblemId;
    this.level = level;
    this.format = format;
    this.problemText = cut(problemText, MAX_PROBLEM_TEXT_LENGTH);
    this.solution = cut(solution, MAX_SOLUTION_LENGTH);
    this.verificationCode = cut(verificationCode, MAX_CODE_LENGTH);
    this.verificationLog = cut(verificationLog, MAX_LOG_LENGTH);
    this.fingerprint = VariantFingerprint.of(this.problemText);
    this.status = status;
    this.model = cut(model, 100);
    this.promptVersion = promptVersion;
    this.generationAttempt = generationAttempt;
    this.createdAt = createdAt;
  }

  // 검사를 모두 통과한 변형. choices 와 answerIndex 는 이미 섞은 뒤의 값입니다.
  public static Variant verified(
      Long userId,
      Long sourceProblemId,
      int level,
      VariantFormat format,
      String problemText,
      List<VariantChoice> choices,
      Integer answerIndex,
      Integer shortAnswer,
      String computedLatex,
      String solution,
      String verificationCode,
      String verificationLog,
      String model,
      String promptVersion,
      int generationAttempt,
      Instant createdAt) {
    Variant variant =
        new Variant(
            userId,
            sourceProblemId,
            level,
            format,
            problemText,
            solution,
            verificationCode,
            verificationLog,
            VariantStatus.VERIFIED,
            model,
            promptVersion,
            generationAttempt,
            createdAt);
    variant.choices = new ArrayList<>(choices);
    variant.answerIndex = answerIndex;
    variant.shortAnswer = shortAnswer;
    variant.computedLatex = cut(computedLatex, MAX_LATEX_LENGTH);
    return variant;
  }

  // 검사에서 떨어진 시도. 글자가 한도보다 길어서 떨어졌을 수도 있으니 한도에 맞게 잘라서 남깁니다.
  public static Variant rejected(
      Long userId,
      Long sourceProblemId,
      int level,
      VariantFormat format,
      String problemText,
      String solution,
      String verificationCode,
      String verificationLog,
      String rejectionReason,
      String model,
      String promptVersion,
      int generationAttempt,
      Instant createdAt) {
    Variant variant =
        new Variant(
            userId,
            sourceProblemId,
            level,
            format,
            problemText,
            solution,
            verificationCode,
            verificationLog,
            VariantStatus.REJECTED,
            model,
            promptVersion,
            generationAttempt,
            createdAt);
    variant.rejectionReason = cut(rejectionReason, MAX_REASON_LENGTH);
    return variant;
  }

  // 정답 칸의 화면용 글자. MC5 는 정답 선지의 LaTeX, SHORT 는 숫자입니다.
  public String answerLatex() {
    if (format == VariantFormat.SHORT) {
      return String.valueOf(shortAnswer);
    }
    return choices.get(answerIndex).getLatex();
  }

  // 빈 값은 빈 글자로, 한도보다 긴 글자는 한도까지 자릅니다. (떨어진 시도를 남길 때 DB 에러가 나지 않게)
  private static String cut(String text, int max) {
    if (text == null) {
      return "";
    }
    return text.length() <= max ? text : text.substring(0, max);
  }
}
