package com.ithopeschool.projectbe.domain.weeklypick;

import com.ithopeschool.projectbe.domain.weeklypick.dto.WeeklyPickResponse;
import com.ithopeschool.projectbe.domain.weeklypick.dto.WeeklyPickSaveRequest;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// "이번 주에 고른 것" 의 로직입니다. 저장(한 주에 하나, 다시 보내면 덮어씀)과 기간 목록. (이슈 #406)
//
// 정해 둔 규칙
//   1. 한 주는 월요일 ~ 일요일이고, weekStart 는 그 주의 월요일입니다. 월요일이 아니면 400 입니다.
//   2. 지난 주도 저장할 수 있습니다. 이 기기(localStorage)에 먼저 저장해 둔 것을 나중에 올릴 수 있어야 하기 때문입니다.
//   3. 목록은 양 끝 날짜를 포함해 366일(1년)까지 한 번에 봅니다.
//
// currentUserProvider.getCurrentUserId() 로 시작합니다. 로그인하지 않은 요청은 401 이고, 내 번호로만 읽고 씁니다.
@Service
@RequiredArgsConstructor
public class WeeklyPickService {

  // 목록(list)이 받아 주는 가장 긴 기간(일)입니다. 시작일과 끝일을 모두 센 날 수입니다.
  static final int MAX_RANGE_DAYS = 366;

  private final CurrentUserProvider currentUserProvider;
  private final WeeklyPickRepository weeklyPickRepository;
  // 저장한 시각을 알 때 씁니다. 테스트에서 시각을 고정할 수 있습니다. (global/config/ClockConfig)
  private final Clock clock;

  // 그 주에 고른 것을 저장합니다. 같은 주에 이미 있으면 새로 고른 것으로 덮어쓰고, 없으면 새로 만듭니다. (규칙 1 · 2)
  @Transactional
  public WeeklyPickResponse save(LocalDate weekStart, WeeklyPickSaveRequest request) {
    // 로그인 확인이 먼저입니다. (토큰이 없으면 날짜를 보기 전에 401)
    Long userId = currentUserProvider.getCurrentUserId();
    if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "weekStart 는 그 주의 월요일 날짜로 보내 주세요");
    }

    Instant now = clock.instant();
    // 고른 시각을 안 보냈으면 지금(저장하는 시각)으로 둡니다.
    Instant decidedAt =
        request.decidedAt() == null ? now : Instant.ofEpochMilli(request.decidedAt());

    Optional<WeeklyPick> found = weeklyPickRepository.findByUserIdAndWeekStart(userId, weekStart);
    WeeklyPick pick;
    if (found.isPresent()) {
      // @Transactional 이라 바뀐 값은 끝날 때 DB 에 저장됩니다.
      pick = found.get();
      pick.change(request.title(), request.hint(), request.skip(), decidedAt, now);
    } else {
      pick =
          weeklyPickRepository.save(
              new WeeklyPick(
                  userId,
                  weekStart,
                  request.title(),
                  request.hint(),
                  request.skip(),
                  decidedAt,
                  now));
    }
    return WeeklyPickResponse.from(pick);
  }

  // 내 from ~ to (둘 다 포함) 사이에 시작한 주에 고른 것들, 오래된 주부터. 없으면 빈 배열입니다. (규칙 3)
  @Transactional(readOnly = true)
  public List<WeeklyPickResponse> list(LocalDate from, LocalDate to) {
    Long userId = currentUserProvider.getCurrentUserId();
    if (from.isAfter(to)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "시작 날짜는 끝 날짜보다 늦을 수 없어요");
    }
    // 시작일과 끝일을 모두 세어야 해서 1 을 더합니다. (같은 날이면 1일)
    if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, "한 번에 " + MAX_RANGE_DAYS + "일까지만 조회할 수 있어요");
    }
    return weeklyPickRepository
        .findByUserIdAndWeekStartBetweenOrderByWeekStartAsc(userId, from, to)
        .stream()
        .map(WeeklyPickResponse::from)
        .toList();
  }
}
