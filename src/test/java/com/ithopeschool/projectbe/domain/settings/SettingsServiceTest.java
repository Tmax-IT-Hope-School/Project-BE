package com.ithopeschool.projectbe.domain.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ithopeschool.projectbe.domain.settings.dto.SettingsResponse;
import com.ithopeschool.projectbe.domain.settings.dto.SettingsUpdateRequest;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

// 관리 설정 로직의 규칙을 하나씩 확인합니다. DB 는 가짜(Repository 를 흉내 낸 것)로 바꾸고 "어떻게 물었는지" 를 봅니다.
class SettingsServiceTest {

  private static final Long ME = 1L;
  // 지금: 2026-10-17 낮 12시(한국 시간) → 오늘은 10/17
  private static final Instant NOW = Instant.parse("2026-10-17T03:00:00Z");
  private static final LocalDate OCT_17 = LocalDate.of(2026, 10, 17);
  private static final LocalDate OCT_18 = LocalDate.of(2026, 10, 18);

  private CurrentUserProvider currentUserProvider;
  private CareSettingRepository careSettingRepository;
  private FocusLockAppRepository focusLockAppRepository;
  private SettingChangeRepository settingChangeRepository;
  private SettingSourceRepository settingSourceRepository;
  private SettingsService settingsService;

  @BeforeEach
  void setUp() {
    currentUserProvider = mock(CurrentUserProvider.class);
    careSettingRepository = mock(CareSettingRepository.class);
    focusLockAppRepository = mock(FocusLockAppRepository.class);
    settingChangeRepository = mock(SettingChangeRepository.class);
    settingSourceRepository = mock(SettingSourceRepository.class);
    settingsService =
        new SettingsService(
            currentUserProvider,
            careSettingRepository,
            focusLockAppRepository,
            settingChangeRepository,
            settingSourceRepository,
            Clock.fixed(NOW, ZoneOffset.UTC));
    given(currentUserProvider.getCurrentUserId()).willReturn(ME);
    given(careSettingRepository.findByUserId(any())).willReturn(Optional.empty());
    given(careSettingRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
    given(focusLockAppRepository.findByUserIdOrderById(any())).willReturn(List.of());
  }

  // 이미 DB 에 있는 내 설정 (처음 값)
  private CareSetting givenMySetting() {
    CareSetting setting = CareSetting.withDefaults(ME);
    given(careSettingRepository.findByUserId(ME)).willReturn(Optional.of(setting));
    return setting;
  }

  private static SettingsUpdateRequest request(
      String callTime,
      Integer dailyCount,
      String quietStart,
      String quietEnd,
      Integer maxHarshness,
      Boolean allowFocusMode,
      Integer maxCareLevel,
      List<String> lockApps) {
    return new SettingsUpdateRequest(
        callTime,
        dailyCount,
        quietStart,
        quietEnd,
        maxHarshness,
        allowFocusMode,
        maxCareLevel,
        lockApps);
  }

  private static SettingsUpdateRequest harshness(int value) {
    return request(null, null, null, null, value, null, null, null);
  }

  private static SettingsUpdateRequest careLevel(int value) {
    return request(null, null, null, null, null, null, value, null);
  }

  private static SettingsUpdateRequest lockApps(String... names) {
    return request(null, null, null, null, null, null, null, List.of(names));
  }

  // 이번 요청에서 저장한 "바꾼 기록"
  @SuppressWarnings("unchecked")
  private List<SettingChange> savedChanges() {
    ArgumentCaptor<List<SettingChange>> captor = ArgumentCaptor.forClass(List.class);
    verify(settingChangeRepository).saveAll(captor.capture());
    return captor.getValue();
  }

  private static void assertError(Runnable action, ErrorCode code, String message) {
    assertThatThrownBy(action::run)
        .isInstanceOf(ApiException.class)
        .hasMessage(message)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(code);
  }

  // ---- 로그인 ----

  @Test
  @DisplayName("로그인하지 않았으면 두 가지 모두 401 이고, DB 는 건드리지 않는다")
  void everythingRequiresLogin() {
    given(currentUserProvider.getCurrentUserId())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    assertError(() -> settingsService.getSettings(), ErrorCode.UNAUTHORIZED, "로그인이 필요해요");
    assertError(() -> settingsService.update(harshness(5)), ErrorCode.UNAUTHORIZED, "로그인이 필요해요");
    verifyNoInteractions(
        careSettingRepository,
        focusLockAppRepository,
        settingChangeRepository,
        settingSourceRepository);
  }

  // ---- 보기 ----

  @Test
  @DisplayName("한 번도 바꾼 적이 없으면 처음 값을 보여 주고, DB 에 저장하지는 않는다")
  void getDefaults() {
    SettingsResponse response = settingsService.getSettings();

    // 기본값: 말투 수위 2, 제재 한도 3 (이슈 #227)
    assertThat(response)
        .extracting(
            SettingsResponse::callTime,
            SettingsResponse::dailyCount,
            SettingsResponse::quietStart,
            SettingsResponse::quietEnd,
            SettingsResponse::maxHarshness,
            SettingsResponse::allowFocusMode,
            SettingsResponse::maxCareLevel,
            SettingsResponse::pendingMaxCareLevel,
            SettingsResponse::pendingMaxHarshness,
            SettingsResponse::lockApps)
        .containsExactly("21:00", 3, "23:00", "07:00", 2, false, 3, null, null, List.of());
    // 아무도 정하지 않은 칸은 근거가 비어 있고 잠기지 않았다
    assertThat(response.sources()).containsOnlyKeys(SettingsResponse.FIELDS);
    assertThat(response.sources().values())
        .allSatisfy(
            info -> {
              assertThat(info.source()).isNull();
              assertThat(info.reason()).isNull();
              assertThat(info.locked()).isFalse();
            });
    verify(careSettingRepository).findByUserId(ME);
    verify(careSettingRepository, never()).save(any());
  }

  @Test
  @DisplayName("내 설정과 잠금 대상 앱을 내 번호로 찾아 보여 준다")
  void getMySettings() {
    CareSetting setting = givenMySetting();
    setting.changeMaxHarshness(1, OCT_17);
    given(focusLockAppRepository.findByUserIdOrderById(ME))
        .willReturn(List.of(new FocusLockApp(ME, "유튜브")));

    SettingsResponse response = settingsService.getSettings();

    assertThat(response.maxHarshness()).isEqualTo(1);
    assertThat(response.lockApps()).containsExactly("유튜브");
    verify(focusLockAppRepository).findByUserIdOrderById(ME);
  }

  @Test
  @DisplayName("오늘 올린 한도는 보기에서 '바뀔 한도' 로 보이고, 지금 한도는 그대로다")
  void getShowsPendingRaise() {
    CareSetting setting = givenMySetting();
    setting.changeMaxCareLevel(4, OCT_17);

    SettingsResponse response = settingsService.getSettings();

    assertThat(response.maxCareLevel()).isEqualTo(3);
    assertThat(response.pendingMaxCareLevel()).isEqualTo(4);
    assertThat(response.pendingEffectiveDate()).isEqualTo(OCT_18);
  }

  @Test
  @DisplayName("어제 올린 한도는 오늘 보기에서 지금 한도로 보이고, 바뀔 한도는 없다")
  void getShowsAppliedRaise() {
    CareSetting setting = givenMySetting();
    setting.changeMaxCareLevel(4, OCT_17.minusDays(1));

    SettingsResponse response = settingsService.getSettings();

    assertThat(response.maxCareLevel()).isEqualTo(4);
    assertThat(response.pendingMaxCareLevel()).isNull();
    assertThat(response.pendingEffectiveDate()).isNull();
  }

  // ---- 바꾸기 ----

  @Test
  @DisplayName("아무 칸도 안 보내면 400 이고, DB 는 건드리지 않는다")
  void updateNothing() {
    assertError(
        () -> settingsService.update(request(null, null, null, null, null, null, null, null)),
        ErrorCode.VALIDATION_ERROR,
        "바꿀 값을 보내 주세요");
    verifyNoInteractions(
        careSettingRepository,
        focusLockAppRepository,
        settingChangeRepository,
        settingSourceRepository);
  }

  @Test
  @DisplayName("처음 바꾸면 처음 값으로 설정 줄을 만든 뒤 바꾼다")
  void firstUpdateCreatesRow() {
    SettingsResponse response = settingsService.update(harshness(1));

    ArgumentCaptor<CareSetting> saved = ArgumentCaptor.forClass(CareSetting.class);
    verify(careSettingRepository).save(saved.capture());
    assertThat(saved.getValue().getUserId()).isEqualTo(ME);
    assertThat(saved.getValue().getMaxHarshness()).isEqualTo(1);
    assertThat(response.maxHarshness()).isEqualTo(1);
  }

  @Test
  @DisplayName("이미 설정이 있으면 새로 만들지 않고 그 줄을 바꾼다")
  void updateExistingRow() {
    CareSetting setting = givenMySetting();

    settingsService.update(harshness(1));

    verify(careSettingRepository, never()).save(any());
    assertThat(setting.getMaxHarshness()).isEqualTo(1);
  }

  @Test
  @DisplayName("보낸 칸만 바뀌고, 칸마다 '바꾼 기록' 이 한 줄씩 남는다 (언제 · 무엇을 · 전 값 · 새 값)")
  void updateRecordsEachChange() {
    CareSetting setting = givenMySetting();

    settingsService.update(request("20:30", 0, "22:00", "06:30", 1, true, null, null));

    assertThat(setting.getCallTime()).isEqualTo(LocalTime.of(20, 30));
    assertThat(setting.getDailyMessageCount()).isEqualTo(0);
    assertThat(setting.getQuietStart()).isEqualTo(LocalTime.of(22, 0));
    assertThat(setting.getQuietEnd()).isEqualTo(LocalTime.of(6, 30));
    assertThat(setting.getMaxHarshness()).isEqualTo(1);
    assertThat(setting.isAllowAppLock()).isTrue();
    // 보내지 않은 한도는 그대로
    assertThat(setting.getMaxCareLevel()).isEqualTo(3);

    assertThat(savedChanges())
        .extracting(
            SettingChange::getUserId,
            SettingChange::getChangedAt,
            SettingChange::getField,
            SettingChange::getOldValue,
            SettingChange::getNewValue)
        .containsExactly(
            tuple(ME, NOW, "callTime", "21:00", "20:30"),
            tuple(ME, NOW, "dailyCount", "3", "0"),
            tuple(ME, NOW, "quietStart", "23:00", "22:00"),
            tuple(ME, NOW, "quietEnd", "07:00", "06:30"),
            tuple(ME, NOW, "maxHarshness", "2", "1"),
            tuple(ME, NOW, "allowFocusMode", "false", "true"));
  }

  @Test
  @DisplayName("지금과 같은 값을 보내면 바뀐 게 없어서 기록을 남기지 않는다")
  void sameValueIsNotRecorded() {
    givenMySetting();

    settingsService.update(request("21:00", 3, null, null, 2, false, 3, null));

    assertThat(savedChanges()).isEmpty();
  }

  @Test
  @DisplayName("한도를 높이면 오늘은 그대로 3 이고 내일(10/18)부터 4 다. 기록에는 3 → 4 가 남는다")
  void raiseCareLevel() {
    CareSetting setting = givenMySetting();

    SettingsResponse response = settingsService.update(careLevel(4));

    assertThat(response.maxCareLevel()).isEqualTo(3);
    assertThat(response.pendingMaxCareLevel()).isEqualTo(4);
    assertThat(response.pendingEffectiveDate()).isEqualTo(OCT_18);
    assertThat(setting.maxCareLevelOn(OCT_18)).isEqualTo(4);
    assertThat(savedChanges())
        .extracting(SettingChange::getField, SettingChange::getOldValue, SettingChange::getNewValue)
        .containsExactly(tuple("maxCareLevel", "3", "4"));
  }

  @Test
  @DisplayName("한도를 낮추면 바로 적용된다")
  void lowerCareLevel() {
    givenMySetting();

    SettingsResponse response = settingsService.update(careLevel(0));

    assertThat(response.maxCareLevel()).isEqualTo(0);
    assertThat(response.pendingMaxCareLevel()).isNull();
  }

  @Test
  @DisplayName("말투 수위를 높이면 오늘은 그대로 2 이고 내일(10/18)부터 4 다. 기록에는 2 → 4 가 남는다")
  void raiseHarshness() {
    CareSetting setting = givenMySetting();

    SettingsResponse response = settingsService.update(harshness(4));

    assertThat(response.maxHarshness()).isEqualTo(2);
    assertThat(response.pendingMaxHarshness()).isEqualTo(4);
    assertThat(response.pendingHarshnessEffectiveDate()).isEqualTo(OCT_18);
    assertThat(setting.maxHarshnessOn(OCT_17)).isEqualTo(2);
    assertThat(setting.maxHarshnessOn(OCT_18)).isEqualTo(4);
    assertThat(savedChanges())
        .extracting(SettingChange::getField, SettingChange::getOldValue, SettingChange::getNewValue)
        .containsExactly(tuple("maxHarshness", "2", "4"));
  }

  @Test
  @DisplayName("말투 수위를 낮추면 바로 적용된다")
  void lowerHarshness() {
    givenMySetting();

    SettingsResponse response = settingsService.update(harshness(1));

    assertThat(response.maxHarshness()).isEqualTo(1);
    assertThat(response.pendingMaxHarshness()).isNull();
    assertThat(response.pendingHarshnessEffectiveDate()).isNull();
  }

  @Test
  @DisplayName("어제 올린 말투 수위는 오늘 보기에서 지금 수위로 보인다")
  void getShowsAppliedHarshnessRaise() {
    CareSetting setting = givenMySetting();
    setting.changeMaxHarshness(5, OCT_17.minusDays(1));

    SettingsResponse response = settingsService.getSettings();

    assertThat(response.maxHarshness()).isEqualTo(5);
    assertThat(response.pendingMaxHarshness()).isNull();
  }

  @Test
  @DisplayName("올려 두고 적용 전에 다시 바꾸면, 기록의 이전 값은 '올려 둔 값' 이다 (4 → 1)")
  void changeWhilePending() {
    CareSetting setting = givenMySetting();
    setting.changeMaxCareLevel(4, OCT_17);

    settingsService.update(careLevel(1));

    assertThat(setting.maxCareLevelOn(OCT_17)).isEqualTo(1);
    assertThat(setting.getPendingMaxCareLevel()).isNull();
    assertThat(savedChanges())
        .extracting(SettingChange::getField, SettingChange::getOldValue, SettingChange::getNewValue)
        .containsExactly(tuple("maxCareLevel", "4", "1"));
  }

  @Test
  @DisplayName("잠금 대상 앱은 통째로 바꾼다: 내 것을 모두 지우고 보낸 순서대로 넣고, 기록을 남긴다")
  void replaceLockApps() {
    givenMySetting();
    given(focusLockAppRepository.findByUserIdOrderById(ME))
        .willReturn(List.of(new FocusLockApp(ME, "게임")));

    settingsService.update(lockApps("유튜브", "인스타그램"));

    verify(focusLockAppRepository).deleteAllOfUser(ME);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<FocusLockApp>> saved = ArgumentCaptor.forClass(List.class);
    verify(focusLockAppRepository).saveAll(saved.capture());
    assertThat(saved.getValue())
        .extracting(FocusLockApp::getUserId, FocusLockApp::getAppName)
        .containsExactly(tuple(ME, "유튜브"), tuple(ME, "인스타그램"));
    assertThat(savedChanges())
        .extracting(SettingChange::getField, SettingChange::getOldValue, SettingChange::getNewValue)
        .containsExactly(tuple("lockApps", "게임", "유튜브, 인스타그램"));
  }

  @Test
  @DisplayName("잠금 대상 앱 목록이 지금과 같으면 지우거나 넣지 않고, 기록도 남기지 않는다")
  void sameLockAppsAreNotTouched() {
    givenMySetting();
    given(focusLockAppRepository.findByUserIdOrderById(ME))
        .willReturn(List.of(new FocusLockApp(ME, "유튜브")));

    settingsService.update(lockApps("유튜브"));

    verify(focusLockAppRepository, never()).deleteAllOfUser(any());
    verify(focusLockAppRepository, never()).saveAll(anyList());
    assertThat(savedChanges()).isEmpty();
  }

  @Test
  @DisplayName("같은 앱을 두 번 보내면 400 이고, 아무것도 바꾸거나 기록하지 않는다")
  void duplicateLockApp() {
    CareSetting setting = givenMySetting();

    assertError(
        () ->
            settingsService.update(
                request(null, null, null, null, 5, null, null, List.of("유튜브", "유튜브"))),
        ErrorCode.VALIDATION_ERROR,
        "같은 앱이 두 번 들어 있어요");
    assertThat(setting.getMaxHarshness()).isEqualTo(2);
    verify(focusLockAppRepository, never()).deleteAllOfUser(any());
    verifyNoInteractions(settingChangeRepository);
  }
}
