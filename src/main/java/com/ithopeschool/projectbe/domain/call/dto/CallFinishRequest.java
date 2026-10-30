package com.ithopeschool.projectbe.domain.call.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

// 통화를 마무리할 때 FE 가 보내는 모양입니다. 통화 중에 화면에 띄운 자막을 그대로 보냅니다. (FE src/lib/transcript.ts)
//   { "lines": [ { "speaker": "ai", "text": "어제 순공 3시간 10분이야." },
//                { "speaker": "me", "text": "폰 보다가 늦게 시작했어" } ] }
//
// ⚠️ 서버는 이 자막을 정리(요약)하는 데만 쓰고 저장하지 않습니다. 로그에도 찍지 않습니다. (CLAUDE.md 4장)
//    5분 통화의 자막은 보통 몇십 줄이라, 줄 수와 한 줄의 길이를 넉넉하지만 끝이 있게 정해 두었습니다. (GPT 비용)
public record CallFinishRequest(
    // 자막 줄들. 말한 순서대로입니다. 학생이 한마디도 하지 않았으면 빈 목록([])을 보냅니다.
    @NotNull(message = "자막(lines)을 보내 주세요. 없으면 빈 목록([])을 보내 주세요")
        @Size(max = 200, message = "자막(lines)은 200줄까지 보낼 수 있어요")
        @Valid
        List<Line> lines) {

  // 자막 한 줄. speaker 는 FE 의 SubtitleMessage.speaker 와 같습니다. ('ai' = AI 코치, 'me' = 학생)
  // 상담 채팅도 이 줄 모양으로 대화를 보냅니다. (ChatMessageRequest)
  public record Line(
      @NotNull(message = "누가 한 말인지(speaker) 보내 주세요")
          @Pattern(regexp = "ai|me", message = "speaker 는 ai 또는 me 예요")
          String speaker,
      @NotNull(message = "한 말(text)을 보내 주세요")
          @Size(max = MAX_TEXT_LENGTH, message = "자막 한 줄(text)은 1000자까지예요")
          String text) {

    // 한 줄의 최대 글자 수. 500 → 1000 (2026-10-31)
    // 과목 쌤이 문제 3~5개 · 풀이 과정을 한 답에 쓰면 500자를 넘을 수 있고, 학생 기기는 그 말을 다음 말과 함께
    // 다시 보내므로 500 이면 400 으로 막힐 수 있습니다. AI 의 말도 이 길이까지만 돌려줍니다. (ChatAiClient.fitLine)
    public static final int MAX_TEXT_LENGTH = 1000;
  }
}
