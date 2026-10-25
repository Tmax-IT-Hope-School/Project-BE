package com.ithopeschool.projectbe.domain.bias;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 오늘 통화에서 할 "생각 습관 확인 질문" 하나입니다. (이슈 #28)
// 어떤 생각 습관(bias)의 몇 번째 질문(index)인지를 같이 들고 다녀서, 무엇을 물었는지 기록에 남길 수 있습니다.
//
// ■ 고르는 규칙 (pick) — 위에서부터 차례로 따지고, 먼저 맞는 줄에서 끝냅니다.
//   0) 위로하는 통화(기분이 낮음 · 관리 강도 0)이면 묻지 않습니다.          →  null
//      힘든 날에 생각 습관을 캐면 자책으로 이어질 수 있어서입니다. 안전이 먼저입니다. (CLAUDE.md 4장)
//   1) 최근 통화에서 같은 생각 습관이 2번 이상 보였으면 그것                →  그 습관을 더 확인
//      (여러 개가 같은 횟수면 더 최근에 보인 것)
//   2) 어제 고른 못 채운 이유가 생각 습관과 이어지면 그것
//        계획 과다(tooMuch)  →  계획 오류
//        하기 싫음(unwilling) →  기분 따라 판단하기
//        폰·SNS(phone)        →  미래의 나 믿기 ("이것만 보고 하자")
//      피곤·아픔·약속·기타는 이어 붙이지 않습니다. 정말 어쩔 수 없던 일일 수 있어서입니다.
//   3) 요즘 시작이 평균 15분 넘게 늦으면                                   →  미래의 나 믿기
//   4) 최근 통화에서 한 번이라도 보인 생각 습관이 있으면 가장 최근 것
//   5) 가입할 때 한 생각 습관 테스트에서 가장 많이 고른 것                  →  처음 세운 가설을 확인
//      스스로 고른 답이라 실제 기록(1~4)보다 뒤에 둡니다. 통화 기록이 없는 처음 며칠 동안 쓰입니다.
//   6) 어디에도 안 맞으면                                                  →  null (오늘은 따로 묻지 않음)
//
// ■ 같은 질문이 연달아 나오지 않게
//   그 습관의 질문 중 최근에 하지 않은 것을 앞에서부터 고릅니다. 전부 했으면 가장 오래전에 한 것을 다시 씁니다.
//
// 예시
//   위로하는 통화, 무엇이든                                   →  null
//   최근에 [계획 오류, 상황 탓하기, 계획 오류], 어제 이유 phone  →  계획 오류 (2번 보임이 어제 이유보다 먼저)
//   최근 기록 없음, 어제 이유 tooMuch                          →  계획 오류의 0번 질문
//   위와 같은데 최근에 "PLANNING_FALLACY-0" 을 물었음           →  계획 오류의 1번 질문
//   최근 기록 없음, 어제 이유 tired, 시작 지연 30분             →  미래의 나 믿기
//   최근 기록 없음, 어제 이유 없음, 시작 지연 10분              →  null
//   위와 같은데 가입 테스트에서 [다 아니면 0, 계획 오류] 를 많이 고름  →  다 아니면 0
public record BiasQuestion(CognitiveBias bias, int index) {

  // 최근 통화에서 이 횟수 이상 보이면 "자주 보이는 생각 습관" 으로 봅니다. (규칙 1)
  static final int REPEATED_COUNT = 2;
  // 평균 시작 지연이 이 값(분)을 넘으면 "미래의 나 믿기" 를 확인합니다. (규칙 3, 통화 대본의 기준과 같습니다)
  static final int LATE_START_MINUTES = 15;

  // 질문 문장
  public String text() {
    return bias.getQuestions().get(index);
  }

  // 무엇을 물었는지 기록에 남길 때 쓰는 이름입니다.   (계획 오류, 1번)  →  "PLANNING_FALLACY-1"
  public String key() {
    return bias.name() + "-" + index;
  }

  // 가입 테스트 결과 없이 고릅니다. (규칙 5 를 건너뜁니다)
  public static BiasQuestion pick(
      boolean supportive,
      String yesterdayMissReason,
      Integer weekStartDelayMinutes,
      List<CognitiveBias> recentFound,
      List<String> recentAskedKeys) {
    return pick(
        supportive,
        yesterdayMissReason,
        weekStartDelayMinutes,
        recentFound,
        recentAskedKeys,
        List.of());
  }

  // 오늘 할 질문을 고릅니다. 묻지 않는 날이면 null 입니다.
  //
  // supportive: 위로하는 통화인지 (기분이 낮거나 관리 강도가 0)
  // yesterdayMissReason: 어제 못 채운 이유. FE 의 MissReason 글자('tooMuch' 등). 안 골랐으면 null
  // weekStartDelayMinutes: 이번 주 평균 시작 지연(분). 모르면 null
  // recentFound: 최근 통화에서 보인 생각 습관들. 최근 것이 앞에 옵니다. 없으면 빈 목록
  // recentAskedKeys: 최근 통화에서 한 질문의 이름(key)들. 최근 것이 앞에 옵니다. 없으면 빈 목록
  // selfReported: 가입 테스트에서 많이 고른 습관들. 많이 고른 것이 앞에 옵니다. 테스트를 안 했으면 빈 목록
  public static BiasQuestion pick(
      boolean supportive,
      String yesterdayMissReason,
      Integer weekStartDelayMinutes,
      List<CognitiveBias> recentFound,
      List<String> recentAskedKeys,
      List<CognitiveBias> selfReported) {
    // 0) 위로하는 통화
    if (supportive) {
      return null;
    }
    // 1) 자주 보이는 생각 습관
    CognitiveBias repeated = mostRepeated(recentFound);
    if (repeated != null) {
      return questionOf(repeated, recentAskedKeys);
    }
    // 2) 어제 고른 이유
    CognitiveBias fromReason = biasOfMissReason(yesterdayMissReason);
    if (fromReason != null) {
      return questionOf(fromReason, recentAskedKeys);
    }
    // 3) 요즘 시작이 늦음
    if (weekStartDelayMinutes != null && weekStartDelayMinutes > LATE_START_MINUTES) {
      return questionOf(CognitiveBias.FUTURE_SELF, recentAskedKeys);
    }
    // 4) 한 번이라도 보인 것 중 가장 최근 것
    if (!recentFound.isEmpty()) {
      return questionOf(recentFound.get(0), recentAskedKeys);
    }
    // 5) 가입 테스트에서 가장 많이 고른 것
    if (!selfReported.isEmpty()) {
      return questionOf(selfReported.get(0), recentAskedKeys);
    }
    // 6) 오늘은 따로 묻지 않음
    return null;
  }

  // 최근 통화에서 2번 이상 보인 생각 습관 중 가장 많이 보인 것. 없으면 null 입니다.
  // 횟수가 같으면 목록에서 먼저 나온 것(더 최근에 보인 것)을 고릅니다.
  static CognitiveBias mostRepeated(List<CognitiveBias> recentFound) {
    // LinkedHashMap 은 처음 넣은 순서를 기억합니다. 그래서 "먼저 나온 것" 을 알 수 있습니다.
    Map<CognitiveBias, Integer> counts = new LinkedHashMap<>();
    for (CognitiveBias bias : recentFound) {
      counts.merge(bias, 1, Integer::sum);
    }
    CognitiveBias best = null;
    int bestCount = REPEATED_COUNT - 1;
    for (Map.Entry<CognitiveBias, Integer> entry : counts.entrySet()) {
      // 더 클 때만 바꾸므로, 횟수가 같으면 먼저 나온 것이 남습니다.
      if (entry.getValue() > bestCount) {
        best = entry.getKey();
        bestCount = entry.getValue();
      }
    }
    return best;
  }

  // 어제 못 채운 이유와 이어지는 생각 습관. 이어 붙이지 않는 이유면 null 입니다. (규칙 2)
  // 음성 통화 대본(CallScript)도 "오늘 생각 습관이 어제 이유에서 나왔는지" 를 이 함수로 봅니다. (#434)
  public static CognitiveBias biasOfMissReason(String missReason) {
    if (missReason == null) {
      return null;
    }
    return switch (missReason) {
      case "tooMuch" -> CognitiveBias.PLANNING_FALLACY;
      case "unwilling" -> CognitiveBias.EMOTIONAL_REASONING;
      case "phone" -> CognitiveBias.FUTURE_SELF;
      default -> null;
    };
  }

  // 그 습관의 질문 중 최근에 하지 않은 것을 앞에서부터 고릅니다. 전부 했으면 가장 오래전에 한 것입니다.
  static BiasQuestion questionOf(CognitiveBias bias, List<String> recentAskedKeys) {
    int oldestIndex = 0;
    int oldestPosition = -1;
    for (int i = 0; i < bias.getQuestions().size(); i++) {
      BiasQuestion question = new BiasQuestion(bias, i);
      // 목록에서의 자리. 최근 것이 앞(0)에 있으니, 숫자가 클수록 오래전에 한 질문입니다. 없으면 -1
      int position = recentAskedKeys.indexOf(question.key());
      if (position == -1) {
        return question;
      }
      if (position > oldestPosition) {
        oldestPosition = position;
        oldestIndex = i;
      }
    }
    return new BiasQuestion(bias, oldestIndex);
  }
}
