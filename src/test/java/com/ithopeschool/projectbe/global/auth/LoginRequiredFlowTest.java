package com.ithopeschool.projectbe.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.global.time.DayRule;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

// 서버 전체를 진짜로 켜고(가짜 없이) "로그인한 사람만 쓸 수 있는지" 를 모든 API 에 대해 한 번에 봅니다. (이슈 #20)
//
//   1. 토큰 없이 부르면 상태 확인·가입·로그인을 뺀 모든 API 가 401 이다
//   2. A 의 토큰으로 B 의 계획을 부르면 404 다 (있는지 없는지도 알려 주지 않습니다)
//
// ⚠️ 새 API 를 만들면 이 테스트가 실패합니다. 아래 LOGIN_REQUIRED 표에 한 줄을 더해 주세요.
//    그 한 줄이 "이 API 는 토큰 없이 부르면 401 이다" 를 확인해 줍니다.
//    401 이 안 나오면 Service 에서 currentUserProvider.getCurrentUserId() 를 부르지 않은 것입니다. (CLAUDE.md 3장)
// 다른 테스트의 create-drop이 이 테스트의 테이블을 지우지 않도록 DB 이름을 분리합니다.
@SpringBootTest(
    properties = "spring.datasource.url=jdbc:h2:mem:login-required-flow;MODE=PostgreSQL")
@AutoConfigureMockMvc
class LoginRequiredFlowTest {

  private static final String PASSWORD = "study2026";

  // API 하나입니다.
  // method · pattern: Controller 에 적힌 방식과 주소 (예: PATCH /api/v1/study-plans/{id})
  // path: 실제로 부를 주소. {id} 같은 자리에 값을 넣은 것입니다.
  // body: 같이 보낼 JSON. 없으면 null. 검사(@Valid)를 통과하는 올바른 값이어야 합니다.
  //   (틀린 값을 보내면 로그인 검사보다 먼저 400 이 나와서, 401 인지 확인할 수 없습니다)
  private record Api(String method, String pattern, String path, String body) {

    private String name() {
      return method + " " + pattern;
    }
  }

  private static final String PLAN_BODY =
      "{\"date\":\"2026-10-12\",\"subjectId\":\"1\",\"content\":\"수학 문제집\",\"targetMinutes\":60}";

  private static final String CARD_BODY =
      "{\"title\":\"수학 문제집\",\"date\":\"2026-10-12\",\"durationMinutes\":60,"
          + "\"checklist\":[{\"text\":\"42쪽\"}]}";

  private static final String EXAM_BODY =
      "{\"type\":\"mock\",\"name\":\"10월 모의고사\",\"date\":\"2026-10-20\","
          + "\"subjectScores\":[{\"subjectId\":\"1\",\"expectedScore\":85}]}";

  private static final String COACH_BODY =
      "{\"numbers\":[{\"label\":\"완주율\",\"value\":\"55%\",\"basis\":\"20번\"}],"
          + "\"maxCareLevel\":2,\"maxHarshness\":3}";

  // 로그인한 사람만 쓸 수 있는 API 들입니다. 새 API 를 만들면 여기에 한 줄을 더합니다.
  private static final List<Api> LOGIN_REQUIRED =
      List.of(
          new Api(
              "GET",
              "/api/v1/study-sessions/target-recommendation",
              "/api/v1/study-sessions/target-recommendation?subjectId=1&currentTargetMinutes=50",
              null),
          // 내 정보 · 계정
          new Api("GET", "/api/v1/auth/me", "/api/v1/auth/me", null),
          new Api("GET", "/api/v1/auth/profile", "/api/v1/auth/profile", null),
          new Api(
              "PUT",
              "/api/v1/auth/profile",
              "/api/v1/auth/profile",
              "{\"name\":\"김티맥\",\"nickname\":\"티맥이\",\"diaryAnalysis\":false}"),
          new Api(
              "PUT",
              "/api/v1/auth/password",
              "/api/v1/auth/password",
              "{\"currentPassword\":\"study2026\",\"newPassword\":\"study2027\"}"),
          new Api("POST", "/api/v1/auth/logout", "/api/v1/auth/logout", null),
          new Api(
              "POST",
              "/api/v1/auth/withdraw",
              "/api/v1/auth/withdraw",
              "{\"password\":\"study2026\"}"),
          // 과목
          new Api("GET", "/api/v1/subjects", "/api/v1/subjects", null),
          new Api(
              "POST",
              "/api/v1/subjects",
              "/api/v1/subjects",
              "{\"name\":\"수학\",\"color\":\"red\"}"),
          new Api("PATCH", "/api/v1/subjects/{id}", "/api/v1/subjects/1", "{\"name\":\"수학\"}"),
          new Api("DELETE", "/api/v1/subjects/{id}", "/api/v1/subjects/1", null),
          // 계획 루틴
          new Api("GET", "/api/v1/routines", "/api/v1/routines", null),
          new Api(
              "POST",
              "/api/v1/routines",
              "/api/v1/routines",
              "{\"weekdays\":[1],\"kind\":\"play\",\"content\":\"rest\",\"targetMinutes\":20}"),
          new Api("PATCH", "/api/v1/routines/{id}", "/api/v1/routines/1", "{\"targetMinutes\":20}"),
          new Api("DELETE", "/api/v1/routines/{id}", "/api/v1/routines/1", null),
          // 공부 계획
          new Api("GET", "/api/v1/study-plans", "/api/v1/study-plans?date=2026-10-12", null),
          new Api("POST", "/api/v1/study-plans", "/api/v1/study-plans", PLAN_BODY),
          new Api(
              "POST",
              "/api/v1/study-plans/batch",
              "/api/v1/study-plans/batch",
              "{\"plans\":[" + PLAN_BODY + "]}"),
          // 말로 계획 세우기 (아무나 부르면 GPT 비용이 나갑니다)
          new Api(
              "POST",
              "/api/v1/study-plans/parse",
              "/api/v1/study-plans/parse",
              "{\"date\":\"2026-10-12\",\"text\":\"수학 문제집 2시간\"}"),
          new Api("PATCH", "/api/v1/study-plans/{id}", "/api/v1/study-plans/1", "{\"done\":true}"),
          new Api("DELETE", "/api/v1/study-plans/{id}", "/api/v1/study-plans/1", null),
          // 카드 (이슈 #222)
          new Api("GET", "/api/v1/cards", "/api/v1/cards?date=2026-10-12", null),
          new Api("POST", "/api/v1/cards", "/api/v1/cards", CARD_BODY),
          new Api("PATCH", "/api/v1/cards/{id}", "/api/v1/cards/1", "{\"title\":\"수학\"}"),
          new Api("DELETE", "/api/v1/cards/{id}", "/api/v1/cards/1", null),
          // 미루기 · 실패 · 사후 입력 · 휴식 처리 (이슈 #225)
          new Api(
              "GET",
              "/api/v1/cards/{id}/postpone-limit",
              "/api/v1/cards/1/postpone-limit?date=2026-10-12",
              null),
          new Api(
              "POST",
              "/api/v1/cards/{id}/postpone",
              "/api/v1/cards/1/postpone",
              "{\"date\":\"2026-10-12\",\"reason\":\""
                  + "가".repeat(100)
                  + "\",\"newDate\":\"2026-10-13\",\"newStartTime\":\"20:00\"}"),
          new Api(
              "POST",
              "/api/v1/cards/{id}/fail",
              "/api/v1/cards/1/fail",
              "{\"date\":\"2026-10-12\",\"cause\":\"tired\"}"),
          new Api(
              "POST",
              "/api/v1/cards/{id}/late-entry",
              "/api/v1/cards/1/late-entry",
              "{\"date\":\"2026-10-12\",\"startedAt\":1790000000000,\"endedAt\":1790003000000}"),
          new Api(
              "GET",
              "/api/v1/card-day-marks",
              "/api/v1/card-day-marks?from=2026-10-01&to=2026-10-07",
              null),
          new Api("POST", "/api/v1/rest-days/{date}", "/api/v1/rest-days/2026-10-12", null),
          new Api(
              "GET", "/api/v1/rest-days", "/api/v1/rest-days?from=2026-10-01&to=2026-10-07", null),
          // 퀴즈 결과 · 통계 탭 (2026-10-24)
          new Api(
              "POST",
              "/api/v1/quiz-results",
              "/api/v1/quiz-results",
              "{\"subjectId\":1,\"unit\":\"함수\",\"correctCount\":1,\"totalCount\":2,"
                  + "\"questionSeconds\":[30,40]}"),
          new Api(
              "GET",
              "/api/v1/quiz-results/stats",
              "/api/v1/quiz-results/stats?from=2026-10-01&to=2026-10-07",
              null),
          // 카드 결과 (이슈 #229)
          new Api(
              "GET",
              "/api/v1/cards/results",
              "/api/v1/cards/results?from=2026-10-01&to=2026-10-07",
              null),
          new Api("GET", "/api/v1/cards/plan-pattern", "/api/v1/cards/plan-pattern?goalId=1", null),
          // 이번 주 목표 (내 계획 · 기록 · 목표를 봅니다. 이슈 #88)
          new Api("GET", "/api/v1/weekly-goals/proposal", "/api/v1/weekly-goals/proposal", null),
          new Api(
              "PUT",
              "/api/v1/weekly-goals/{weekStart}",
              "/api/v1/weekly-goals/2026-10-19",
              "{\"kind\":\"keep\",\"target\":50,\"baseline\":50}"),
          new Api(
              "GET",
              "/api/v1/weekly-goals",
              "/api/v1/weekly-goals?from=2026-10-01&to=2026-10-31",
              null),
          // 주간 점수 (내 퀴즈 결과 · 계획 · 기록을 봅니다. 이슈 #412)
          new Api(
              "GET", "/api/v1/weekly-scores/{weekStart}", "/api/v1/weekly-scores/2026-10-19", null),
          // 달력 (내 카드 결과 · 체크인을 봅니다. 이슈 #235)
          new Api("GET", "/api/v1/calendar", "/api/v1/calendar?month=2026-10", null),
          // 관리 단계 (내 카드 결과 · 체크인으로 판정해 저장합니다. 이슈 #62)
          new Api("POST", "/api/v1/care-stages/today", "/api/v1/care-stages/today", null),
          new Api(
              "GET",
              "/api/v1/care-stages",
              "/api/v1/care-stages?from=2026-10-01&to=2026-10-18",
              null),
          // 자기 실험 (내 카드에 실험 규칙을 붙입니다. 이슈 #52 · #54)
          new Api(
              "POST",
              "/api/v1/experiments",
              "/api/v1/experiments",
              "{\"hypothesis\":\"폰\",\"strategyId\":\"phone-bag\",\"cardIds\":[\"1\"]}"),
          new Api("GET", "/api/v1/experiments", "/api/v1/experiments", null),
          // 잠금 해제 기록
          new Api(
              "POST",
              "/api/v1/lock-events",
              "/api/v1/lock-events",
              "{\"occurredAt\":\"2026-10-17T10:00:00Z\",\"appName\":\"app\",\"reason\":\"reason\"}"),
          new Api(
              "GET",
              "/api/v1/lock-events/stats",
              "/api/v1/lock-events/stats?startDate=2026-10-17&endDate=2026-10-17",
              null),
          // 관리 설정
          new Api("GET", "/api/v1/settings", "/api/v1/settings", null),
          new Api("PATCH", "/api/v1/settings", "/api/v1/settings", "{\"maxHarshness\":5}"),
          // 시험 점수
          new Api("GET", "/api/v1/exams", "/api/v1/exams", null),
          new Api("POST", "/api/v1/exams", "/api/v1/exams", EXAM_BODY),
          new Api(
              "PATCH",
              "/api/v1/exams/{id}",
              "/api/v1/exams/1",
              "{\"subjectScores\":[{\"subjectId\":\"1\",\"actualScore\":80}]}"),
          // 시험을 목표에 잇기 · 떼기 (이슈 #228)
          new Api("PATCH", "/api/v1/exams/{id}/goal", "/api/v1/exams/1/goal", "{\"goalId\":\"1\"}"),
          // 공부 기록
          new Api(
              "POST",
              "/api/v1/study-sessions",
              "/api/v1/study-sessions",
              "{\"subjectId\":\"1\",\"startedAt\":1790000000000,"
                  + "\"endedAt\":1790003000000,\"pauseCount\":0}"),
          new Api(
              "GET",
              "/api/v1/study-sessions",
              "/api/v1/study-sessions?from=2026-10-01&to=2026-10-07",
              null),
          new Api(
              "GET",
              "/api/v1/study-sessions/today/total",
              "/api/v1/study-sessions/today/total",
              null),
          new Api(
              "GET",
              "/api/v1/study-sessions/by-plan",
              "/api/v1/study-sessions/by-plan?date=2026-10-12",
              null),
          new Api("DELETE", "/api/v1/study-sessions", "/api/v1/study-sessions", null),
          // 하루 신호 (수면·걸음은 건강 데이터입니다)
          new Api(
              "POST",
              "/api/v1/daily-signals",
              "/api/v1/daily-signals",
              "{\"date\":\"2026-10-12\"}"),
          // 회고 (시험 후 · 주간)
          new Api(
              "POST",
              "/api/v1/reviews",
              "/api/v1/reviews",
              "{\"type\":\"weekly\",\"weekStart\":\"2026-10-12\",\"answers\":[\"a\",\"\",\"\"]}"),
          // 체크인 (기분·약속은 개인적인 내용입니다)
          new Api(
              "PUT",
              "/api/v1/checkins/{date}",
              "/api/v1/checkins/2026-10-12",
              "{\"source\":\"manual\"}"),
          new Api(
              "GET", "/api/v1/checkins", "/api/v1/checkins?from=2026-10-01&to=2026-10-07", null),
          new Api(
              "GET", "/api/v1/checkins/today/questions", "/api/v1/checkins/today/questions", null),
          new Api("DELETE", "/api/v1/checkins", "/api/v1/checkins", null),
          // 일기 (원문은 본인만 봅니다, 이슈 #60)
          new Api("POST", "/api/v1/diaries", "/api/v1/diaries", "{\"content\":\"일기\"}"),
          new Api("GET", "/api/v1/diaries", "/api/v1/diaries?from=2026-10-01&to=2026-10-07", null),
          new Api("DELETE", "/api/v1/diaries/{id}", "/api/v1/diaries/1", null),
          // 구글 캘린더 일정 알림 (일정 제목이 들어 있어서 본인만)
          new Api("PUT", "/api/v1/calendar-alarms", "/api/v1/calendar-alarms", "{\"events\":[]}"),
          new Api("DELETE", "/api/v1/calendar-alarms", "/api/v1/calendar-alarms", null),
          // 사진 채점 (아무나 부르면 GPT 비용이 나가고, 채점 결과는 본인만 봅니다)
          new Api(
              "POST",
              "/api/v1/answer-checks",
              "/api/v1/answer-checks",
              "{\"mode\":\"MARKED\",\"image\":\"data:image/jpeg;base64,AQID\",\"date\":\"2026-10-24\"}"),
          new Api("GET", "/api/v1/answer-checks", "/api/v1/answer-checks?date=2026-10-24", null),
          // 같은 주소를 기간(from · to)으로 불러도 401 인지 봅니다. (주간 리포트)
          new Api(
              "GET",
              "/api/v1/answer-checks",
              "/api/v1/answer-checks?from=2026-10-18&to=2026-10-24",
              null),
          new Api(
              "PATCH",
              "/api/v1/answer-checks/{id}/items/{itemId}",
              "/api/v1/answer-checks/1/items/1",
              "{\"result\":\"WRONG\"}"),
          // 이번 주에 고른 것 (이슈 #406, 내가 고른 것은 본인만 봅니다)
          new Api(
              "PUT",
              "/api/v1/weekly-picks/{weekStart}",
              "/api/v1/weekly-picks/2026-10-26",
              "{\"title\":\"폰을 다른 방에 두기\",\"hint\":\"손이 안 닿으면 덜 멈춰요\",\"skip\":false}"),
          new Api(
              "GET",
              "/api/v1/weekly-picks",
              "/api/v1/weekly-picks?from=2026-10-01&to=2026-10-31",
              null),
          // AI 메시지
          new Api("GET", "/api/v1/messages", "/api/v1/messages", null),
          new Api("PATCH", "/api/v1/messages/{id}/read", "/api/v1/messages/1/read", null),
          // AI 코치 · AI 통화 (아무나 부르면 GPT 비용이 나갑니다)
          new Api(
              "POST",
              "/api/v1/coach/weekly",
              "/api/v1/coach/weekly",
              "{\"mondayKey\":\"2026-10-05\"," + COACH_BODY.substring(1)),
          new Api("POST", "/api/v1/coach/daily", "/api/v1/coach/daily", COACH_BODY),
          new Api("POST", "/api/v1/coach/monthly", "/api/v1/coach/monthly", COACH_BODY),
          new Api(
              "POST",
              "/api/v1/realtime/session",
              "/api/v1/realtime/session",
              "{\"careLevel\":1,\"maxHarshness\":3}"),
          // 확인 통화 문 (오늘 끝낸 공부, 2026-10-18)
          new Api("GET", "/api/v1/realtime/check-call", "/api/v1/realtime/check-call", null),
          // AI 통화 기록 (자막은 학생의 대화입니다)
          new Api("GET", "/api/v1/calls", "/api/v1/calls", null),
          new Api("GET", "/api/v1/calls/habits", "/api/v1/calls/habits", null),
          // 상담 채팅 (이슈 #238)
          new Api("POST", "/api/v1/chats", "/api/v1/chats", "{\"careLevel\":2,\"maxHarshness\":3}"),
          new Api(
              "POST",
              "/api/v1/chats/{id}/messages",
              "/api/v1/chats/1/messages",
              "{\"lines\":[{\"speaker\":\"me\",\"text\":\"응\"}]}"),
          new Api(
              "POST",
              "/api/v1/chats/{id}/finish",
              "/api/v1/chats/1/finish",
              "{\"lines\":[{\"speaker\":\"me\",\"text\":\"응\"}]}"),
          // 온보딩 마침 표시 (2026-10-17)
          new Api("POST", "/api/v1/onboarding/completion", "/api/v1/onboarding/completion", null),
          // 목표 만들기 대화 (이슈 #239)
          new Api("POST", "/api/v1/goal-chats", "/api/v1/goal-chats", null),
          new Api(
              "POST",
              "/api/v1/goal-chats/{id}/messages",
              "/api/v1/goal-chats/abc/messages",
              "{\"lines\":[{\"speaker\":\"me\",\"text\":\"응\"}]}"),
          new Api(
              "POST",
              "/api/v1/goal-chats/{id}/finish",
              "/api/v1/goal-chats/abc/finish",
              "{\"lines\":[{\"speaker\":\"me\",\"text\":\"응\"}]}"),
          // 웹 푸시 (내 기기를 알림 받을 곳으로 등록합니다)
          new Api("GET", "/api/v1/push/public-key", "/api/v1/push/public-key", null),
          new Api(
              "POST",
              "/api/v1/push/subscriptions",
              "/api/v1/push/subscriptions",
              "{\"endpoint\":\"https://fcm.googleapis.com/x\",\"keys\":{\"p256dh\":\"BNc\",\"auth\":\"tBH\"}}"),
          new Api(
              "DELETE",
              "/api/v1/push/subscriptions",
              "/api/v1/push/subscriptions",
              "{\"endpoint\":\"https://fcm.googleapis.com/x\"}"),
          // 생각 습관 테스트 (학생의 심리 기록입니다)
          new Api("GET", "/api/v1/thinking-tests/latest", "/api/v1/thinking-tests/latest", null),
          new Api("GET", "/api/v1/thinking-tests/gap", "/api/v1/thinking-tests/gap", null),
          new Api(
              "GET",
              "/api/v1/thinking-tests/exam-gap",
              "/api/v1/thinking-tests/exam-gap?examDate=2026-10-20",
              null),
          new Api(
              "POST",
              "/api/v1/thinking-tests",
              "/api/v1/thinking-tests",
              "{\"testVersion\":2,\"scores\":["
                  + "{\"habit\":\"PLANNING_FALLACY\",\"average\":4.0},"
                  + "{\"habit\":\"FUTURE_SELF\",\"average\":4.0},"
                  + "{\"habit\":\"WHAT_THE_HELL\",\"average\":4.0},"
                  + "{\"habit\":\"EMOTIONAL_REASONING\",\"average\":4.0},"
                  + "{\"habit\":\"EXTERNAL_BLAME\",\"average\":4.0},"
                  + "{\"habit\":\"ALL_OR_NOTHING\",\"average\":4.0},"
                  + "{\"habit\":\"MORAL_LICENSING\",\"average\":4.0},"
                  + "{\"habit\":\"OVERGENERALIZING\",\"average\":4.0}]}"),
          new Api(
              "POST",
              "/api/v1/calls/{id}/finish",
              "/api/v1/calls/1/finish",
              "{\"lines\":[{\"speaker\":\"me\",\"text\":\"응\"}]}"),
          // 성찰일기 처방 (처방 이유가 학생의 못 채운 이유입니다)
          new Api("GET", "/api/v1/reflections/current", "/api/v1/reflections/current", null),
          // 온보딩 설문 · 처음 계획 (학년·생활 리듬·자기 인식 같은 학생의 정보입니다)
          new Api("GET", "/api/v1/onboarding", "/api/v1/onboarding", null),
          new Api(
              "PUT",
              "/api/v1/onboarding",
              "/api/v1/onboarding",
              "{\"grade\":\"고2\",\"targetExam\":\"수능\",\"targetExamDate\":\"2027-11-18\","
                  + "\"dailyAvailableMinutes\":180}"),
          new Api(
              "PATCH", "/api/v1/onboarding", "/api/v1/onboarding", "{\"chronotype\":\"night\"}"),
          new Api(
              "GET", "/api/v1/onboarding/initial-plan", "/api/v1/onboarding/initial-plan", null),
          new Api(
              "POST",
              "/api/v1/onboarding/initial-plan",
              "/api/v1/onboarding/initial-plan",
              "{\"dailyMinutes\":180,\"daysPerWeek\":5}"),
          new Api(
              "PUT",
              "/api/v1/onboarding/initial-plan",
              "/api/v1/onboarding/initial-plan",
              "{\"dailyMinutes\":180,\"daysPerWeek\":5}"),
          // 개인정보 동의 (동의 문구가 바뀌었을 때 다시 동의하는 API. 이슈 #71)
          new Api("GET", "/api/v1/auth/consent", "/api/v1/auth/consent", null),
          new Api(
              "POST",
              "/api/v1/auth/consent",
              "/api/v1/auth/consent",
              "{\"terms\":true,\"privacy\":true,\"over14\":true,\"version\":\"v1\"}"),
          // 첫 가설 (설문 답으로 만든 문장이라 학생의 정보입니다)
          new Api("GET", "/api/v1/insights/hypotheses", "/api/v1/insights/hypotheses", null),
          new Api("GET", "/api/v1/insights/score-study", "/api/v1/insights/score-study", null),
          // 인사이트 반응 버튼 (내 인사이트에만 반응할 수 있습니다)
          new Api(
              "POST",
              "/api/v1/insights/{id}/reaction",
              "/api/v1/insights/1/reaction",
              "{\"response\":\"surprised\"}"),
          new Api("GET", "/api/v1/insights/stats", "/api/v1/insights/stats", null),
          // 실패 원인 분석 (못 채운 날과 그 이유라서 학생의 정보입니다)
          new Api("GET", "/api/v1/stats/miss-reasons", "/api/v1/stats/miss-reasons", null),
          // 리듬·패턴 분석 (공부한 날·시간대·시험 날이라 학생의 정보입니다)
          new Api("GET", "/api/v1/stats/rhythm", "/api/v1/stats/rhythm", null),
          // 내 학교 학사일정 (가입 정보의 학교 번호로 찾습니다. 이슈 #282)
          new Api("GET", "/api/v1/school/events", "/api/v1/school/events", null),
          new Api("POST", "/api/v1/school/events/refresh", "/api/v1/school/events/refresh", null),
          new Api("GET", "/api/v1/school/today", "/api/v1/school/today", null),
          // 내 학교 한 학년의 반 번호 목록 (가입 정보의 학교 번호로 찾습니다. 2026-10-23)
          new Api("GET", "/api/v1/school/classes", "/api/v1/school/classes?grade=2", null),
          // 내 학교 홈페이지 가정통신문 · 공지사항 (가입 정보의 학교 번호로 찾습니다. 2026-10-18)
          new Api("GET", "/api/v1/school/notices", "/api/v1/school/notices", null),
          // 카드 체크리스트 추천 (내 지난 기록을 보고 AI 를 부릅니다. 이슈 #236)
          new Api("POST", "/api/v1/ai/checklist", "/api/v1/ai/checklist", "{\"title\":\"수학 문제집\"}"),
          // 첫 준비 행동 (AI 를 부르고 호출 기록을 사람마다 남깁니다. 이슈 #236)
          new Api(
              "POST", "/api/v1/ai/first-step", "/api/v1/ai/first-step", "{\"item\":\"단어 외우기\"}"),
          // 예상 실패 원인 선지 (내 최근 30일 실패 원인을 봅니다. 이슈 #237)
          new Api(
              "POST",
              "/api/v1/ai/fail-cause-options",
              "/api/v1/ai/fail-cause-options",
              "{\"title\":\"수학 문제집\"}"),
          // 시험 회고 → 다음 카드에 넣을 한 가지 (내 회고·점수를 봅니다. 이슈 #237)
          new Api(
              "POST",
              "/api/v1/ai/exam-next-step",
              "/api/v1/ai/exam-next-step",
              "{\"examId\":\"1\"}"),
          // 글에 이름표 붙이기 (AI 를 부르고 호출 기록을 사람마다 남깁니다. 이슈 #237)
          new Api(
              "POST",
              "/api/v1/ai/classify-text",
              "/api/v1/ai/classify-text",
              "{\"kind\":\"memo\",\"text\":\"문자 보내기\"}"),
          // 루틴 카드 제안 (AI 를 부르고 호출 기록을 사람마다 남깁니다. 이슈 #239)
          new Api(
              "POST",
              "/api/v1/ai/routine-cards",
              "/api/v1/ai/routine-cards",
              "{\"goal\":\"영단어장 1회독\",\"plan\":\"폰을 다른 방에 두기\"}"),
          // AI 자기 분석 (내 기록에서 계산한 숫자와 내 통화의 생각 습관을 봅니다. 이슈 #237)
          new Api(
              "POST",
              "/api/v1/ai/analysis",
              "/api/v1/ai/analysis",
              "{\"facts\":[{\"key\":\"slotRate\",\"group\":\"tendency\",\"label\":\"시간대별 실행률\",\"value\":\"아침 95%\",\"basis\":\"카드 64번\"}]}"),
          // 즉석 카드 (내 과목·최근 카드 제목을 봅니다. 이슈 #237)
          new Api("POST", "/api/v1/ai/quick-card", "/api/v1/ai/quick-card", "{\"text\":\"수학 하자\"}"),
          // 말로 설정 바꾸기 (내 관리 설정을 봅니다. 바꾸지는 않습니다. 이슈 #237)
          new Api(
              "POST",
              "/api/v1/ai/settings-from-text",
              "/api/v1/ai/settings-from-text",
              "{\"text\":\"아침엔 알림 꺼 줘\"}"),
          // 위험 예측 (무너질 가능성이 높은 날과 그 이유라서 학생의 정보입니다. 이슈 #49)
          new Api(
              "POST",
              "/api/v1/risk-forecasts",
              "/api/v1/risk-forecasts",
              "{\"date\":\"2026-10-13\"}"),
          new Api(
              "GET", "/api/v1/risk-forecasts/{date}", "/api/v1/risk-forecasts/2026-10-13", null),
          new Api(
              "GET",
              "/api/v1/risk-forecasts",
              "/api/v1/risk-forecasts?from=2026-10-01&to=2026-10-31",
              null),
          // 개선안 3개 제안 (내 실패 원인을 봅니다. 이슈 #51)
          new Api(
              "GET", "/api/v1/improvements/suggestions", "/api/v1/improvements/suggestions", null),
          // 심리검사 (학생의 성향 답이라 학생의 정보입니다. 이슈 #219)
          new Api("POST", "/api/v1/psych-tests", "/api/v1/psych-tests", "{\"answers\":{\"S1\":4}}"),
          new Api("GET", "/api/v1/psych-tests/latest", "/api/v1/psych-tests/latest", null),
          // 성향 요약 3줄 (내 심리검사를 봅니다. 이슈 #237)
          new Api("POST", "/api/v1/ai/profile-lines", "/api/v1/ai/profile-lines", null),
          // 고정 일정 (학교 · 학원 시간이라 학생의 생활 정보입니다. 이슈 #223)
          new Api("GET", "/api/v1/fixed-blocks", "/api/v1/fixed-blocks", null),
          new Api(
              "POST",
              "/api/v1/fixed-blocks",
              "/api/v1/fixed-blocks",
              "{\"title\":\"학교\",\"repeatDays\":[1],\"startTime\":\"08:00\",\"endTime\":\"16:30\"}"),
          new Api(
              "PATCH", "/api/v1/fixed-blocks/{id}", "/api/v1/fixed-blocks/1", "{\"title\":\"학원\"}"),
          new Api("DELETE", "/api/v1/fixed-blocks/{id}", "/api/v1/fixed-blocks/1", null),
          // 목표 (학생의 장기 목표와 다짐이라 학생의 정보입니다. 이슈 #221)
          new Api("GET", "/api/v1/goals", "/api/v1/goals", null),
          new Api(
              "POST",
              "/api/v1/goals",
              "/api/v1/goals",
              "{\"kind\":\"score\",\"title\":\"수학\",\"dDay\":\"2099-01-01\",\"currentScore\":\"5등급\",\"benefit\":\"a\",\"cost\":\"b\",\"obstacle\":\"c\",\"plan\":\"d\"}"),
          new Api("PATCH", "/api/v1/goals/{id}", "/api/v1/goals/1", "{\"plan\":\"d\"}"),
          new Api("GET", "/api/v1/goals/{id}/history", "/api/v1/goals/1/history", null),
          // 말로 카드 만들기 (내 목표 · 카드 · 기록을 봅니다. 이슈 #237)
          new Api(
              "POST",
              "/api/v1/ai/cards/parse",
              "/api/v1/ai/cards/parse",
              "{\"date\":\"2026-10-17\",\"text\":\"수학\"}"),
          // 사진으로 카드 만들기 (내 목표 · 카드 · 기록을 봅니다. 2026-10-18)
          new Api(
              "POST",
              "/api/v1/ai/cards/from-photo",
              "/api/v1/ai/cards/from-photo",
              "{\"image\":\"data:image/jpeg;base64,AAAA\",\"date\":\"2026-10-18\"}"),
          // 사진으로 증빙 적기 (방금 한 공부의 사진이라 학생의 정보입니다. Project-FE#670)
          new Api(
              "POST",
              "/api/v1/ai/proof-from-photo",
              "/api/v1/ai/proof-from-photo",
              "{\"image\":\"data:image/jpeg;base64,AAAA\"}"),
          // 카드 폼 기본값 (내 카드 · 목표 · 기록을 봅니다. 이슈 #237)
          new Api(
              "POST", "/api/v1/ai/card-defaults", "/api/v1/ai/card-defaults", "{\"title\":\"수학\"}"),
          // AI 하루 계획 (내 고정 일정 · 카드 · 목표 · 기록 · 설문 · 심리검사를 봅니다. 이슈 #237)
          new Api(
              "POST", "/api/v1/ai/day-plan", "/api/v1/ai/day-plan", "{\"date\":\"2099-01-01\"}"),
          // AI 오늘의 미션 (오늘 과목 · 시험 · 준비하는 것을 보고 미션을 짭니다. 2026-10-18)
          new Api(
              "POST",
              "/api/v1/ai/daily-missions",
              "/api/v1/ai/daily-missions",
              "{\"date\":\"2099-01-01\"}"),
          // 계획 핑퐁 (학생의 요청으로 계획을 고칩니다. 2026-10-18)
          new Api(
              "POST",
              "/api/v1/ai/day-plan/revise",
              "/api/v1/ai/day-plan/revise",
              "{\"drafts\":[{\"date\":\"2099-01-01\",\"startTime\":\"19:00\",\"durationMinutes\":30,"
                  + "\"title\":\"수학\"}],\"request\":\"줄여 줘\"}"),
          new Api("POST", "/api/v1/ai/quiz", "/api/v1/ai/quiz", "{\"topic\":\"수열의 극한\"}"),
          // 긴 계획(30일) (목표 한 줄로 할 일을 쓰고 날짜별로 나눕니다. 2026-10-18)
          new Api(
              "POST",
              "/api/v1/ai/long-plan",
              "/api/v1/ai/long-plan",
              "{\"goal\":\"중간고사\",\"endDate\":\"2099-01-01\"}"),
          // 긴 계획 고치기 (학생의 요청으로 뼈대를 고치고 다시 나눕니다. 2026-10-18)
          new Api(
              "POST",
              "/api/v1/ai/long-plan/revise",
              "/api/v1/ai/long-plan/revise",
              "{\"plan\":{\"goal\":\"중간고사\",\"startDate\":\"2099-01-01\",\"endDate\":\"2099-01-05\","
                  + "\"dailyMinutes\":60,\"startTime\":\"19:00\","
                  + "\"topics\":[{\"title\":\"수학\",\"minutes\":60}]},\"request\":\"줄여 줘\"}"),
          // 코치 알림 문장 (그날 아침 · 밤 알림 문장을 미리 받습니다. 2026-10-18)
          new Api("POST", "/api/v1/ai/nudges", "/api/v1/ai/nudges", "{\"date\":\"2099-01-01\"}"));

  // 토큰 없이 불러도 되는 API 들입니다. 로그인하기 전에 써야 하는 것뿐입니다.
  // 구글로 시작하기 · 구글로 가입하기(이슈 #19)도 로그인하기 전에 부릅니다. 대신 구글 확인 정보가 있어야 합니다.
  private static final List<String> PUBLIC =
      List.of(
          "GET /api/v1/health",
          "GET /api/v1/ping",
          "POST /api/v1/auth/signup",
          "POST /api/v1/auth/login",
          "POST /api/v1/auth/google",
          "POST /api/v1/auth/google/signup",
          // 시연 코드 로그인(2026-10-18). 코드가 맞아야 토큰을 주고, 서버에 코드가 없으면 404 입니다.
          "POST /api/v1/auth/demo-login",
          // 학교 종류 + 학과 목록(2026-10-19). 가입 화면에서 "무슨 과야?" 를 물을 때(계정이 생기기 전) 부릅니다.
          // NEIS 의 공개 자료이고 내 데이터가 없습니다.
          "GET /api/v1/schools/{schoolCode}/majors");

  // 로그인 토큰이 아니라 "작업 키"(X-Job-Key)로 지키는 API 입니다. 사람이 아니라 예약 작업이 부릅니다.
  // 키가 없으면 401 인 것은 JobFlowTest 가 확인합니다.
  private static final List<String> JOB_KEY_REQUIRED = List.of("POST /api/v1/jobs/{name}");

  @Autowired private MockMvc mockMvc;

  // Spring 이 알고 있는 "주소 → Controller 메서드" 표입니다. 서버에 어떤 API 가 있는지 여기서 전부 꺼냅니다.
  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping handlerMapping;

  // ---- 도우미 ----

  // 이 토큰을 Authorization 헤더에 붙이고(null 이면 로그인 안 한 요청), 본문이 있으면 JSON 으로 담아 부릅니다.
  private ResultActions send(MockHttpServletRequestBuilder builder, String token, String body)
      throws Exception {
    if (token != null) {
      builder.header("Authorization", "Bearer " + token);
    }
    if (body != null) {
      builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }
    return mockMvc.perform(builder);
  }

  private ResultActions call(Api api, String token) throws Exception {
    return send(request(HttpMethod.valueOf(api.method()), api.path()), token, api.body());
  }

  private static String responseBody(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  // 새 사용자를 가입시키고 로그인해서 토큰을 돌려줍니다.
  private String signupAndLogin(String nickname) throws Exception {
    String email =
        "login-required-" + UUID.randomUUID().toString().substring(0, 8) + "@starflow.com";
    String signupBody =
        """
        {
          "email": "%s",
          "password": "%s",
          "name": "김티맥",
          "nickname": "%s",
          "schoolCode": null,
          "schoolName": null,
          "consents": { "terms": true, "privacy": true, "diaryAnalysis": false, "over14": true, "version": "v1" }
        }
        """
            .formatted(email, PASSWORD, nickname);
    send(post("/api/v1/auth/signup"), null, signupBody).andExpect(status().isCreated());

    ResultActions login =
        send(
            post("/api/v1/auth/login"),
            null,
            "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD));
    login.andExpect(status().isOk());
    return JsonPath.read(responseBody(login), "$.token");
  }

  // 탈퇴시켜서 그 사람이 만든 데이터를 모두 지웁니다.
  private void withdraw(String token) throws Exception {
    send(post("/api/v1/auth/withdraw"), token, "{\"password\":\"" + PASSWORD + "\"}")
        .andExpect(status().isNoContent());
  }

  // ---- 1. 토큰 없이 부르면 401 ----

  @Test
  @DisplayName("토큰 없이 부르면 로그인이 필요한 API 는 하나도 빠짐없이 401 이다")
  void everyApiRejectsRequestWithoutToken() throws Exception {
    for (Api api : LOGIN_REQUIRED) {
      ResultActions result = call(api, null);

      assertThat(result.andReturn().getResponse().getStatus()).as(api.name()).isEqualTo(401);
      result.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
  }

  @Test
  @DisplayName("모르는 토큰으로 불러도 하나도 빠짐없이 401 이다")
  void everyApiRejectsUnknownToken() throws Exception {
    for (Api api : LOGIN_REQUIRED) {
      ResultActions result = call(api, "this-token-does-not-exist");

      assertThat(result.andReturn().getResponse().getStatus()).as(api.name()).isEqualTo(401);
      result.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
  }

  @Test
  @DisplayName("상태 확인(health)은 토큰 없이도 200 이다")
  void healthIsPublic() throws Exception {
    mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("서버에 있는 모든 API 가 위의 표에 들어 있다 (새 API 를 만들고 표에 안 넣으면 실패)")
  void tableCoversEveryApi() {
    // 서버에 실제로 있는 API 를 "방식 주소" 글자로 모읍니다. 예: "PATCH /api/v1/study-plans/{id}"
    Set<String> actual = new TreeSet<>();
    for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
      for (String pattern : info.getPatternValues()) {
        // Swagger 화면·에러 화면처럼 /api/v1 밖에 있는 주소는 우리가 만든 API 가 아니라서 뺍니다.
        if (!pattern.startsWith("/api/v1")) {
          continue;
        }
        for (RequestMethod method : info.getMethodsCondition().getMethods()) {
          actual.add(method.name() + " " + pattern);
        }
      }
    }

    Set<String> expected = new TreeSet<>();
    for (Api api : LOGIN_REQUIRED) {
      expected.add(api.name());
    }
    expected.addAll(PUBLIC);
    expected.addAll(JOB_KEY_REQUIRED);

    assertThat(actual)
        .as("새 API 를 만들었으면 LOGIN_REQUIRED 표에 한 줄을 더해 주세요. 지운 API 는 표에서도 지워 주세요")
        .isEqualTo(expected);
  }

  // ---- 2. 남의 데이터는 404 ----

  @Test
  @DisplayName("A 의 토큰으로 B 의 계획을 바꾸거나 지우려 하면 404 이고, B 의 계획은 그대로다")
  void otherUsersPlanIsNotFound() throws Exception {
    String tokenA = signupAndLogin("에이에이");
    String tokenB = signupAndLogin("비이비이");
    try {
      // 비이가 과목과 계획을 하나씩 만듭니다.
      ResultActions subject =
          send(post("/api/v1/subjects"), tokenB, "{\"name\":\"수학\",\"color\":\"red\"}");
      subject.andExpect(status().isCreated());
      String subjectId = JsonPath.read(responseBody(subject), "$.id");

      String planBody =
          "{\"date\":\"2026-10-12\",\"subjectId\":\"%s\",\"content\":\"수학 문제집\",\"targetMinutes\":60}"
              .formatted(subjectId);
      ResultActions plan = send(post("/api/v1/study-plans"), tokenB, planBody);
      plan.andExpect(status().isCreated());
      String planId = JsonPath.read(responseBody(plan), "$.id").toString();

      // 에이가 비이의 계획 번호로 부르면 "없는 계획" 과 똑같이 404 입니다.
      send(patch("/api/v1/study-plans/" + planId), tokenA, "{\"done\":true}")
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
      send(delete("/api/v1/study-plans/" + planId), tokenA, null)
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

      // 에이의 그날 목록에는 비이의 계획이 보이지 않습니다.
      send(get("/api/v1/study-plans?date=2026-10-12"), tokenA, null)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(0));

      // 비이의 계획은 지워지지도, 완료로 바뀌지도 않았습니다.
      send(get("/api/v1/study-plans?date=2026-10-12"), tokenB, null)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(1))
          .andExpect(jsonPath("$[0].done").value(false));
    } finally {
      withdraw(tokenA);
      withdraw(tokenB);
    }
  }

  @Test
  @DisplayName("A 의 토큰으로 조회하면 B 의 공부 기록은 기간 목록·오늘 합계·계획별 실제 시간 어디에도 섞이지 않는다")
  void otherUsersSessionsAreNotMixedIn() throws Exception {
    String tokenA = signupAndLogin("에이에이");
    String tokenB = signupAndLogin("비이비이");
    try {
      // 비이가 과목과 계획을 만들고, 그 계획으로 켠 60분짜리 기록을 저장합니다. (한 시간 전에 끝난 기록)
      ResultActions subject =
          send(post("/api/v1/subjects"), tokenB, "{\"name\":\"수학\",\"color\":\"red\"}");
      subject.andExpect(status().isCreated());
      String subjectId = JsonPath.read(responseBody(subject), "$.id");

      String planBody =
          "{\"date\":\"2026-10-12\",\"subjectId\":\"%s\",\"content\":\"수학 문제집\",\"targetMinutes\":60}"
              .formatted(subjectId);
      ResultActions plan = send(post("/api/v1/study-plans"), tokenB, planBody);
      plan.andExpect(status().isCreated());
      String planId = JsonPath.read(responseBody(plan), "$.id").toString();

      Instant end = Instant.now().minusSeconds(3600);
      Instant start = end.minusSeconds(3600);
      String sessionBody =
          "{\"subjectId\":\"%s\",\"startedAt\":%d,\"endedAt\":%d,\"pauseCount\":0,\"planId\":\"%s\"}"
              .formatted(subjectId, start.toEpochMilli(), end.toEpochMilli(), planId);
      send(post("/api/v1/study-sessions"), tokenB, sessionBody).andExpect(status().isCreated());

      // 기간은 어제 ~ 오늘로 잡습니다. 지금이 새벽 4시 근처여도 방금 저장한 기록이 꼭 들어오게 하려는 것입니다.
      LocalDate today = DayRule.dateOf(Instant.now());
      String period = "?from=" + today.minusDays(1) + "&to=" + today;

      // 에이의 기간 목록에는 비이의 기록이 보이지 않습니다.
      send(get("/api/v1/study-sessions" + period), tokenA, null)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(0));
      // 비이의 기간 목록에는 그 기록이 있습니다.
      send(get("/api/v1/study-sessions" + period), tokenB, null)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(1))
          .andExpect(jsonPath("$[0].planId").value(planId));

      // 에이의 오늘 합계는 0분입니다.
      send(get("/api/v1/study-sessions/today/total"), tokenA, null)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.studyMinutes").value(0))
          .andExpect(jsonPath("$.subjects.length()").value(0));

      // 에이에게는 그날 계획이 없어서 빈 배열이고, 비이의 계획은 60분을 채운 것으로 나옵니다.
      send(get("/api/v1/study-sessions/by-plan?date=2026-10-12"), tokenA, null)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(0));
      send(get("/api/v1/study-sessions/by-plan?date=2026-10-12"), tokenB, null)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(1))
          .andExpect(jsonPath("$[0].planId").value(planId))
          .andExpect(jsonPath("$[0].actualMinutes").value(60))
          .andExpect(jsonPath("$[0].isReached").value(true));
    } finally {
      withdraw(tokenA);
      withdraw(tokenB);
    }
  }
}
