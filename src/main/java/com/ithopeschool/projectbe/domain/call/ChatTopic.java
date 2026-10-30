package com.ithopeschool.projectbe.domain.call;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// 상담 채팅을 "무엇에 대한 대화인지" 와 함께 열 때의 주제입니다. (2026-10-18 "코치랑 이야기하기")
// 사용자: "AI 랑 채팅 이런 걸 더 해야 돼, 후기나 이런 거에서 채팅 더 넣을 곳 있으면 하자"
// FE 의 여러 화면(공부 끝 후기 · 못 한 카드 · 주간 리포트 · 틀린 문제 · 성향 검사 결과 · 달력의 하루)에 있는
// [코치랑 이야기하기] 를 누르면, 그 화면의 사실 몇 줄(ChatAbout.lines)과 함께 채팅이 열립니다.
//
// 주제마다 두 가지를 정해 둡니다.
//   guide : AI 가 그 주제로 어떻게 이야기할지 (대본의 [이번 대화의 주제] 칸, CallScript.topicSection)
//   tags  : AI 가 학생 말에서 짐작해 FE 에 돌려줄 수 있는 "이유" 값과 이름표. FE 의 값과 글자가 같아야 합니다.
//           비어 있으면 그 주제는 tags 를 쓰지 않습니다. (ChatService.cleanTags 가 이 목록에 있는 값만 남깁니다)
//
// ⚠️ 주제를 더하면 ChatAbout.KINDS 에도 더해야 합니다. (ChatTopicTest 가 확인)
public enum ChatTopic {
  SESSION_REVIEW(
      "방금 끝낸 공부의 후기",
      List.of(
          "'어땠어?' 처럼 짧게 후기를 물어. 숫자(집중도 · 폰 든 횟수 등)는 하나만 짚어.",
          "학생 말에서 체크리스트를 다 못 한 이유가 보이면 tags 로 짐작하고, '이유: 폰 · 피곤 — 맞아?' 처럼 맞는지 확인해.",
          "마지막엔 다음 공부 때 바꿀 것 딱 하나를 같이 정해."),
      shortfallTags()),
  CARD_FAIL(
      "하지 못한 카드",
      List.of(
          "못 한 걸 탓하지 마. 왜 못 했는지 편하게 물어.",
          "이유가 보이면 tags 로 짐작하고, '이유: 피곤 — 맞아?' 처럼 맞는지 확인해.",
          "그다음 내일 다시 할지 물어봐. quickReplies 예: '내일로 다시 짜 줘' · '이번엔 빼 줘'."),
      cardFailTags()),
  WEEKLY_REPORT(
      "주간 리포트 · AI 분석",
      List.of(
          "숫자 하나를 짚어서 왜 그랬을지 학생에게 물어.",
          "다음 주에 바꿀 것 하나를 같이 정해. 계획이 필요하면 make_plan 이나 quickReplies 로 이어 가."),
      Map.of()),
  // 틀린 문제는 과목 쌤 방에서 열립니다. 과목 쌤처럼 되묻기 전에 먼저 설명합니다. (2026-10-31 "질문이 다시 질문으로 돌아옴")
  QUIZ_WRONG(
      "틀린 확인 문제",
      List.of(
          "되묻지 말고 먼저 설명해. 틀린 문제마다 정답이 왜 맞는지와 학생이 고른 답이 왜 아닌지를 짧게 말해.",
          "설명은 과외 선생님처럼 풀이 단계를 번호 목록으로 써도 돼.",
          "다 설명했으면 비슷한 문제 하나를 내 줘. 질문은 그 하나뿐이야. 더 풀고 싶어 하면 start_quiz."),
      Map.of()),
  PSYCH_RESULT(
      "성향 검사 결과",
      List.of(
          "높게 나온 성향 하나를 실제 공부 장면과 이어서 물어. (예: 공부를 시작하기까지 오래 걸리는 편인지)",
          "검사가 '믿기 어려움' 이면 성향을 단정하지 말고 하나만 물어.",
          "성향을 좋다 · 나쁘다로 평가하지 마."),
      Map.of()),
  DAY_REVIEW(
      "달력의 하루", List.of("그날 숫자로 잘 된 것 하나, 아쉬운 것 하나를 물어.", "다음에 비슷한 날에 해 볼 것 하나를 같이 정해."), Map.of());

  private final String label;
  private final List<String> guide;
  // 값 → 이름표. 순서를 지키려고 LinkedHashMap 으로 만듭니다.
  private final Map<String, String> tags;

  ChatTopic(String label, List<String> guide, Map<String, String> tags) {
    this.label = label;
    this.guide = guide;
    this.tags = tags;
  }

  // 대본에 쓰는 이름. 예) "방금 끝낸 공부의 후기"
  public String label() {
    return label;
  }

  // 대본에 넣는 이야기 방법 (줄마다 하나)
  public List<String> guide() {
    return guide;
  }

  // AI 가 돌려줄 수 있는 이유 값 → 이름표. 비어 있으면 tags 를 쓰지 않는 주제입니다.
  public Map<String, String> tags() {
    return tags;
  }

  // 글자 → 주제. 모르는 글자면 빈 값입니다. (요청은 ChatAbout 의 @Pattern 이 먼저 거릅니다)
  public static Optional<ChatTopic> from(String kind) {
    for (ChatTopic topic : values()) {
      if (topic.name().equals(kind)) {
        return Optional.of(topic);
      }
    }
    return Optional.empty();
  }

  // 종료 팝업의 "체크리스트를 다 못 한 이유". FE src/types/index.ts 의 ShortfallReason 과 같은 글자입니다.
  private static Map<String, String> shortfallTags() {
    Map<String, String> tags = new LinkedHashMap<>();
    tags.put("tooMuch", "계획이 많았음");
    tags.put("stuck", "막힘");
    tags.put("phone", "폰");
    tags.put("mindWander", "딴생각");
    tags.put("tired", "피곤");
    tags.put("external", "바깥 일");
    tags.put("etc", "기타");
    return tags;
  }

  // 카드 "못 했어요" 원인. FE src/lib/cardMarks.ts 의 CardFailCause(CARD_FAIL_CAUSES)와 같은 글자입니다.
  private static Map<String, String> cardFailTags() {
    Map<String, String> tags = new LinkedHashMap<>();
    tags.put("tired", "피곤 · 수면");
    tags.put("sick", "아픔");
    tags.put("event", "행사 · 약속");
    tags.put("tooMuch", "계획 과다");
    tags.put("phone", "폰 · SNS");
    tags.put("unwilling", "하기 싫음");
    tags.put("procrastinated", "미루다 보니");
    tags.put("etc", "기타");
    return tags;
  }
}
