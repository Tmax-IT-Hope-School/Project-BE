package com.ithopeschool.projectbe.domain.call;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// 배포 설정(application-prod.yml)으로 켰을 때 AI 통화가 켜지고 꺼지는 조건을 확인합니다.
// ⚠️ 켜졌는지만 봅니다. 진짜 OpenAI 를 부르지는 않습니다.
class CallProdSettingTest {

  @Nested
  @WebMvcTest(CallController.class)
  @Import(CallKeyClient.class)
  @ActiveProfiles("prod")
  @TestPropertySource(
      properties = {"OPENAI_API_KEY=test-key-123", "OPENAI_REALTIME_MODEL=test-realtime-model"})
  @DisplayName("배포 서버에 OPENAI_API_KEY 와 OPENAI_REALTIME_MODEL 이 둘 다 있을 때")
  class WithKeyAndRealtimeModel {

    @MockitoBean private CallService callService;
    // 확인 통화 문(2026-10-18)도 Controller 가 쓰니 가짜로 둡니다.
    @MockitoBean private CheckCallService checkCallService;
    @Autowired private CallKeyClient callKeyClient;
    @Autowired private Environment environment;

    @Test
    @DisplayName("통화가 켜진다")
    void callIsOn() {
      assertThat(callKeyClient.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("AI_CALL_MAX_SECONDS 를 넣지 않으면 통화 한 번 최대 길이는 300초(5분)다 (#434 에서 180 → 300)")
    void callMaxSecondsDefaultsToFiveMinutes() {
      assertThat(environment.getProperty("app.ai.call-max-seconds", Integer.class)).isEqualTo(300);
    }
  }

  @Nested
  @WebMvcTest(CallController.class)
  @Import(CallKeyClient.class)
  @ActiveProfiles("prod")
  @TestPropertySource(properties = {"OPENAI_API_KEY=test-key-123", "OPENAI_MODEL=test-model"})
  @DisplayName("배포 서버에 키와 글 모델만 있고 음성 모델 이름이 없을 때")
  class WithoutRealtimeModel {

    @MockitoBean private CallService callService;
    // 확인 통화 문(2026-10-18)도 Controller 가 쓰니 가짜로 둡니다.
    @MockitoBean private CheckCallService checkCallService;
    @Autowired private CallKeyClient callKeyClient;

    @Test
    @DisplayName("통화는 꺼져 있다 (글 AI 를 켰다고 비싼 음성까지 같이 켜지지 않는다)")
    void callIsOff() {
      assertThat(callKeyClient.isEnabled()).isFalse();
    }
  }
}
