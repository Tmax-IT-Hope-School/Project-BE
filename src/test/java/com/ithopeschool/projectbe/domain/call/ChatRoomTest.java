package com.ithopeschool.projectbe.domain.call;

import static org.assertj.core.api.Assertions.assertThat;

import com.ithopeschool.projectbe.domain.call.dto.CallSessionRequest;
import com.ithopeschool.projectbe.domain.goalchat.GoalChatPrompt;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 상담 채팅의 방 3개(상담 · 계획 · 과목 쌤)의 [이번 방] 칸을 확인합니다. (2026-10-18)
class ChatRoomTest {

  @Test
  @DisplayName("요청에서 받는 방 이름(CallSessionRequest.ROOMS)은 ChatRoom 의 이름 + 예전 이름 GOAL_MATE 다")
  void roomsMatchEnum() {
    String names =
        Arrays.stream(ChatRoom.values()).map(Enum::name).collect(Collectors.joining("|"));

    assertThat(CallSessionRequest.ROOMS).isEqualTo(names + "|" + ChatRoom.OLD_GOAL_MATE);
  }

  @Test
  @DisplayName("코치 방은 지금 대본 그대로라서 붙일 칸이 없다")
  void coachAddsNothing() {
    assertThat(ChatRoom.COACH.section()).isEmpty();
    assertThat(ChatRoom.COACH.usesPsychSection()).isTrue();
  }

  @Test
  @DisplayName("계획 방은 오늘 하루 계획 대화 · WOOP 목표(목표 만들기 대화와 같은 규칙) · make_plan · 주간 점검을 적는다")
  void plannerSection() {
    assertThat(ChatRoom.PLANNER.section())
        .startsWith("\n\n[이번 방: 계획]")
        .contains("이 방에는 [통화 순서] · [생각 습관] 이 없어")
        .contains("[꼭 지킬 것] · 위로하는 날 규칙은 그대로 지켜")
        .contains("오늘 하루 스케줄을 같이 짜고 고치는")
        .contains("계획을 강요하지 말고")
        .contains("1) 바람")
        .contains(GoalChatPrompt.OBSTACLE_RULE)
        .contains(GoalChatPrompt.IF_THEN_RULE)
        .contains("make_plan")
        .contains("다음 주에 바꿀 것 하나");
    assertThat(ChatRoom.PLANNER.usesPsychSection()).isTrue();
  }

  @Test
  @DisplayName("계획 방은 먼저 제안하고, 질문은 기록에 없는 것만 한 답에 하나 · 두 번까지, 동의하면 day_plan 을 준다 (#431)")
  void plannerProposesFirstAndAsksLess() {
    String section = ChatRoom.PLANNER.section();

    assertThat(section)
        .contains("묻기 전에 먼저 제안해")
        .contains("[오늘 쓸 수 있는 시간] · [오늘의 사실] · 목표 · 시험")
        .contains("무엇을 · 몇 분 · 어떤 순서로")
        .contains("기록에 없는 것만")
        .contains("한 답에 하나, 이 대화 전체에서 두 번까지")
        .contains("다 받아서 안에 넣어. 이미 말한 것은 다시 묻지 마")
        .contains("action 을 day_plan 으로 줘")
        .contains("\"이렇게 짜 볼게\"")
        .contains("묶어서 받고")
        .contains("위로하는 날이면 기분을 먼저 듣고");
    // 예전의 되묻기 규칙과 "오늘 계획 짜 줘" 칩 규칙은 없습니다. (칩은 FE 가 늘 붙입니다)
    assertThat(section)
        .doesNotContain("한 번에 하나씩 물어")
        .doesNotContain("한 번에 하나씩 짧게 물어")
        .doesNotContain("quickReplies 에 \"오늘 계획 짜 줘\"")
        .doesNotContain("위의 [통화 순서] 는 따르지 마");
  }

  @Test
  @DisplayName("과목 쌤은 답을 바로 주지 않고 단계별로 설명하며, 학생 성향 칸을 쓰지 않는다")
  void tutorSection() {
    assertThat(ChatRoom.TUTOR.section())
        .startsWith("\n\n[이번 방: 과목 쌤]")
        .contains("위의 [통화 순서] 는 따르지 마")
        .contains("답을 바로 주지 마")
        .contains("풀이 과정을 같이 따라가")
        .contains("어디가 막혔는지 먼저 물어봐")
        .contains("start_quiz");
    assertThat(ChatRoom.TUTOR.usesPsychSection()).isFalse();
  }

  @Test
  @DisplayName("글자로 방을 찾고, 없거나 모르는 글자는 빈 값이다")
  void findsRoom() {
    assertThat(ChatRoom.from("PLANNER")).contains(ChatRoom.PLANNER);
    assertThat(ChatRoom.from(null)).isEmpty();
    assertThat(ChatRoom.from("tutor")).isEmpty();
  }

  @Test
  @DisplayName("예전 이름 GOAL_MATE(목표 메이트)로 오면 계획 방(PLANNER)으로 받는다")
  void oldGoalMateIsPlanner() {
    assertThat(ChatRoom.from("GOAL_MATE")).contains(ChatRoom.PLANNER);
    assertThat(ChatRoom.from(ChatRoom.OLD_GOAL_MATE)).contains(ChatRoom.PLANNER);
  }
}
