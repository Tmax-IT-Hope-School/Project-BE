package com.ithopeschool.projectbe.domain.ai;

import com.ithopeschool.projectbe.domain.ai.dto.ProofPhotoRequest;
import com.ithopeschool.projectbe.domain.ai.dto.ProofPhotoResponse;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.safety.CrisisText;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// "사진으로 증빙 적기" 의 로직입니다. 공부한 사진 한 장을 "무엇을 했는지" 한 줄로 바꿉니다. (Project-FE#670)
// (2026-10-25 결정 "사진은 서버 · 기기 어디에도 저장하지 않고 AI 요약 한 줄만 돌려줌")
// ⚠️ 아무것도 저장하지 않습니다. 사진은 메모리에서 OpenAI 로 보내고 끝이며, DB · 파일 · 로그 어디에도 남기지 않습니다.
//    학생이 한 줄을 고친 뒤 FE 가 공부 기록 저장(endReport.proof.summary)으로 남깁니다.
//
// 순서
//   1. 사진 모양을 봅니다. "사진으로 카드 만들기" 와 같은 규칙입니다. (CardPhotoService.checkImage, 아니면 400)
//   2. 카드 제목에 위기 표현(단어 목록)이 있으면 AI 를 부르지 않고 showHelp 를 돌려줍니다. (#56, 어떤 기능보다 먼저)
//   3. 사진과 함께 AI 를 부릅니다. 꺼짐 · 실패면 summary 가 null 입니다. (기록은 작은 AI 기능 ASSIST)
//   4. AI 가 위기 표현이라고 하면 2번과 같이 하고, 공부 흔적이 안 보인다고 하면 summary 가 null 입니다.
//   5. 한 줄을 다듬습니다. (ProofPhoto.summaryOf) 다듬은 한 줄에 위기 표현이 있으면 2번과 같이 합니다.
//
// 이 기능은 DB 를 읽지 않습니다. 사진 한 장과 카드 제목만으로 씁니다.
// currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 입니다.
@Service
@RequiredArgsConstructor
public class ProofPhotoService {

  private final CurrentUserProvider currentUserProvider;
  private final AiClient aiClient;

  public ProofPhotoResponse summarize(ProofPhotoRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();

    // 1) 사진 검사
    CardPhotoService.checkImage(request.image());

    // 2) 위기 표현 (단어 목록)
    if (request.cardTitle() != null && CrisisText.hasCrisisText(List.of(request.cardTitle()))) {
      return ProofPhotoResponse.help();
    }

    // 3) 사진과 함께 AI
    Optional<ProofPhoto.Answer> answer =
        aiClient.askWithImage(
            userId,
            GptUsageKind.ASSIST,
            ProofPhoto.question(request.cardTitle()),
            request.image(),
            ProofPhoto.Answer.class);
    if (answer.isEmpty()) {
      return ProofPhotoResponse.notMade();
    }

    // 4) 위기 표현 (AI 판단) · 공부 흔적이 안 보이는 사진
    if (Boolean.TRUE.equals(answer.get().crisis())) {
      return ProofPhotoResponse.help();
    }
    if (!Boolean.TRUE.equals(answer.get().studied())) {
      return ProofPhotoResponse.notMade();
    }

    // 5) 한 줄 다듬기. 학생의 기록에 그대로 들어가는 글이라 단어 목록으로 한 번 더 봅니다.
    String summary = ProofPhoto.summaryOf(answer.get().summary());
    if (summary != null && CrisisText.hasCrisisText(List.of(summary))) {
      return ProofPhotoResponse.help();
    }
    return ProofPhotoResponse.of(summary);
  }
}
