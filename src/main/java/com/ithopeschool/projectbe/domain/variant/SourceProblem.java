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

// 원본 문항 테이블. 한 줄 = 학생이 푼 문항 하나 (맞은 것 · 틀린 것 모두). (docs/VARIANT-SPEC.md 4 · 5장)
//
// 앞단계(사진 OCR)가 생기면 POST /api/v1/variant/source-problems 로 이 테이블에 쌓습니다.
// 맞은 문항도 저장하는 이유: 주간 (A) "맞은 문제의 대표 유형 심화" 와 통계에 쓰기 때문입니다.
// 그림이 필요한 문항(UNSUPPORTED_FIGURE)도 저장만 하고 출제하지 않습니다.
//
// 원본 선지는 딸린 테이블 source_problem_choices 에 한 줄씩 둡니다. (position = 보낸 순서)
@Entity
@Table(
    name = "source_problems",
    indexes = {
      @Index(name = "idx_source_problems_user_id_solved_at", columnList = "user_id, solved_at")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SourceProblem {

  // 문제 글 · 정답 글자 수 한도 (요청 DTO 검사와 같은 값)
  public static final int MAX_TEXT_LENGTH = 2000;
  public static final int MAX_ANSWER_LENGTH = 200;
  public static final int MAX_CHOICE_LENGTH = 200;
  public static final int MAX_SUBJECT_LENGTH = 30;
  public static final int MAX_STANDARD_CODE_LENGTH = 30;

  // 수학 분류체계(taxonomy.json)가 다루는 과목. 예전 원본처럼 과목이 비어 있으면 이 과목으로 봅니다
  public static final String MATH_SUBJECT = "공통수학1";

  // 원본 번호
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 누구의 문항인지 (users.id)
  @Column(nullable = false)
  private Long userId;

  // 문제 글. 수식은 $...$ 안에 LaTeX
  @Column(nullable = false, length = MAX_TEXT_LENGTH)
  private String problemText;

  // 원본이 객관식이면 선지 글자들. 단답형이면 빈 목록
  // 많아야 몇 개라서 원본을 읽을 때 같이 읽습니다(EAGER). AI 를 부르는 동안 DB 연결을 열어 두지 않기 위해서입니다.
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "source_problem_choices",
      joinColumns = @JoinColumn(name = "source_problem_id"))
  @OrderColumn(name = "position")
  @Column(name = "text", nullable = false, length = MAX_CHOICE_LENGTH)
  private List<String> choices = new ArrayList<>();

  // 원본의 정답 (모르면 비어 있음)
  @Column(length = MAX_ANSWER_LENGTH)
  private String correctAnswer;

  // 학생이 쓴 답 (모르면 비어 있음)
  @Column(length = MAX_ANSWER_LENGTH)
  private String studentAnswer;

  // 학생이 맞혔는지
  @Column(nullable = false)
  private boolean isCorrect;

  // 그림 · 그래프가 있어야 풀 수 있는 문항인지
  @Column(nullable = false)
  private boolean hasFigure;

  // 단원 코드 (taxonomy.json). 분류 전이면 비어 있음
  @Column(length = 20)
  private String unitCode;

  // 유형 코드 (taxonomy.json). 분류 전이면 비어 있음
  @Column(length = 20)
  private String typeCode;

  // 코드를 누가 정했는지 (보낸 쪽 · 분류기)
  @Enumerated(EnumType.STRING)
  @Column(length = 10)
  private ClassificationSource classificationSource;

  // 분류기의 자신감 (0 ~ 1). 보낸 쪽이 코드를 줬으면 비어 있음
  private Double classificationConfidence;

  // 출제에 쓸 수 있는지
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private SupportStatus supportStatus;

  // 같은 문제인지 비교하는 지문 (VariantFingerprint)
  @Column(nullable = false, length = 64)
  private String fingerprint;

  // ---- 확인 문제 연결 · 수학이 아닌 과목 (docs/VARIANT-SPEC.md 4-1, 2026-10-24) ----
  // ⚠️ 아래 칸 이름은 AI 가 지었습니다. 담당 학생이 확인해 주세요. (docs/DB.md)

  // 어디서 들어온 원본인지 (직접 등록 · 시드 · 확인 문제)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private SourceProblemOrigin origin;

  // 과목 이름 (성취기준 파일의 이름, 예: 공통국어1). 예전 원본(직접 등록 · 시드)은 비어 있고 고1 공통수학1 로 봅니다
  @Column(length = MAX_SUBJECT_LENGTH)
  private String subject;

  // 성취기준 코드 (예: 10공국1-02-01). 수학이 아닌 과목만 채웁니다. 비어 있으면 수학 분류체계(단원 · 유형)를 씁니다
  @Column(length = MAX_STANDARD_CODE_LENGTH)
  private String standardCode;

  // 문제가 기대는 지문 (국어 · 영어 독해 등). 없으면 비어 있음
  @Column(length = MAX_TEXT_LENGTH)
  private String passageText;

  // 학생이 푼 순간
  @Column(nullable = false)
  private Instant solvedAt;

  // 저장한 순간
  @Column(nullable = false)
  private Instant createdAt;

  public SourceProblem(
      Long userId,
      String problemText,
      List<String> choices,
      String correctAnswer,
      String studentAnswer,
      boolean isCorrect,
      boolean hasFigure,
      Instant solvedAt,
      Instant createdAt) {
    this(
        userId,
        problemText,
        choices,
        correctAnswer,
        studentAnswer,
        isCorrect,
        hasFigure,
        solvedAt,
        createdAt,
        SourceProblemOrigin.MANUAL,
        null);
  }

  //   origin      : 어디서 들어온 원본인지
  //   passageText : 문제가 기대는 지문 (없으면 null)
  public SourceProblem(
      Long userId,
      String problemText,
      List<String> choices,
      String correctAnswer,
      String studentAnswer,
      boolean isCorrect,
      boolean hasFigure,
      Instant solvedAt,
      Instant createdAt,
      SourceProblemOrigin origin,
      String passageText) {
    this.userId = userId;
    this.problemText = problemText;
    this.choices = choices == null ? new ArrayList<>() : new ArrayList<>(choices);
    this.correctAnswer = correctAnswer;
    this.studentAnswer = studentAnswer;
    this.isCorrect = isCorrect;
    this.hasFigure = hasFigure;
    this.fingerprint = VariantFingerprint.of(problemText);
    this.solvedAt = solvedAt;
    this.createdAt = createdAt;
    this.origin = origin;
    this.passageText = passageText == null || passageText.isBlank() ? null : passageText;
    // 분류가 정해지기 전에는 출제하지 않습니다. markXxx 로 바꿉니다.
    this.supportStatus = SupportStatus.PENDING_CLASSIFICATION;
  }

  // 수학 분류체계(단원 · 유형)로 다루는 원본인지. 성취기준 코드가 있으면 수학이 아닌 과목입니다.
  public boolean isMath() {
    return standardCode == null;
  }

  // 수학이 아닌 과목: 성취기준으로 분류가 끝나서 출제에 쓸 수 있습니다.
  public void markSupportedByStandard(String subject, String standardCode, Double confidence) {
    this.subject = subject;
    this.standardCode = standardCode;
    this.unitCode = null;
    this.typeCode = null;
    this.classificationSource = ClassificationSource.LLM;
    this.classificationConfidence = confidence;
    this.supportStatus = SupportStatus.SUPPORTED;
  }

  // 분류가 끝나서 출제에 쓸 수 있습니다. (수학 분류체계)
  public void markSupported(
      String unitCode, String typeCode, ClassificationSource source, Double confidence) {
    this.subject = MATH_SUBJECT;
    this.unitCode = unitCode;
    this.typeCode = typeCode;
    this.classificationSource = source;
    this.classificationConfidence = confidence;
    this.supportStatus = SupportStatus.SUPPORTED;
  }

  // 사람이 확인해야 합니다. 분류기가 고른 코드는 참고용으로 남깁니다(분류체계에 있는 코드일 때만).
  public void markNeedsReview(String unitCode, String typeCode, Double confidence) {
    this.unitCode = unitCode;
    this.typeCode = typeCode;
    this.classificationSource = unitCode == null ? null : ClassificationSource.LLM;
    this.classificationConfidence = confidence;
    this.supportStatus = SupportStatus.NEEDS_REVIEW;
  }

  // 그림이 있어야 풀 수 있어서 출제하지 않습니다.
  public void markUnsupportedFigure() {
    this.supportStatus = SupportStatus.UNSUPPORTED_FIGURE;
  }
}
