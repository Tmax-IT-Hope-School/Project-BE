package com.ithopeschool.projectbe.domain.weeklypick;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.weeklypick.dto.WeeklyPickResponse;
import com.ithopeschool.projectbe.domain.weeklypick.dto.WeeklyPickSaveRequest;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

// Service 단위 테스트: DB(Repository)를 가짜로 두고, 저장 · 덮어쓰기 · 날짜 규칙만 확인합니다.
class WeeklyPickServiceTest {

  private static final Long USER_ID = 7L;
  private static final LocalDate MONDAY = LocalDate.of(2026, 10, 26);
  private static final Instant NOW = Instant.parse("2026-10-24T11:00:00Z");
  private static final Instant DECIDED = Instant.parse("2026-10-24T10:30:00Z");

  private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
  private final WeeklyPickRepository weeklyPickRepository = mock(WeeklyPickRepository.class);
  private final WeeklyPickService service =
      new WeeklyPickService(
          currentUserProvider, weeklyPickRepository, Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void setUp() {
    given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
    // 저장하면 DB 가 번호를 붙여 주는 것처럼 5번을 붙입니다.
    given(weeklyPickRepository.save(any()))
        .willAnswer(
            invocation -> {
              WeeklyPick pick = invocation.getArgument(0);
              ReflectionTestUtils.setField(pick, "id", 5L);
              return pick;
            });
  }

  private static WeeklyPickSaveRequest request(String title, boolean skip, Long decidedAt) {
    return new WeeklyPickSaveRequest(title, "손이 안 닿으면 덜 멈춰요", skip, decidedAt);
  }

  private static WeeklyPick existing(LocalDate weekStart, String title, Instant createdAt) {
    WeeklyPick pick =
        new WeeklyPick(USER_ID, weekStart, title, "처음 설명", false, createdAt, createdAt);
    ReflectionTestUtils.setField(pick, "id", 3L);
    return pick;
  }

  @Test
  @DisplayName("그 주에 고른 것이 없으면 내 번호로 새 줄을 만들고, 보낸 고른 시각을 그대로 남긴다")
  void createsNewPick() {
    given(weeklyPickRepository.findByUserIdAndWeekStart(USER_ID, MONDAY))
        .willReturn(Optional.empty());

    WeeklyPickResponse response =
        service.save(MONDAY, request("폰을 다른 방에 두기", false, DECIDED.toEpochMilli()));

    ArgumentCaptor<WeeklyPick> captor = ArgumentCaptor.forClass(WeeklyPick.class);
    verify(weeklyPickRepository).save(captor.capture());
    WeeklyPick saved = captor.getValue();
    assertThat(saved.getUserId()).isEqualTo(USER_ID);
    assertThat(saved.getWeekStart()).isEqualTo(MONDAY);
    assertThat(saved.getTitle()).isEqualTo("폰을 다른 방에 두기");
    assertThat(saved.getHint()).isEqualTo("손이 안 닿으면 덜 멈춰요");
    assertThat(saved.isSkip()).isFalse();
    assertThat(saved.getDecidedAt()).isEqualTo(DECIDED);
    assertThat(saved.getCreatedAt()).isEqualTo(NOW);
    assertThat(saved.getUpdatedAt()).isEqualTo(NOW);

    assertThat(response)
        .isEqualTo(
            new WeeklyPickResponse(
                "5", MONDAY, "폰을 다른 방에 두기", "손이 안 닿으면 덜 멈춰요", false, DECIDED.toEpochMilli()));
  }

  @Test
  @DisplayName("고른 시각을 안 보내면 저장하는 지금 시각으로 둔다")
  void decidedAtDefaultsToNow() {
    given(weeklyPickRepository.findByUserIdAndWeekStart(USER_ID, MONDAY))
        .willReturn(Optional.empty());

    WeeklyPickResponse response = service.save(MONDAY, request("이번 주는 넘어가기", true, null));

    assertThat(response.decidedAt()).isEqualTo(NOW.toEpochMilli());
    assertThat(response.skip()).isTrue();
  }

  @Test
  @DisplayName("같은 주에 이미 고른 것이 있으면 새 줄을 만들지 않고 그 줄을 덮어쓴다 (처음 만든 시각은 그대로)")
  void overwritesSameWeek() {
    Instant firstTime = Instant.parse("2026-10-20T09:00:00Z");
    WeeklyPick found = existing(MONDAY, "처음 고른 것", firstTime);
    given(weeklyPickRepository.findByUserIdAndWeekStart(USER_ID, MONDAY))
        .willReturn(Optional.of(found));

    WeeklyPickResponse response =
        service.save(MONDAY, request("카드 1장만 하기", true, DECIDED.toEpochMilli()));

    verify(weeklyPickRepository, never()).save(any());
    assertThat(found.getTitle()).isEqualTo("카드 1장만 하기");
    assertThat(found.getHint()).isEqualTo("손이 안 닿으면 덜 멈춰요");
    assertThat(found.isSkip()).isTrue();
    assertThat(found.getDecidedAt()).isEqualTo(DECIDED);
    assertThat(found.getCreatedAt()).isEqualTo(firstTime);
    assertThat(found.getUpdatedAt()).isEqualTo(NOW);
    assertThat(response.id()).isEqualTo("3");
    assertThat(response.title()).isEqualTo("카드 1장만 하기");
  }

  @Test
  @DisplayName("지난 주도 저장할 수 있다 (이 기기에 먼저 저장해 둔 것을 나중에 올릴 수 있게)")
  void pastWeekCanBeSaved() {
    LocalDate longAgo = MONDAY.minusWeeks(10);
    given(weeklyPickRepository.findByUserIdAndWeekStart(USER_ID, longAgo))
        .willReturn(Optional.empty());

    WeeklyPickResponse response = service.save(longAgo, request("폰 뒤집어 두기", false, null));

    assertThat(response.weekStart()).isEqualTo(longAgo);
  }

  @Test
  @DisplayName("weekStart 가 월요일이 아니면 400 이고 DB 는 건드리지 않는다")
  void rejectsNotMonday() {
    assertThatThrownBy(() -> service.save(MONDAY.plusDays(1), request("해 볼 것", false, null)))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
              assertThat(e.getMessage()).isEqualTo("weekStart 는 그 주의 월요일 날짜로 보내 주세요");
            });
    verifyNoInteractions(weeklyPickRepository);
  }

  @Test
  @DisplayName("로그인 확인이 날짜 확인보다 먼저다 (토큰이 없으면 월요일이 아니어도 401)")
  void loginCheckComesFirst() {
    given(currentUserProvider.getCurrentUserId())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    assertThatThrownBy(() -> service.save(MONDAY.plusDays(1), request("해 볼 것", false, null)))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    assertThatThrownBy(() -> service.list(MONDAY, MONDAY.minusDays(1)))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    verifyNoInteractions(weeklyPickRepository);
  }

  @Test
  @DisplayName("목록은 내 번호로 찾은 순서(오래된 주부터) 그대로 돌려준다")
  void listsMyPicks() {
    LocalDate from = LocalDate.of(2026, 10, 1);
    LocalDate to = LocalDate.of(2026, 10, 31);
    WeeklyPick first = existing(LocalDate.of(2026, 10, 12), "앞 주", NOW);
    WeeklyPick second = existing(LocalDate.of(2026, 10, 19), "뒷 주", NOW);
    ReflectionTestUtils.setField(second, "id", 4L);
    given(
            weeklyPickRepository.findByUserIdAndWeekStartBetweenOrderByWeekStartAsc(
                USER_ID, from, to))
        .willReturn(List.of(first, second));

    List<WeeklyPickResponse> found = service.list(from, to);

    assertThat(found).extracting(WeeklyPickResponse::id).containsExactly("3", "4");
    assertThat(found).extracting(WeeklyPickResponse::title).containsExactly("앞 주", "뒷 주");
  }

  @Test
  @DisplayName("시작이 끝보다 늦으면 400")
  void rejectsReversedRange() {
    assertThatThrownBy(() -> service.list(MONDAY, MONDAY.minusDays(1)))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
              assertThat(e.getMessage()).isEqualTo("시작 날짜는 끝 날짜보다 늦을 수 없어요");
            });
    verifyNoInteractions(weeklyPickRepository);
  }

  @Test
  @DisplayName("양 끝을 포함해 366일까지는 되고, 367일이면 400")
  void rangeLimitIs366Days() {
    LocalDate from = LocalDate.of(2025, 10, 27);
    // from 부터 365일 뒤 = 양 끝 포함 366일
    LocalDate lastAllowed = from.plusDays(365);
    given(
            weeklyPickRepository.findByUserIdAndWeekStartBetweenOrderByWeekStartAsc(
                USER_ID, from, lastAllowed))
        .willReturn(List.of());

    assertThat(service.list(from, lastAllowed)).isEmpty();
    assertThatThrownBy(() -> service.list(from, lastAllowed.plusDays(1)))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
              assertThat(e.getMessage()).isEqualTo("한 번에 366일까지만 조회할 수 있어요");
            });
  }
}
