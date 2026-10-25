package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// AI 하루 계획에서 AI 에게 주는 입력 글과, 받은 답의 칸별 검사를 확인합니다.
class DayPlanPromptTest {

  private static final List<DayPlanPrompt.Text> FALLBACKS =
      List.of(
          new DayPlanPrompt.Text("수학 문제집", List.of("수학 문제집")),
          new DayPlanPrompt.Text("영단어장 1회독", List.of("30쪽 하기")));

  @Test
  @DisplayName("입력에는 칸마다 시각 · 길이 · 목표 · D-day · 하루 분량과 최근 카드 제목이 들어간다 (이름 · 학교는 없음)")
  void input() {
    String input =
        DayPlanPrompt.input(
            LocalDate.of(2026, 10, 18),
            List.of(
                new DayPlanPrompt.Block("16:40", 25, "11월 학평 수학 3등급", 32, null),
                new DayPlanPrompt.Block("17:15", 25, "영단어장 1회독", 8, "30쪽"),
                new DayPlanPrompt.Block("17:50", 25, null, null, null)),
            List.of("수학 문제집", "영어 단어"));

    assertThat(input)
        .isEqualTo(
            """
            날짜: 10/18
            칸 (이 순서대로 하나씩):
            1. 16:40 · 25분 · 목표 「11월 학평 수학 3등급」 D-day 까지 32일
            2. 17:15 · 25분 · 목표 「영단어장 1회독」 D-day 까지 8일 · 하루 분량 30쪽
            3. 17:50 · 25분 · 목표 없음
            최근 카드 제목: 수학 문제집 / 영어 단어""");
  }

  @Test
  @DisplayName("칸마다 검사해서 못 쓰는 칸만 기본 글로 바꾸고, 체크리스트는 빈 · 겹치는 항목을 버리고 3개까지")
  void checksEachCard() {
    DayPlanPrompt.Answer answer =
        new DayPlanPrompt.Answer(
            List.of(
                new DayPlanPrompt.Answer.Item(
                    " 수학 기출 ", List.of("3단원 15문제", "3단원 15문제", " ", "오답 표시", "채점", "한 번 더")),
                new DayPlanPrompt.Answer.Item("게으른 나의 영어", List.of("30쪽"))));

    List<DayPlanPrompt.Text> texts = DayPlanPrompt.textsOf(answer, FALLBACKS);

    assertThat(texts)
        .containsExactly(
            new DayPlanPrompt.Text("수학 기출", List.of("3단원 15문제", "오답 표시", "채점")), FALLBACKS.get(1));
  }

  @Test
  @DisplayName("답이 없거나 칸 수가 다르면 모두 기본 글이다 (AI 가 없어도 계획은 나온다)")
  void fallbacksWhenUnusable() {
    assertThat(DayPlanPrompt.textsOf(null, FALLBACKS)).isEqualTo(FALLBACKS);
    assertThat(
            DayPlanPrompt.textsOf(
                new DayPlanPrompt.Answer(
                    List.of(new DayPlanPrompt.Answer.Item("수학", List.of("1문제")))),
                FALLBACKS))
        .isEqualTo(FALLBACKS);
  }

  @Test
  @DisplayName("AI 에게 내 과목 목록을 주고, 답의 과목은 그 목록에 있을 때만 쓴다 (지어낸 과목은 null)")
  void subjectsFromMyList() {
    List<String> mine = List.of("국어", "영어", "수학");
    AiQuestion question =
        DayPlanPrompt.question(
            LocalDate.of(2026, 10, 25),
            List.of(new DayPlanPrompt.Block("20:00", 50, null, null, null)),
            List.of(),
            List.of(),
            mine);

    assertThat(question.input()).contains("내 과목: 국어 / 영어 / 수학");
    assertThat(question.instructions()).contains("7. subject:", "영단어 · 영어 독해 → 영어");
    List<DayPlanPrompt.Text> texts =
        DayPlanPrompt.textsOf(
            new DayPlanPrompt.Answer(
                List.of(
                    new DayPlanPrompt.Answer.Item("영단어", List.of("22쪽"), " 영어 "),
                    new DayPlanPrompt.Answer.Item("물리 개념", List.of("1단원"), "물리"))),
            List.of(
                new DayPlanPrompt.Text("기본", List.of("기본")),
                new DayPlanPrompt.Text("기본", List.of("기본"))),
            mine);
    assertThat(texts).extracting(DayPlanPrompt.Text::subject).containsExactly("영어", null);
  }
}
