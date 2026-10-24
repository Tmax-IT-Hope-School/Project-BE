package com.ithopeschool.projectbe.domain.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ithopeschool.projectbe.domain.settings.dto.SettingsUpdateRequest;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

// 설정 근거(누가·왜·잠금) 규칙을 확인합니다. (이슈 #227)
//   - 사용자가 바꾸면 source=USER, 잠김
//   - AI 자동 매핑은 잠긴 칸과 말투 수위·제재 한도를 바꾸지 않음
//   - 바꾼 기록(setting_changes)에 누가 바꿨는지 남음
class SettingsSourceTest {

  private static final Long ME = 1L;
  private static final Instant NOW = Instant.parse("2026-10-17T03:00:00Z");
  private static final String TEST_REASON = "시작 저항이 높게 나와서 바꿨어요";

  private CurrentUserProvider currentUserProvider;
  private CareSettingRepository careSettingRepository;
  private SettingChangeRepository settingChangeRepository;
  private SettingSourceRepository settingSourceRepository;
  private SettingsService settingsService;
  private CareSetting mySetting;

  @BeforeEach
  void setUp() {
    currentUserProvider = mock(CurrentUserProvider.class);
    careSettingRepository = mock(CareSettingRepository.class);
    settingChangeRepository = mock(SettingChangeRepository.class);
    settingSourceRepository = mock(SettingSourceRepository.class);
    settingsService =
        new SettingsService(
            currentUserProvider,
            careSettingRepository,
            mock(FocusLockAppRepository.class),
            settingChangeRepository,
            settingSourceRepository,
            Clock.fixed(NOW, ZoneOffset.UTC));
    given(currentUserProvider.getCurrentUserId()).willReturn(ME);
    mySetting = CareSetting.withDefaults(ME);
    given(careSettingRepository.findByUserId(ME)).willReturn(Optional.of(mySetting));
  }

  // 이미 DB 에 있는 근거 줄들
  private void givenSources(SettingSource... sources) {
    given(settingSourceRepository.findByUserId(ME)).willReturn(List.of(sources));
  }

  private static SettingsUpdateRequest request(
      String callTime, Integer dailyCount, Integer maxHarshness, Integer maxCareLevel) {
    return new SettingsUpdateRequest(
        callTime, dailyCount, null, null, maxHarshness, null, maxCareLevel, null);
  }

  // 이번에 새로 만든 근거 줄
  private List<SettingSource> savedSources() {
    ArgumentCaptor<SettingSource> captor = ArgumentCaptor.forClass(SettingSource.class);
    verify(settingSourceRepository, atLeast(0)).save(captor.capture());
    return captor.getAllValues();
  }

  // 이번에 저장한 "바꾼 기록"
  @SuppressWarnings("unchecked")
  private List<SettingChange> savedChanges() {
    ArgumentCaptor<List<SettingChange>> captor = ArgumentCaptor.forClass(List.class);
    verify(settingChangeRepository).saveAll(captor.capture());
    return captor.getValue();
  }

  // ---- 사용자가 바꿀 때 ----

  @Test
  @DisplayName("사용자가 바꾼 칸은 source=USER · 이유 '내가 정했어요' · 잠김 으로 적힌다")
  void userUpdateLocks() {
    settingsService.update(request(null, 0, null, null));

    assertThat(savedSources())
        .extracting(
            SettingSource::getUserId,
            SettingSource::getField,
            SettingSource::getSource,
            SettingSource::getReason,
            SettingSource::isLocked)
        .containsExactly(tuple(ME, "dailyCount", ChangeSource.USER, "내가 정했어요", true));
  }

  @Test
  @DisplayName("지금과 같은 값을 보내도 사용자가 확인한 것이라 잠긴다. 바꾼 기록은 남지 않는다")
  void sameValueStillLocks() {
    settingsService.update(request("21:00", null, null, null));

    assertThat(savedSources())
        .extracting(SettingSource::getField, SettingSource::isLocked)
        .containsExactly(tuple("callTime", true));
    assertThat(savedChanges()).isEmpty();
  }

  @Test
  @DisplayName("AI 가 정했던 칸을 사용자가 바꾸면, 그 근거 줄이 USER · 잠김 으로 고쳐진다")
  void userOverridesAiSource() {
    SettingSource fromTest =
        new SettingSource(ME, "dailyCount", ChangeSource.TEST, TEST_REASON, false);
    givenSources(fromTest);

    settingsService.update(request(null, 1, null, null));

    assertThat(fromTest.getSource()).isEqualTo(ChangeSource.USER);
    assertThat(fromTest.getReason()).isEqualTo(SettingsService.USER_REASON);
    assertThat(fromTest.isLocked()).isTrue();
    verify(settingSourceRepository, never()).save(any());
  }

  @Test
  @DisplayName("사용자가 바꾼 기록에는 누가 바꿨는지 USER 가 남는다")
  void userChangeRecordsSource() {
    settingsService.update(request(null, 0, null, null));

    assertThat(savedChanges())
        .extracting(SettingChange::getField, SettingChange::getSource)
        .containsExactly(tuple("dailyCount", ChangeSource.USER));
  }

  // ---- AI 자동 매핑 ----

  @Test
  @DisplayName("잠긴 칸은 자동 매핑이 바꾸지 않는다. 잠기지 않은 칸만 바꾸고 바꾼 칸 이름을 돌려준다")
  void autoSkipsLockedField() {
    givenSources(new SettingSource(ME, "dailyCount", ChangeSource.USER, "내가 정했어요", true));

    List<String> applied =
        settingsService.applyAuto(
            ME, request("20:00", 5, null, null), ChangeSource.TEST, TEST_REASON);

    assertThat(applied).containsExactly("callTime");
    assertThat(mySetting.getDailyMessageCount()).isEqualTo(3);
    assertThat(mySetting.getCallTime()).isEqualTo(LocalTime.of(20, 0));
  }

  @Test
  @DisplayName("자동 매핑이 바꾼 칸은 source=TEST · 받은 이유 · 잠기지 않음 으로 적히고, 기록에도 TEST 가 남는다")
  void autoRecordsSource() {
    settingsService.applyAuto(
        ME, request("20:00", null, null, null), ChangeSource.TEST, TEST_REASON);

    assertThat(savedSources())
        .extracting(
            SettingSource::getField,
            SettingSource::getSource,
            SettingSource::getReason,
            SettingSource::isLocked)
        .containsExactly(tuple("callTime", ChangeSource.TEST, TEST_REASON, false));
    assertThat(savedChanges())
        .extracting(
            SettingChange::getField,
            SettingChange::getOldValue,
            SettingChange::getNewValue,
            SettingChange::getSource)
        .containsExactly(tuple("callTime", "21:00", "20:00", ChangeSource.TEST));
  }

  @Test
  @DisplayName("2주 뒤 데이터(DATA)는 검사(TEST)로 정한 잠기지 않은 칸을 다시 바꿀 수 있다")
  void dataOverridesTest() {
    SettingSource fromTest =
        new SettingSource(ME, "callTime", ChangeSource.TEST, TEST_REASON, false);
    givenSources(fromTest);

    List<String> applied =
        settingsService.applyAuto(
            ME, request("19:30", null, null, null), ChangeSource.DATA, "최근 통화를 일찍 받아서 바꿨어요");

    assertThat(applied).containsExactly("callTime");
    assertThat(fromTest.getSource()).isEqualTo(ChangeSource.DATA);
    assertThat(fromTest.isLocked()).isFalse();
  }

  @Test
  @DisplayName("말투 수위·제재 한도는 잠기지 않았어도 자동 매핑이 바꾸지 않는다")
  void autoNeverChangesUserOnlyFields() {
    List<String> applied =
        settingsService.applyAuto(ME, request(null, null, 1, 0), ChangeSource.TEST, TEST_REASON);

    assertThat(applied).isEmpty();
    assertThat(mySetting.getMaxHarshness()).isEqualTo(CareSetting.DEFAULT_MAX_HARSHNESS);
    assertThat(mySetting.getMaxCareLevel()).isEqualTo(CareSetting.DEFAULT_MAX_CARE_LEVEL);
    assertThat(savedChanges()).isEmpty();
  }

  @Test
  @DisplayName("자동 매핑이 USER 로 바꾸려 하거나 이유가 비어 있으면 거절한다")
  void autoRejectsBadInput() {
    assertThatThrownBy(
            () ->
                settingsService.applyAuto(
                    ME, request("20:00", null, null, null), ChangeSource.USER, TEST_REASON))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                settingsService.applyAuto(
                    ME, request("20:00", null, null, null), ChangeSource.TEST, " "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(mySetting.getCallTime()).isEqualTo(CareSetting.DEFAULT_CALL_TIME);
  }
}
