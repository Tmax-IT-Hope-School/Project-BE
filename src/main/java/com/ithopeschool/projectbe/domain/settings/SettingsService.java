package com.ithopeschool.projectbe.domain.settings;

import com.ithopeschool.projectbe.domain.settings.dto.SettingsResponse;
import com.ithopeschool.projectbe.domain.settings.dto.SettingsUpdateRequest;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import com.ithopeschool.projectbe.global.time.DayRule;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 관리 설정 보기·바꾸기 로직입니다. (이슈 #22 · #227, docs/API.md "관리 설정")
//
// 화면에서 부르는 메서드는 currentUserProvider.getCurrentUserId() 로 시작합니다. 그래서 로그인하지 않은 요청은 401 이 나가고,
// 내 설정만 보고 바꿉니다. (누구의 설정인지를 요청에서 받지 않습니다)
//
// 아직 한 번도 바꾼 적이 없는 사람은 DB 에 줄이 없고, 처음 값(CareSetting.withDefaults)을 보여 줍니다.
// 처음 바꿀 때 처음 값으로 줄을 만든 뒤 바꿉니다.
//
// 설정은 두 곳에서 바뀝니다. (이슈 #227)
//   1) 사용자가 PATCH  → update()    : 보낸 칸은 source=USER 로 잠깁니다(locked).
//   2) AI 자동 매핑     → applyAuto() : 잠긴 칸과 "사용자만 바꾸는 칸"(말투 수위·제재 한도)은 건너뜁니다.
@Service
@RequiredArgsConstructor
public class SettingsService {

  // 사용자가 직접 정한 칸의 이유 한 줄
  public static final String USER_REASON = "내가 정했어요";

  // AI 가 바꾸지 못하고 사용자만 바꾸는 칸 (기획서 4.8 "제재 최대 한도와 말투 수위는 AI가 바꾸지 않는다")
  public static final Set<String> USER_ONLY_FIELDS = Set.of("maxHarshness", "maxCareLevel");

  private final CurrentUserProvider currentUserProvider;
  private final CareSettingRepository careSettingRepository;
  private final FocusLockAppRepository focusLockAppRepository;
  private final SettingChangeRepository settingChangeRepository;
  private final SettingSourceRepository settingSourceRepository;
  // "지금" 과 "오늘" 을 알 때 씁니다. 테스트에서 시각을 고정할 수 있습니다. (global/config/ClockConfig)
  private final Clock clock;

  // 내 관리 설정. 한 번도 바꾼 적이 없으면 처음 값입니다. (이때 DB 에 저장하지는 않습니다)
  @Transactional(readOnly = true)
  public SettingsResponse getSettings() {
    Long userId = currentUserProvider.getCurrentUserId();
    CareSetting setting =
        careSettingRepository
            .findByUserId(userId)
            .orElseGet(() -> CareSetting.withDefaults(userId));
    return responseOf(userId, setting, today());
  }

  // 관리 설정 바꾸기 (사용자): 보낸 칸만 바꾸고, 실제로 값이 달라진 칸마다 "바꾼 기록" 을 한 줄씩 남깁니다.
  // 보낸 칸은 값이 같아도 "내가 정함" 으로 잠급니다. (사용자가 이 값으로 하겠다고 확인한 것이라서)
  @Transactional
  public SettingsResponse update(SettingsUpdateRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    if (request.isEmpty()) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "바꿀 값을 보내 주세요");
    }
    // 바꾸기 전에 먼저 검사합니다. 여기서 에러가 나면 아무것도 바뀌지 않습니다.
    if (request.lockApps() != null) {
      checkNoDuplicateApp(request.lockApps());
    }

    Instant now = clock.instant();
    LocalDate today = DayRule.dateOf(now);
    CareSetting setting = findOrCreate(userId, today);

    Changes changes = new Changes(userId, now, ChangeSource.USER);
    applyValues(setting, request, Set.of(), changes, today);
    saveChanges(changes, USER_REASON, true);
    // 엔티티의 값을 바꾸기만 하면 이 메서드가 끝날 때 JPA 가 DB 에 반영합니다.
    return responseOf(userId, setting, today);
  }

  // AI 자동 매핑(이슈 #233)이 설정을 바꿀 때 부르는 입구입니다. API 로 열려 있지 않습니다.
  // 예약 작업(14일 뒤 데이터 기준)에서도 부르므로, 누구의 설정인지를 userId 로 받습니다. (QuietHoursSettings 와 같음)
  //
  // - 잠긴 칸(사용자가 정한 칸)은 건너뜁니다. AI 는 다시 바꾸지 않습니다.
  // - 말투 수위·제재 한도는 AI 가 바꾸지 않으므로 항상 건너뜁니다.
  // - 바꾼 칸에는 source(TEST/DATA)와 reason 이 적히고, 잠기지 않습니다.
  // 실제로 적용한 칸 이름들을 돌려줍니다. (건너뛴 칸은 들어 있지 않습니다)
  @Transactional
  public List<String> applyAuto(
      Long userId, SettingsUpdateRequest values, ChangeSource source, String reason) {
    if (source == ChangeSource.USER) {
      throw new IllegalArgumentException("자동 매핑은 TEST 나 DATA 로만 바꿉니다");
    }
    if (reason == null || reason.isBlank() || reason.length() > SettingSource.MAX_REASON_LENGTH) {
      throw new IllegalArgumentException("이유는 1~100자로 적어 주세요");
    }
    if (values.lockApps() != null) {
      checkNoDuplicateApp(values.lockApps());
    }

    Instant now = clock.instant();
    LocalDate today = DayRule.dateOf(now);
    CareSetting setting = findOrCreate(userId, today);

    Set<String> skip = new HashSet<>(USER_ONLY_FIELDS);
    for (SettingSource saved : settingSourceRepository.findByUserId(userId)) {
      if (saved.isLocked()) {
        skip.add(saved.getField());
      }
    }

    Changes changes = new Changes(userId, now, source);
    applyValues(setting, values, skip, changes, today);
    saveChanges(changes, reason, false);
    return changes.touched;
  }

  // 보낸 칸(null 이 아닌 칸)을 하나씩 바꿉니다. skip 에 든 칸은 보냈어도 건드리지 않습니다.
  private void applyValues(
      CareSetting setting,
      SettingsUpdateRequest values,
      Set<String> skip,
      Changes changes,
      LocalDate today) {
    if (values.callTime() != null && !skip.contains("callTime")) {
      LocalTime callTime = LocalTime.parse(values.callTime());
      changes.add("callTime", format(setting.getCallTime()), format(callTime));
      setting.changeCallTime(callTime);
    }
    if (values.dailyCount() != null && !skip.contains("dailyCount")) {
      changes.add("dailyCount", setting.getDailyMessageCount(), values.dailyCount());
      setting.changeDailyMessageCount(values.dailyCount());
    }
    if (values.quietStart() != null && !skip.contains("quietStart")) {
      LocalTime quietStart = LocalTime.parse(values.quietStart());
      changes.add("quietStart", format(setting.getQuietStart()), format(quietStart));
      setting.changeQuietStart(quietStart);
    }
    if (values.quietEnd() != null && !skip.contains("quietEnd")) {
      LocalTime quietEnd = LocalTime.parse(values.quietEnd());
      changes.add("quietEnd", format(setting.getQuietEnd()), format(quietEnd));
      setting.changeQuietEnd(quietEnd);
    }
    if (values.maxHarshness() != null && !skip.contains("maxHarshness")) {
      // 이전 값은 "사용자가 마지막으로 고른 수위" 입니다. (올리고 아직 적용 전이면 그 올린 값)
      changes.add("maxHarshness", setting.requestedMaxHarshness(), values.maxHarshness());
      // 낮추면 바로, 높이면 다음 날부터 적용됩니다. (CareSetting.changeMaxHarshness)
      setting.changeMaxHarshness(values.maxHarshness(), today);
    }
    if (values.allowFocusMode() != null && !skip.contains("allowFocusMode")) {
      changes.add("allowFocusMode", setting.isAllowAppLock(), values.allowFocusMode());
      setting.changeAllowAppLock(values.allowFocusMode());
    }
    if (values.maxCareLevel() != null && !skip.contains("maxCareLevel")) {
      // 이전 값은 "사용자가 마지막으로 고른 한도" 입니다. (올리고 아직 적용 전이면 그 올린 값)
      changes.add("maxCareLevel", setting.requestedMaxCareLevel(), values.maxCareLevel());
      // 낮추면 바로, 높이면 다음 날부터 적용됩니다. (CareSetting.changeMaxCareLevel)
      setting.changeMaxCareLevel(values.maxCareLevel(), today);
    }
    if (values.lockApps() != null && !skip.contains("lockApps")) {
      Long userId = setting.getUserId();
      List<String> before = lockAppNamesOf(userId);
      changes.add("lockApps", String.join(", ", before), String.join(", ", values.lockApps()));
      if (!before.equals(values.lockApps())) {
        replaceLockApps(userId, values.lockApps());
      }
    }
  }

  // "바꾼 기록" 을 저장하고, 손댄 칸마다 근거(누가·왜·잠금)를 적습니다. 이미 근거 줄이 있는 칸은 그 줄을 고칩니다.
  private void saveChanges(Changes changes, String reason, boolean locked) {
    settingChangeRepository.saveAll(changes.logs);

    Map<String, SettingSource> saved = new HashMap<>();
    for (SettingSource source : settingSourceRepository.findByUserId(changes.userId)) {
      saved.put(source.getField(), source);
    }
    for (String field : changes.touched) {
      SettingSource source = saved.get(field);
      if (source == null) {
        settingSourceRepository.save(
            new SettingSource(changes.userId, field, changes.source, reason, locked));
      } else {
        source.change(changes.source, reason, locked);
      }
    }
  }

  // 내 설정 줄을 찾고, 없으면 처음 값으로 만듭니다.
  // 적용 날짜가 된 "바뀔 한도 · 바뀔 수위" 를 먼저 지금 값으로 옮겨 둡니다. (그래야 이전 값이 정확합니다)
  private CareSetting findOrCreate(Long userId, LocalDate today) {
    CareSetting setting =
        careSettingRepository
            .findByUserId(userId)
            .orElseGet(() -> careSettingRepository.save(CareSetting.withDefaults(userId)));
    setting.applyDuePending(today);
    return setting;
  }

  private SettingsResponse responseOf(Long userId, CareSetting setting, LocalDate today) {
    return SettingsResponse.of(
        setting, lockAppNamesOf(userId), settingSourceRepository.findByUserId(userId), today);
  }

  // 잠금 대상 앱 목록을 통째로 바꿉니다. (모두 지우고, 보낸 순서대로 새로 넣습니다)
  private void replaceLockApps(Long userId, List<String> appNames) {
    focusLockAppRepository.deleteAllOfUser(userId);
    List<FocusLockApp> apps = new ArrayList<>();
    for (String appName : appNames) {
      apps.add(new FocusLockApp(userId, appName));
    }
    focusLockAppRepository.saveAll(apps);
  }

  // 같은 앱을 두 번 보내면 400 입니다. (DB 의 "같은 앱은 한 번만" 규칙에 걸리기 전에 알려 줍니다)
  private void checkNoDuplicateApp(List<String> appNames) {
    Set<String> seen = new HashSet<>();
    for (String appName : appNames) {
      if (!seen.add(appName)) {
        throw new ApiException(ErrorCode.VALIDATION_ERROR, "같은 앱이 두 번 들어 있어요");
      }
    }
  }

  private List<String> lockAppNamesOf(Long userId) {
    return focusLockAppRepository.findByUserIdOrderById(userId).stream()
        .map(FocusLockApp::getAppName)
        .toList();
  }

  private LocalDate today() {
    return DayRule.dateOf(clock.instant());
  }

  private static String format(LocalTime time) {
    return SettingsResponse.format(time);
  }

  // 한 번의 요청(또는 자동 매핑 한 번)에서 손댄 칸과 "바꾼 기록" 을 모읍니다.
  // - touched: 손댄 칸 이름 전부 (값이 같아도 들어감 → 근거를 적을 칸)
  // - logs: 값이 실제로 달라진 칸만 (같은 값을 보내면 기록 안 함)
  private static class Changes {
    private final Long userId;
    private final Instant changedAt;
    private final ChangeSource source;
    private final List<String> touched = new ArrayList<>();
    private final List<SettingChange> logs = new ArrayList<>();

    Changes(Long userId, Instant changedAt, ChangeSource source) {
      this.userId = userId;
      this.changedAt = changedAt;
      this.source = source;
    }

    void add(String field, Object oldValue, Object newValue) {
      touched.add(field);
      String oldText = String.valueOf(oldValue);
      String newText = String.valueOf(newValue);
      if (!oldText.equals(newText)) {
        logs.add(new SettingChange(userId, changedAt, field, oldText, newText, source));
      }
    }
  }
}
