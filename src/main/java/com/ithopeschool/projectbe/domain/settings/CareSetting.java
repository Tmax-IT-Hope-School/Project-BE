package com.ithopeschool.projectbe.domain.settings;

import com.ithopeschool.projectbe.domain.safety.QuietHours;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 관리 설정 테이블. 한 줄 = 사용자 한 명의 설정. (이슈 #22)
// 사용자 한 명에 한 줄뿐이라 user_id 가 겹칠 수 없습니다. (unique)
//
// 제재 한도(maxCareLevel)는 낮추면 바로, 높이면 다음 날부터 적용됩니다. (기획서 v2 5장 「원칙」)
//   오늘 10/17 에 2 → 4 로 올리면:  maxCareLevel 2, pendingMaxCareLevel 4, pendingEffectiveDate 10/18
//   10/18 이 되면 maxCareLevelOn(10/18) 이 4 를 돌려줍니다.
// 말투 수위(maxHarshness)도 똑같습니다. 바뀔 수위는 pendingMaxHarshness · pendingHarshnessEffectiveDate 에 적습니다.
// (이슈 #227)
// 한도와 수위는 날짜 칸을 따로 둡니다. 둘을 다른 날 올려도 서로의 적용 날짜가 섞이지 않게 하려고요.
//
// 값의 범위는 Service 와 요청 검사에서 먼저 막고, 혹시 빠뜨려도 DB 가 한 번 더 막도록 검사 규칙(check)도 걸어 두었습니다.
// (제재 한도 0 ~ 4 는 CareLevel.MIN_LEVEL · MAX_LEVEL 과 같습니다. 그쪽 범위가 바뀌면 아래 check 도 고쳐 주세요)
// 잠금 대상 앱은 여러 개라서 focus_lock_apps 테이블에 따로 적습니다.
@Entity
@Table(
    name = "care_settings",
    check = {
      @CheckConstraint(
          name = "ck_care_settings_daily_message_count",
          constraint = "daily_message_count between 0 and 5"),
      @CheckConstraint(
          name = "ck_care_settings_max_harshness",
          constraint = "max_harshness between 1 and 5"),
      @CheckConstraint(
          name = "ck_care_settings_max_care_level",
          constraint = "max_care_level between 0 and 4"),
      @CheckConstraint(
          name = "ck_care_settings_pending_max_care_level",
          constraint = "pending_max_care_level is null or pending_max_care_level between 0 and 4"),
      @CheckConstraint(
          name = "ck_care_settings_pending_max_harshness",
          constraint = "pending_max_harshness is null or pending_max_harshness between 1 and 5")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CareSetting {

  // 처음 값(아직 아무것도 정하지 않은 사람). FE 의 defaultMessageSettings · defaultLimits 와 같은 값입니다.
  // 바꿀 때는 FE(src/lib/messageSettings.ts · careLimits.ts)도 같이 바꿔야 화면과 서버가 어긋나지 않습니다.
  public static final LocalTime DEFAULT_CALL_TIME = LocalTime.of(21, 0);
  public static final int DEFAULT_DAILY_MESSAGE_COUNT = 3;
  // 제재 한도 3, 말투 수위 2 (기획서 6.3 표의 기본값, 이슈 #227 에서 2/3 → 3/2 로 바꿈)
  public static final int DEFAULT_MAX_HARSHNESS = 2;
  public static final int DEFAULT_MAX_CARE_LEVEL = 3;
  public static final boolean DEFAULT_ALLOW_APP_LOCK = false;

  // 값의 범위
  public static final int MIN_DAILY_MESSAGE_COUNT = 0;
  public static final int MAX_DAILY_MESSAGE_COUNT = 5;
  public static final int MIN_HARSHNESS = 1;
  public static final int MAX_HARSHNESS = 5;

  // 설정 한 줄을 구분하는 고유 번호. DB 가 1, 2, 3 ... 순서로 만들어 줍니다.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 이 설정의 주인(users.id). 사용자 한 명에 한 줄뿐입니다.
  @Column(nullable = false, unique = true)
  private Long userId;

  // AI 가 전화하는 시각. 예: 21:00 (몇 시 몇 분만)
  @Column(nullable = false)
  private LocalTime callTime;

  // 하루에 받을 AI 메시지 수 (0 ~ 5). 0 이면 메시지를 받지 않습니다.
  @Column(nullable = false)
  private int dailyMessageCount;

  // 연락 금지 시작 시각. 예: 23:00
  @Column(nullable = false)
  private LocalTime quietStart;

  // 연락 금지 끝 시각. 예: 07:00 (시작보다 이르면 다음 날 그 시각까지)
  @Column(nullable = false)
  private LocalTime quietEnd;

  // 허용하는 독설 수위 (1 ~ 5)
  @Column(nullable = false)
  private int maxHarshness;

  // 앱 잠금(집중 모드)을 허용하는지
  @Column(nullable = false)
  private boolean allowAppLock;

  // 지금 적용 중인 제재 한도 (관리 강도 0 ~ 4 중 어디까지)
  @Column(nullable = false)
  private int maxCareLevel;

  // 한도를 올렸는데 아직 적용 전이면, 바뀔 한도. 없으면 비어 있습니다.
  private Integer pendingMaxCareLevel;

  // 바뀔 한도가 적용되는 날짜. 없으면 비어 있습니다.
  private LocalDate pendingEffectiveDate;

  // 말투 수위를 올렸는데 아직 적용 전이면, 바뀔 수위. 없으면 비어 있습니다.
  private Integer pendingMaxHarshness;

  // 바뀔 수위가 적용되는 날짜. 없으면 비어 있습니다.
  private LocalDate pendingHarshnessEffectiveDate;

  public CareSetting(
      Long userId,
      LocalTime callTime,
      int dailyMessageCount,
      LocalTime quietStart,
      LocalTime quietEnd,
      int maxHarshness,
      boolean allowAppLock,
      int maxCareLevel) {
    this.userId = userId;
    this.callTime = callTime;
    this.dailyMessageCount = dailyMessageCount;
    this.quietStart = quietStart;
    this.quietEnd = quietEnd;
    this.maxHarshness = maxHarshness;
    this.allowAppLock = allowAppLock;
    this.maxCareLevel = maxCareLevel;
  }

  // 아직 아무것도 정하지 않은 사람의 설정 (처음 값)
  public static CareSetting withDefaults(Long userId) {
    return new CareSetting(
        userId,
        DEFAULT_CALL_TIME,
        DEFAULT_DAILY_MESSAGE_COUNT,
        QuietHours.DEFAULT_START,
        QuietHours.DEFAULT_END,
        DEFAULT_MAX_HARSHNESS,
        DEFAULT_ALLOW_APP_LOCK,
        DEFAULT_MAX_CARE_LEVEL);
  }

  // 그날(today) 적용되는 제재 한도입니다. 당근·채찍(#62)은 이 값을 넘지 않습니다.
  //   한도 2, 바뀔 한도 4 (10/18 부터)  →  10/17 은 2, 10/18 은 4
  public int maxCareLevelOn(LocalDate today) {
    return isPendingDue(today) ? pendingMaxCareLevel : maxCareLevel;
  }

  // 그날(today) 기준으로 아직 적용 전인 바뀔 한도가 있으면 true 입니다.
  public boolean hasPendingOn(LocalDate today) {
    return pendingMaxCareLevel != null && !isPendingDue(today);
  }

  // 사용자가 마지막으로 고른 한도입니다. (바뀔 한도가 있으면 그것, 없으면 지금 한도)
  // "바꾼 기록" 에 이전 값으로 적을 때 씁니다.
  public int requestedMaxCareLevel() {
    return pendingMaxCareLevel != null ? pendingMaxCareLevel : maxCareLevel;
  }

  // 적용 날짜가 된 바뀔 한도 · 바뀔 수위를 지금 값으로 옮깁니다.
  public void applyDuePending(LocalDate today) {
    if (isPendingDue(today)) {
      maxCareLevel = pendingMaxCareLevel;
      clearPending();
    }
    if (isHarshnessPendingDue(today)) {
      maxHarshness = pendingMaxHarshness;
      clearPendingHarshness();
    }
  }

  // 제재 한도를 바꿉니다.
  // - 낮추거나 지금과 같으면: 바로 적용하고, 기다리던 바뀔 한도는 지웁니다. (올리기 취소)
  // - 높이면: 바로 바꾸지 않고, 다음 날부터 적용되게 적어 둡니다.
  public void changeMaxCareLevel(int requested, LocalDate today) {
    applyDuePending(today);
    if (requested <= maxCareLevel) {
      maxCareLevel = requested;
      clearPending();
    } else {
      pendingMaxCareLevel = requested;
      pendingEffectiveDate = today.plusDays(1);
    }
  }

  public void changeCallTime(LocalTime callTime) {
    this.callTime = callTime;
  }

  public void changeDailyMessageCount(int dailyMessageCount) {
    this.dailyMessageCount = dailyMessageCount;
  }

  public void changeQuietStart(LocalTime quietStart) {
    this.quietStart = quietStart;
  }

  public void changeQuietEnd(LocalTime quietEnd) {
    this.quietEnd = quietEnd;
  }

  // 그날(today) 적용되는 말투 수위입니다.
  public int maxHarshnessOn(LocalDate today) {
    return isHarshnessPendingDue(today) ? pendingMaxHarshness : maxHarshness;
  }

  // 그날(today) 기준으로 아직 적용 전인 바뀔 수위가 있으면 true 입니다.
  public boolean hasPendingHarshnessOn(LocalDate today) {
    return pendingMaxHarshness != null && !isHarshnessPendingDue(today);
  }

  // 사용자가 마지막으로 고른 수위입니다. (바뀔 수위가 있으면 그것, 없으면 지금 수위)
  public int requestedMaxHarshness() {
    return pendingMaxHarshness != null ? pendingMaxHarshness : maxHarshness;
  }

  // 말투 수위를 바꿉니다. 제재 한도(changeMaxCareLevel)와 같은 규칙입니다.
  // - 낮추거나 지금과 같으면: 바로 적용하고, 기다리던 바뀔 수위는 지웁니다.
  // - 높이면: 바로 바꾸지 않고, 다음 날부터 적용되게 적어 둡니다.
  public void changeMaxHarshness(int requested, LocalDate today) {
    applyDuePending(today);
    if (requested <= maxHarshness) {
      maxHarshness = requested;
      clearPendingHarshness();
    } else {
      pendingMaxHarshness = requested;
      pendingHarshnessEffectiveDate = today.plusDays(1);
    }
  }

  public void changeAllowAppLock(boolean allowAppLock) {
    this.allowAppLock = allowAppLock;
  }

  // 바뀔 한도가 있고, 적용 날짜가 오늘이거나 지났으면 true
  private boolean isPendingDue(LocalDate today) {
    return pendingMaxCareLevel != null && !today.isBefore(pendingEffectiveDate);
  }

  private void clearPending() {
    pendingMaxCareLevel = null;
    pendingEffectiveDate = null;
  }

  // 바뀔 수위가 있고, 적용 날짜가 오늘이거나 지났으면 true
  private boolean isHarshnessPendingDue(LocalDate today) {
    return pendingMaxHarshness != null && !today.isBefore(pendingHarshnessEffectiveDate);
  }

  private void clearPendingHarshness() {
    pendingMaxHarshness = null;
    pendingHarshnessEffectiveDate = null;
  }
}
