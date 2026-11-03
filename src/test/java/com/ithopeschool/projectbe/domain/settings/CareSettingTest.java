package com.ithopeschool.projectbe.domain.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 관리 설정 한 줄의 규칙을 확인합니다. 특히 "제재 한도·말투 수위는 낮추면 즉시, 높이면 다음 날부터" (이슈 #22 · #227 완료 조건)
class CareSettingTest {

  private static final LocalDate OCT_17 = LocalDate.of(2026, 10, 17);
  private static final LocalDate OCT_18 = LocalDate.of(2026, 10, 18);
  private static final LocalDate OCT_19 = LocalDate.of(2026, 10, 19);

  @Test
  @DisplayName("처음 값은 FE 와 같다: 전화 21:00 · 메시지 3 · 연락 금지 23:00~07:00 · 독설 2 · 잠금 안 함 · 한도 3 (#227)")
  void defaults() {
    CareSetting setting = CareSetting.withDefaults(1L);

    assertThat(setting.getUserId()).isEqualTo(1L);
    assertThat(setting.getCallTime()).isEqualTo(LocalTime.of(21, 0));
    assertThat(setting.getDailyMessageCount()).isEqualTo(3);
    assertThat(setting.getQuietStart()).isEqualTo(LocalTime.of(23, 0));
    assertThat(setting.getQuietEnd()).isEqualTo(LocalTime.of(7, 0));
    assertThat(setting.getMaxHarshness()).isEqualTo(2);
    assertThat(setting.isAllowAppLock()).isFalse();
    assertThat(setting.getMaxCareLevel()).isEqualTo(3);
    assertThat(setting.getPendingMaxCareLevel()).isNull();
    assertThat(setting.getPendingEffectiveDate()).isNull();
  }

  @Test
  @DisplayName("한도를 높이면 오늘은 그대로이고, 다음 날부터 높인 한도가 적용된다")
  void raisingAppliesFromTomorrow() {
    CareSetting setting = CareSetting.withDefaults(1L);

    setting.changeMaxCareLevel(4, OCT_17);

    assertThat(setting.maxCareLevelOn(OCT_17)).isEqualTo(3);
    assertThat(setting.hasPendingOn(OCT_17)).isTrue();
    assertThat(setting.getPendingMaxCareLevel()).isEqualTo(4);
    assertThat(setting.getPendingEffectiveDate()).isEqualTo(OCT_18);
    // 다음 날, 그 다음 날에는 4
    assertThat(setting.maxCareLevelOn(OCT_18)).isEqualTo(4);
    assertThat(setting.maxCareLevelOn(OCT_19)).isEqualTo(4);
    assertThat(setting.hasPendingOn(OCT_18)).isFalse();
  }

  @Test
  @DisplayName("한도를 낮추면 오늘 바로 적용된다")
  void loweringAppliesNow() {
    CareSetting setting = CareSetting.withDefaults(1L);

    setting.changeMaxCareLevel(0, OCT_17);

    assertThat(setting.maxCareLevelOn(OCT_17)).isEqualTo(0);
    assertThat(setting.getMaxCareLevel()).isEqualTo(0);
    assertThat(setting.hasPendingOn(OCT_17)).isFalse();
  }

  @Test
  @DisplayName("올려 둔 한도가 적용되기 전에 낮추면, 올리기는 취소되고 낮춘 값이 바로 적용된다")
  void loweringCancelsPendingRaise() {
    CareSetting setting = CareSetting.withDefaults(1L);
    setting.changeMaxCareLevel(4, OCT_17);

    setting.changeMaxCareLevel(1, OCT_17);

    assertThat(setting.maxCareLevelOn(OCT_17)).isEqualTo(1);
    assertThat(setting.maxCareLevelOn(OCT_18)).isEqualTo(1);
    assertThat(setting.getPendingMaxCareLevel()).isNull();
    assertThat(setting.getPendingEffectiveDate()).isNull();
  }

  @Test
  @DisplayName("올려 둔 한도가 적용되기 전에 지금 한도와 같은 값을 고르면, 올리기만 취소된다")
  void sameValueCancelsPendingRaise() {
    CareSetting setting = CareSetting.withDefaults(1L);
    setting.changeMaxCareLevel(4, OCT_17);

    setting.changeMaxCareLevel(3, OCT_17);

    assertThat(setting.maxCareLevelOn(OCT_18)).isEqualTo(3);
    assertThat(setting.getPendingMaxCareLevel()).isNull();
  }

  @Test
  @DisplayName("다음 날에 다시 올리면, 어제 올린 값이 먼저 적용된 뒤 또 다음 날부터 적용된다")
  void raisingAgainNextDay() {
    CareSetting setting = CareSetting.withDefaults(1L);
    setting.changeMaxCareLevel(2, OCT_17.minusDays(1));
    setting.changeMaxCareLevel(3, OCT_17);

    setting.changeMaxCareLevel(4, OCT_18);

    assertThat(setting.getMaxCareLevel()).isEqualTo(3);
    assertThat(setting.maxCareLevelOn(OCT_18)).isEqualTo(3);
    assertThat(setting.getPendingEffectiveDate()).isEqualTo(OCT_19);
    assertThat(setting.maxCareLevelOn(OCT_19)).isEqualTo(4);
  }

  @Test
  @DisplayName("사용자가 마지막으로 고른 한도는, 올려 둔 값이 있으면 그 값이다")
  void requestedMaxCareLevel() {
    CareSetting setting = CareSetting.withDefaults(1L);
    assertThat(setting.requestedMaxCareLevel()).isEqualTo(3);

    setting.changeMaxCareLevel(4, OCT_17);

    assertThat(setting.requestedMaxCareLevel()).isEqualTo(4);
  }

  @Test
  @DisplayName("적용 날짜가 된 바뀔 한도는 applyDuePending 으로 지금 한도로 옮겨진다. 날짜 전에는 그대로")
  void applyDuePending() {
    CareSetting setting = CareSetting.withDefaults(1L);
    setting.changeMaxCareLevel(4, OCT_17);

    setting.applyDuePending(OCT_17);
    assertThat(setting.getMaxCareLevel()).isEqualTo(3);
    assertThat(setting.getPendingMaxCareLevel()).isEqualTo(4);

    setting.applyDuePending(OCT_18);
    assertThat(setting.getMaxCareLevel()).isEqualTo(4);
    assertThat(setting.getPendingMaxCareLevel()).isNull();
    assertThat(setting.getPendingEffectiveDate()).isNull();
  }

  // ---- 말투 수위 (이슈 #227: 한도와 같은 규칙) ----

  @Test
  @DisplayName("말투 수위를 높이면 오늘은 그대로이고, 다음 날부터 높인 수위가 적용된다")
  void raisingHarshnessAppliesFromTomorrow() {
    CareSetting setting = CareSetting.withDefaults(1L);

    setting.changeMaxHarshness(4, OCT_17);

    assertThat(setting.maxHarshnessOn(OCT_17)).isEqualTo(2);
    assertThat(setting.hasPendingHarshnessOn(OCT_17)).isTrue();
    assertThat(setting.getPendingHarshnessEffectiveDate()).isEqualTo(OCT_18);
    assertThat(setting.maxHarshnessOn(OCT_18)).isEqualTo(4);
    assertThat(setting.hasPendingHarshnessOn(OCT_18)).isFalse();
    assertThat(setting.requestedMaxHarshness()).isEqualTo(4);
  }

  @Test
  @DisplayName("말투 수위를 낮추면 바로 적용되고, 올려 둔 수위는 취소된다")
  void loweringHarshnessAppliesNow() {
    CareSetting setting = CareSetting.withDefaults(1L);
    setting.changeMaxHarshness(4, OCT_17);

    setting.changeMaxHarshness(1, OCT_17);

    assertThat(setting.maxHarshnessOn(OCT_17)).isEqualTo(1);
    assertThat(setting.maxHarshnessOn(OCT_18)).isEqualTo(1);
    assertThat(setting.getPendingMaxHarshness()).isNull();
  }

  @Test
  @DisplayName("한도와 수위를 다른 날 올려도 적용 날짜가 섞이지 않는다")
  void careLevelAndHarshnessHaveOwnDates() {
    CareSetting setting = CareSetting.withDefaults(1L);
    setting.changeMaxCareLevel(4, OCT_17);
    setting.changeMaxHarshness(4, OCT_18);

    assertThat(setting.getPendingHarshnessEffectiveDate()).isEqualTo(OCT_19);
    assertThat(setting.maxCareLevelOn(OCT_18)).isEqualTo(4);
    assertThat(setting.maxHarshnessOn(OCT_18)).isEqualTo(2);
    assertThat(setting.maxHarshnessOn(OCT_19)).isEqualTo(4);
  }
}
