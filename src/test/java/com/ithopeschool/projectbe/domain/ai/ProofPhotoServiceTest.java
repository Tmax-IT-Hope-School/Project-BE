package com.ithopeschool.projectbe.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.ai.dto.ProofPhotoRequest;
import com.ithopeschool.projectbe.domain.ai.dto.ProofPhotoResponse;
import com.ithopeschool.projectbe.domain.care.ToneGuide;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

// Service 단위 테스트: AI(AiClient)를 가짜로 두고, 검사 · 순서 · 결과만 확인합니다. (CardPhotoServiceTest 와 같은 방식)
class ProofPhotoServiceTest {

  private static final Long USER_ID = 7L;
  // 작은 jpeg 처럼 보이는 사진 (내용은 상관없습니다. 모양만 봅니다)
  private static final String IMAGE =
      "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(new byte[] {1, 2, 3, 4});

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final AiClient aiClient = mock(AiClient.class);
  private final ProofPhotoService service = new ProofPhotoService(currentUserProvider, aiClient);

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
  }

  private void givenAnswer(ProofPhoto.Answer answer) {
    given(aiClient.askWithImage(any(), any(), any(), any(), eq(ProofPhoto.Answer.class)))
        .willReturn(Optional.ofNullable(answer));
  }

  private ProofPhotoResponse summarize(String cardTitle) {
    return service.summarize(new ProofPhotoRequest(IMAGE, cardTitle));
  }

  @Test
  @DisplayName("사진과 카드 제목을 AI 에 보내고(ASSIST), 받은 한 줄의 공백을 다듬어 돌려준다")
  void summarizesThePhoto() {
    givenAnswer(new ProofPhoto.Answer(false, true, "  수학 문제집 42~45쪽 풀고\n틀린 3문제 표시함 "));

    ProofPhotoResponse response = summarize("수학 문제집");

    assertThat(response).isEqualTo(ProofPhotoResponse.of("수학 문제집 42~45쪽 풀고 틀린 3문제 표시함"));
    ArgumentCaptor<AiQuestion> question = ArgumentCaptor.forClass(AiQuestion.class);
    verify(aiClient)
        .askWithImage(
            eq(USER_ID),
            eq(GptUsageKind.ASSIST),
            question.capture(),
            eq(IMAGE),
            eq(ProofPhoto.Answer.class));
    assertThat(question.getValue().input()).startsWith("카드 제목: 수학 문제집");
    // 지침에 개인정보를 옮기지 말라는 규칙과 깎아내리기 금지 규칙이 들어 있습니다.
    assertThat(question.getValue().instructions())
        .contains("개인정보는 사진에 있어도 summary 에 옮기지 마")
        .contains(ToneGuide.NO_PUT_DOWN_RULE);
  }

  @Test
  @DisplayName("카드 제목이 없으면 '(없음)' 으로 묻는다")
  void asksWithoutCardTitle() {
    givenAnswer(new ProofPhoto.Answer(false, true, "영어 단어 30개 외움"));

    assertThat(summarize(null).summary()).isEqualTo("영어 단어 30개 외움");
    ArgumentCaptor<AiQuestion> question = ArgumentCaptor.forClass(AiQuestion.class);
    verify(aiClient).askWithImage(any(), any(), question.capture(), any(), any());
    assertThat(question.getValue().input()).startsWith("카드 제목: (없음)");
  }

  @Test
  @DisplayName("한 줄이 60자를 넘으면 60자에서 자르고, 비었거나 깎아내리는 말이 있으면 null 이다")
  void trimsLongOrBadSummaries() {
    givenAnswer(new ProofPhoto.Answer(false, true, "가".repeat(70)));
    assertThat(summarize(null).summary()).isEqualTo("가".repeat(60) + "…");

    givenAnswer(new ProofPhoto.Answer(false, true, "   "));
    assertThat(summarize(null)).isEqualTo(ProofPhotoResponse.notMade());

    givenAnswer(new ProofPhoto.Answer(false, true, "한심하게 3문제밖에 못 풂"));
    assertThat(summarize(null)).isEqualTo(ProofPhotoResponse.notMade());
  }

  @Test
  @DisplayName("AI 가 못 답하면(꺼짐 · 실패) summary 가 null 이다")
  void notMadeWhenNoAnswer() {
    givenAnswer(null);

    assertThat(summarize("수학")).isEqualTo(ProofPhotoResponse.notMade());
  }

  @Test
  @DisplayName("사진에서 공부 흔적이 안 보인다고 하면 summary 가 null 이다")
  void notMadeWhenNotStudied() {
    givenAnswer(new ProofPhoto.Answer(false, false, "고양이 사진"));

    assertThat(summarize("수학")).isEqualTo(ProofPhotoResponse.notMade());
  }

  @Test
  @DisplayName("카드 제목에 위기 표현이 있으면 AI 를 부르지 않고 showHelp, AI 가 위기라고 하거나 한 줄에 위기 표현이 있어도 showHelp")
  void crisisMeansHelp() {
    assertThat(summarize("죽고 싶다")).isEqualTo(ProofPhotoResponse.help());
    verifyNoInteractions(aiClient);

    givenAnswer(new ProofPhoto.Answer(true, true, "수학 문제 풂"));
    assertThat(summarize(null)).isEqualTo(ProofPhotoResponse.help());

    givenAnswer(new ProofPhoto.Answer(false, true, "공책에 죽고 싶다고 적음"));
    assertThat(summarize(null)).isEqualTo(ProofPhotoResponse.help());
  }

  @Test
  @DisplayName("data URL 이 아니거나 base64 가 깨졌거나 4MB 를 넘으면 400 이고 AI 를 부르지 않는다")
  void rejectsBadImages() {
    assertRejected("aGVsbG8=", "사진은 jpeg · png · webp 의 data URL 로 보내 주세요");
    assertRejected("data:image/jpeg;base64,@@@", "사진을 읽을 수 없어요. 다시 찍어 주세요");
    String tooBig =
        "data:image/png;base64,"
            + Base64.getEncoder().encodeToString(new byte[CardPhotoService.MAX_IMAGE_BYTES + 1]);
    assertRejected(tooBig, "사진이 너무 커요. 4MB 이하로 보내 주세요");
    verifyNoInteractions(aiClient);
  }

  private void assertRejected(String image, String message) {
    assertThatThrownBy(() -> service.summarize(new ProofPhotoRequest(image, null)))
        .isInstanceOf(ApiException.class)
        .hasMessage(message)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(ErrorCode.VALIDATION_ERROR);
  }
}
