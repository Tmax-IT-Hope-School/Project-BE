package com.ithopeschool.projectbe.domain.ai;

import com.ithopeschool.projectbe.domain.ai.dto.StudyQuizRequest;
import com.ithopeschool.projectbe.domain.ai.dto.StudyQuizResponse;
import com.ithopeschool.projectbe.domain.curriculum.CurriculumService;
import com.ithopeschool.projectbe.domain.gpt.GptUsageKind;
import com.ithopeschool.projectbe.domain.safety.CrisisText;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// "확인 문제" 의 로직입니다. 방금 공부한 것(주제 · 자료 글 · 교과서 사진)으로 4지선다 문제를 냅니다. (2026-10-18)
// ⚠️ 아무것도 저장하지 않습니다. 사진 · 자료 글은 메모리에서 OpenAI 로 보내고 끝이며, DB · 파일 · 로그 어디에도 남기지 않습니다.
//    채점은 FE 가 기기에서 합니다.
//
// 순서
//   1. 사진이 있으면 모양을 봅니다. ("사진으로 카드 만들기" 와 같은 규칙, 아니면 400)
//   2. 주제 · 체크리스트 · 자료 글에 위기 표현(단어 목록)이 있으면 AI 를 부르지 않고 showHelp 를 돌려줍니다. (#56)
//   3. AI 를 부릅니다. 사진이 있으면 사진과 함께. 꺼짐 · 실패면 questions 가 null 입니다. (기록은 ASSIST)
//      학생의 학년을 알면 교육과정(2022 · 2015 개정)과 주제에 맞는 성취기준 몇 줄을 입력에 붙여 범위 안에서 내게 합니다.
//      (CurriculumService.quizLines, 2026-10-18 "가벼운 RAG")
//   4. AI 가 위기 표현이라고 하면 showHelp, 공부 내용이 아니라고 하면 reason: NOT_STUDY
//   5. 받은 문제를 검사합니다. (StudyQuiz.from — 보기 4개 · 정답 위치 · 글자 수 · 말투 · 지문)
//   6. 문제의 지문(passage, 사진 속 글에서 옮긴 것일 수 있음)에 위기 표현(단어 목록)이 있으면 showHelp (2026-10-24, #394)
//
// currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 입니다.
@Service
@RequiredArgsConstructor
public class StudyQuizService {

  private final CurrentUserProvider currentUserProvider;
  private final AiClient aiClient;
  private final CurriculumService curriculumService;

  // AI 를 기다리는 동안 DB 연결을 붙잡지 않으려고 @Transactional 을 붙이지 않았습니다. (CardPhotoService 와 같음)
  public StudyQuizResponse make(StudyQuizRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();

    // 1) 사진 모양
    if (request.image() != null) {
      CardPhotoService.checkImage(request.image());
    }

    // 2) 위기 표현 (단어 목록)
    List<String> texts = new ArrayList<>();
    texts.add(request.topic());
    texts.addAll(request.checklist());
    if (request.material() != null) {
      texts.add(request.material());
    }
    if (CrisisText.hasCrisisText(texts)) {
      return StudyQuizResponse.help();
    }

    // 3) AI (사진이 있으면 사진과 함께)
    String query = (request.topic() + " " + String.join(" ", request.checklist())).trim();
    AiQuestion question = StudyQuiz.question(request, curriculumService.quizLines(userId, query));
    Optional<StudyQuiz.Answer> answer =
        request.image() == null
            ? aiClient.ask(userId, GptUsageKind.ASSIST, question, StudyQuiz.Answer.class)
            : aiClient.askWithImage(
                userId, GptUsageKind.ASSIST, question, request.image(), StudyQuiz.Answer.class);
    if (answer.isEmpty()) {
      return StudyQuizResponse.notMade();
    }

    // 4) 위기 표현 (AI 판단) · 공부 내용이 아님
    if (Boolean.TRUE.equals(answer.get().crisis())) {
      return StudyQuizResponse.help();
    }
    if (Boolean.TRUE.equals(answer.get().notStudy())) {
      return StudyQuizResponse.notStudy();
    }

    // 5) 문제마다 검사
    List<StudyQuiz.Question> questions = StudyQuiz.from(answer.get(), request.count());

    // 6) 지문에 위기 표현 (단어 목록). 사진 속 글에서 옮긴 지문은 2) 에서 보지 못한 글이라 한 번 더 봅니다. (#394)
    if (questions != null && CrisisText.hasCrisisText(passagesOf(questions))) {
      return StudyQuizResponse.help();
    }
    return StudyQuizResponse.of(questions, StudyQuiz.unitOf(answer.get()));
  }

  // 문제들의 지문 (없는 것은 뺍니다)
  private static List<String> passagesOf(List<StudyQuiz.Question> questions) {
    List<String> passages = new ArrayList<>();
    for (StudyQuiz.Question question : questions) {
      if (question.passage() != null) {
        passages.add(question.passage());
      }
    }
    return passages;
  }
}
