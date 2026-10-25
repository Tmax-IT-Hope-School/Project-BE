package com.ithopeschool.projectbe.domain.variant;

import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// 내 컴퓨터(local)에서만, 그리고 app.variant.seed-on-start=true 일 때만:
// 서버가 켜질 때 시드 원본 30문항(VariantSeedData)을 테스트 사용자(1번)에게 등록해 둡니다. (VAR-03)
// 그러면 오답 17문항의 학습 항목이 생겨서, 다음 날부터 일일 출제 흐름을 Swagger 로 볼 수 있습니다.
//
// 켜는 법: 환경변수 VARIANT_SEED_ON_START=true 로 서버를 켭니다. (기본은 꺼짐 — 다른 사람의 로컬 데이터를 바꾸지 않게)
// 로컬 DB(H2)는 켤 때마다 비어 있어서 켤 때마다 한 번 등록됩니다.
// 코드(단원 · 유형)를 같이 보내서 AI 없이도 SUPPORTED 가 됩니다. (로컬은 AI 가 꺼져 있음)
@Component
@Profile("local")
@ConditionalOnProperty(name = "app.variant.seed-on-start", havingValue = "true")
@RequiredArgsConstructor
public class VariantSeedInitializer implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(VariantSeedInitializer.class);

  private final SourceProblemService sourceProblemService;

  @Override
  public void run(ApplicationArguments args) {
    int count = 0;
    for (VariantSeedData.Problem problem : VariantSeedData.load()) {
      sourceProblemService.registerFor(
          CurrentUserProvider.TEST_USER_ID, problem.toRequest(true), SourceProblemOrigin.SEED);
      count++;
    }
    log.info("[변형 시드] 테스트 사용자에게 원본 문항 {}개를 등록했어요", count);
  }
}
