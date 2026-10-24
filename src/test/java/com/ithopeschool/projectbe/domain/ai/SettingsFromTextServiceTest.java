package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.ai.dto.SettingsFromTextRequest;
import com.ithopeschool.projectbe.domain.ai.dto.SettingsFromTextResponse;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.settings.SettingsService;
import com.ithopeschool.projectbe.domain.settings.dto.SettingsResponse;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

// Service 단위 테스트: 관리 설정(SettingsService)과 AI(AiClient)를 가짜로 바꿔서
// "DB 의 지금 설정을 AI 에게 주는지" 와 "설정을 바꾸지 않는지" 를 확인합니다.
class SettingsFromTextServiceTest {

  private static final Long USER_ID = 7L;

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final SettingsService settingsService = mock(SettingsService.class);
  private final AiClient aiClient = mock(AiClient.class);
  private final SettingsFromTextService service =
      new SettingsFromTextService(currentUserProvider, settingsService, aiClient);

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    given(settingsService.getSettings())
        .willReturn(
            new SettingsResponse(
                "21:00",
                3,
                "23:00",
                "07:00",
                3,
                false,
                2,
                null,
                null,
                List.of("유튜브"),
                null,
                null,
                Map.of()));
  }

  @Test
  @DisplayName("지금 설정의 네 칸만 AI 에게 주고, 바뀌는 칸만 담은 변경안을 돌려준다. 설정은 바꾸지 않는다")
  void suggests() {
    given(
            aiClient.ask(
                eq(USER_ID), eq(GptUsageKind.ASSIST), any(), eq(SettingsFromText.Answer.class)))
        .willReturn(
            Optional.of(
                new SettingsFromText.Answer(null, 3, null, "09:00", "연락 금지 끝을 09:00 으로 바꿔요")));

    SettingsFromTextResponse response = service.suggest(new SettingsFromTextRequest("아침엔 알림 꺼 줘"));

    assertThat(response)
        .isEqualTo(
            SettingsFromTextResponse.of(
                new SettingsFromTextResponse.Proposal(
                    null, null, null, "09:00", "연락 금지 끝을 09:00 으로 바꿔요")));
    ArgumentCaptor<AiQuestion> question = ArgumentCaptor.forClass(AiQuestion.class);
    verify(aiClient)
        .ask(
            eq(USER_ID),
            eq(GptUsageKind.ASSIST),
            question.capture(),
            eq(SettingsFromText.Answer.class));
    // 수위 · 한도 · 잠금 앱 같은 다른 설정과 개인정보는 들어가지 않습니다.
    assertThat(question.getValue().input())
        .isEqualTo("학생의 부탁: 아침엔 알림 꺼 줘\n지금 설정: 전화 21:00 · 하루 메시지 3번 · 연락 금지 23:00 ~ 07:00");
    verify(settingsService, never()).update(any());
  }

  @Test
  @DisplayName("AI 가 못 만들었거나 바뀌는 칸이 없으면 proposal: null 이다")
  void unusableIsNull() {
    given(aiClient.ask(any(), any(), any(), eq(SettingsFromText.Answer.class)))
        .willReturn(Optional.empty());
    assertThat(service.suggest(new SettingsFromTextRequest("수위 5로")))
        .isEqualTo(SettingsFromTextResponse.of(null));

    given(aiClient.ask(any(), any(), any(), eq(SettingsFromText.Answer.class)))
        .willReturn(
            Optional.of(new SettingsFromText.Answer(null, null, null, null, "설정 화면에서 바꿔 주세요")));
    assertThat(service.suggest(new SettingsFromTextRequest("수위 5로")).proposal()).isNull();
  }

  @Test
  @DisplayName("부탁에 위기 표현이 있으면 설정과 AI 를 부르지 않고 showHelp 다")
  void crisisShowsHelp() {
    assertThat(service.suggest(new SettingsFromTextRequest("알림 다 끄고 사라지고 싶어")))
        .isEqualTo(SettingsFromTextResponse.help());
    verifyNoInteractions(settingsService, aiClient);
  }
}
