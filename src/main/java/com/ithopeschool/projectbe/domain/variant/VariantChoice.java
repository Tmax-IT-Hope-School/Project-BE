package com.ithopeschool.projectbe.domain.variant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 변형 문항(MC5)의 선지 하나. 딸린 테이블 variant_choices 의 한 줄입니다. (Variant 에 매달린 값이라 @Embeddable)
// 순서(position)는 서버가 섞은 뒤의 순서입니다. 학생이 보는 ①~⑤ 가 이 순서입니다.
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VariantChoice {

  public static final int MAX_VALUE_LENGTH = 200;
  public static final int MAX_LATEX_LENGTH = 1000;
  public static final int MAX_RATIONALE_LENGTH = 300;

  // 선지의 값. AI 가 쓴 Symja 식 그대로입니다. (예: "2*Sqrt(3)") 학생에게는 보내지 않습니다
  @Column(name = "value_expr", nullable = false, length = MAX_VALUE_LENGTH)
  private String valueExpr;

  // 화면에 보여 줄 LaTeX. AI 가 아니라 샌드박스가 값에서 만든 것입니다 (그래서 값과 어긋날 수 없음)
  @Column(nullable = false, length = MAX_LATEX_LENGTH)
  private String latex;

  // 이 선지를 고르면 보여 줄 "어떤 실수인지" 한 문장. 정답 선지는 빈 글자
  @Column(nullable = false, length = MAX_RATIONALE_LENGTH)
  private String rationale;

  public VariantChoice(String valueExpr, String latex, String rationale) {
    this.valueExpr = valueExpr;
    this.latex = latex;
    this.rationale = rationale;
  }
}
