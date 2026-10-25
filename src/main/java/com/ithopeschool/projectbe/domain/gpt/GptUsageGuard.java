package com.ithopeschool.projectbe.domain.gpt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// GPT 호출 기록 (이슈 #33). 부른 것을 gpt_usages 테이블에 남겨, 실제로 얼마나 썼는지 볼 수 있게 합니다.
//
// 2026-10-24 부터 한 사람의 하루 한도(통화 횟수 · 통화 시간 · 글 호출 횟수 · 작은 AI 기능 횟수)는 없습니다.
// ("요금은 여유로운데 개인이 막힌다" → 한도를 모두 없앰) 그래서 이 클래스는 막지 않고 기록만 합니다.
// 다시 막아야 하면 git 기록에서 이 파일의 예전 모양(tryUseText · allowedCallSeconds)을 찾아 되살리면 됩니다.
//
// 남은 설정 (application.yml 의 app.ai)
//   통화 한 번 최대 길이   AI_CALL_MAX_SECONDS   (기본 300초 = 5분. 처음 값은 180초였고 #434 에서 늘림. 하루 한도가 아니라 통화 한 번의
// 길이입니다)
//
// 비용은 "대략" 입니다. 요금은 달러라서 대략 환산한 원 값을 설정으로 받습니다. 요금이 바뀌면 설정만 고치면 됩니다.
@Component
public class GptUsageGuard {

  // OpenAI 가 음성 통화(Realtime) 하나를 끊는 최대 길이(초). 60분입니다. (docs/DEPLOY.md)
  static final int OPENAI_MAX_SESSION_SECONDS = 60 * 60;

  private final GptUsageRepository gptUsageRepository;
  private final Clock clock;
  private final int maxCallSeconds;
  private final int costWonPerCallMinute;
  private final int costWonPerTextCall;

  public GptUsageGuard(
      GptUsageRepository gptUsageRepository,
      Clock clock,
      @Value("${app.ai.call-max-seconds:300}") int maxCallSeconds,
      @Value("${app.ai.cost-won-per-call-minute:80}") int costWonPerCallMinute,
      @Value("${app.ai.cost-won-per-text-call:1}") int costWonPerTextCall) {
    this.gptUsageRepository = gptUsageRepository;
    this.clock = clock;
    this.maxCallSeconds = maxCallSeconds;
    this.costWonPerCallMinute = costWonPerCallMinute;
    this.costWonPerTextCall = costWonPerTextCall;
  }

  // 통화 한 번을 최대 몇 초까지 할 수 있는지. FE 는 이 시간이 되면 통화를 끊습니다. (기본 300초)
  public int maxCallSeconds() {
    return maxCallSeconds;
  }

  // 통화를 시작했다고 기록합니다. (통화 시간과 비용은 끝날 때 채웁니다)
  @Transactional
  public void recordCallStart(Long userId, Long callRecordId) {
    gptUsageRepository.save(GptUsage.callStarted(userId, clock.instant(), callRecordId));
  }

  // 통화가 끝났을 때 통화 시간과 비용을 채웁니다. 이미 채웠으면(마무리를 다시 부름) 그대로 둡니다.
  // 통화 시간은 시작 ~ 끝입니다.
  //   ⚠️ 통화 최대 길이(AI_CALL_MAX_SECONDS)로 자르지 않습니다. 통화를 끊는 것은 FE 라서, 브라우저를 고쳐 쓰면 더 길게 통화할 수 있습니다.
  //      그런 통화를 최대 길이로 줄여 적으면 실제 비용이 숨습니다. (docs/DEPLOY.md)
  //      대신 OpenAI 가 통화를 끊는 최대 길이(60분)로만 자릅니다. 마무리를 몇 시간 뒤에 불러도 그 이상은 통화했을 수 없어서입니다.
  // 비용은 분당 비용 × 통화한 분이고, 원 단위로 올림합니다.
  @Transactional
  public void recordCallFinish(
      Long userId, Long callRecordId, Instant startedAt, Instant finishedAt) {
    gptUsageRepository
        .findByCallRecordIdAndUserId(callRecordId, userId)
        .filter(usage -> !usage.isCallFinished())
        .ifPresent(
            usage -> {
              long seconds = Duration.between(startedAt, finishedAt).toSeconds();
              int callSeconds = (int) Math.max(0, Math.min(OPENAI_MAX_SESSION_SECONDS, seconds));
              int costWon = (int) Math.ceil(callSeconds * costWonPerCallMinute / 60.0);
              usage.finishCall(callSeconds, costWon);
            });
  }

  // 글을 받는 GPT(통화 요약 · 코치 · 계획 초안 · 상담 채팅 · 작은 AI 기능)를 한 번 불렀다고 기록합니다.
  // 막지 않습니다. GPT 를 부르기 바로 전에 부릅니다.
  @Transactional
  public void recordText(Long userId, GptUsageKind kind) {
    if (kind == GptUsageKind.CALL) {
      throw new IllegalArgumentException("통화는 recordCallStart 를 씁니다");
    }
    gptUsageRepository.save(GptUsage.textCall(userId, kind, clock.instant(), costWonPerTextCall));
  }
}
