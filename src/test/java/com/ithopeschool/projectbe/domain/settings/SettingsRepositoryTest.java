package com.ithopeschool.projectbe.domain.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

// 관리 설정 테이블 4개가 진짜 DB(H2)에서 "내 것만" 찾는지, 그리고 DB 가 규칙(한 명에 한 줄 · 같은 앱 한 번 · 같은 칸 근거 한 줄 · 값의 범위)을 막는지
// 확인합니다.
@DataJpaTest
class SettingsRepositoryTest {

  @Autowired private CareSettingRepository careSettingRepository;
  @Autowired private FocusLockAppRepository focusLockAppRepository;
  @Autowired private SettingChangeRepository settingChangeRepository;
  @Autowired private SettingSourceRepository settingSourceRepository;
  @Autowired private TestEntityManager em;

  // ---- 관리 설정 ----

  @Test
  @DisplayName("내 설정은 내 번호로 찾아지고, 설정이 없는 사람은 빈 Optional")
  void findByUserId() {
    em.persistAndFlush(CareSetting.withDefaults(1L));
    em.persistAndFlush(CareSetting.withDefaults(2L));
    em.clear();

    assertThat(careSettingRepository.findByUserId(1L))
        .get()
        .extracting(CareSetting::getUserId)
        .isEqualTo(1L);
    assertThat(careSettingRepository.findByUserId(999L)).isEmpty();
  }

  @Test
  @DisplayName("시각·날짜·바뀔 한도가 그대로 저장되고 읽힌다")
  void storesValues() {
    CareSetting setting = CareSetting.withDefaults(1L);
    setting.changeCallTime(LocalTime.of(20, 30));
    setting.changeMaxCareLevel(4, LocalDate.of(2026, 10, 17));
    em.persistAndFlush(setting);
    em.clear();

    CareSetting loaded = careSettingRepository.findByUserId(1L).orElseThrow();
    assertThat(loaded.getCallTime()).isEqualTo(LocalTime.of(20, 30));
    assertThat(loaded.getQuietStart()).isEqualTo(LocalTime.of(23, 0));
    assertThat(loaded.getPendingMaxCareLevel()).isEqualTo(4);
    assertThat(loaded.getPendingEffectiveDate()).isEqualTo(LocalDate.of(2026, 10, 18));
  }

  @Test
  @DisplayName("한 사람의 설정 줄이 두 개가 되면 DB 가 막는다")
  void oneRowPerUser() {
    em.persistAndFlush(CareSetting.withDefaults(1L));

    assertThatThrownBy(() -> em.persistAndFlush(CareSetting.withDefaults(1L)))
        .isInstanceOf(PersistenceException.class);
  }

  @Test
  @DisplayName("독설 수위가 1~5 밖이면 DB 가 한 번 더 막는다")
  void harshnessRange() {
    CareSetting setting = CareSetting.withDefaults(1L);
    setting.changeMaxHarshness(0, LocalDate.of(2026, 10, 17));

    assertThatThrownBy(() -> em.persistAndFlush(setting)).isInstanceOf(PersistenceException.class);
  }

  @Test
  @DisplayName("하루 메시지 횟수가 0~5 밖이면 DB 가 한 번 더 막는다")
  void dailyMessageCountRange() {
    CareSetting setting = CareSetting.withDefaults(1L);
    setting.changeDailyMessageCount(6);

    assertThatThrownBy(() -> em.persistAndFlush(setting)).isInstanceOf(PersistenceException.class);
  }

  // ---- 잠금 대상 앱 ----

  @Test
  @DisplayName("내 앱만, 넣은 순서대로 나온다")
  void findLockApps() {
    em.persistAndFlush(new FocusLockApp(1L, "유튜브"));
    em.persistAndFlush(new FocusLockApp(2L, "남의 앱"));
    em.persistAndFlush(new FocusLockApp(1L, "게임"));
    em.clear();

    assertThat(focusLockAppRepository.findByUserIdOrderById(1L))
        .extracting(FocusLockApp::getAppName)
        .containsExactly("유튜브", "게임");
  }

  @Test
  @DisplayName("같은 사람이 같은 앱을 두 번 넣으면 DB 가 막는다. 다른 사람은 같은 앱을 넣을 수 있다")
  void sameAppOncePerUser() {
    em.persistAndFlush(new FocusLockApp(1L, "유튜브"));
    em.persistAndFlush(new FocusLockApp(2L, "유튜브"));

    assertThatThrownBy(() -> em.persistAndFlush(new FocusLockApp(1L, "유튜브")))
        .isInstanceOf(PersistenceException.class);
  }

  @Test
  @DisplayName("내 앱을 모두 지운 뒤 바로 같은 앱을 다시 넣을 수 있고, 남의 앱은 지워지지 않는다")
  void deleteAllOfUserThenInsertSame() {
    em.persistAndFlush(new FocusLockApp(1L, "유튜브"));
    em.persistAndFlush(new FocusLockApp(2L, "유튜브"));

    focusLockAppRepository.deleteAllOfUser(1L);
    focusLockAppRepository.saveAndFlush(new FocusLockApp(1L, "유튜브"));
    em.clear();

    assertThat(focusLockAppRepository.findByUserIdOrderById(1L)).hasSize(1);
    assertThat(focusLockAppRepository.findByUserIdOrderById(2L)).hasSize(1);
  }

  // ---- 바꾼 기록 ----

  @Test
  @DisplayName("내 기록만, 바꾼 순서대로 나온다")
  void findChanges() {
    Instant now = Instant.parse("2026-10-17T12:00:00Z");
    em.persistAndFlush(new SettingChange(1L, now, "maxHarshness", "3", "5", ChangeSource.USER));
    em.persistAndFlush(new SettingChange(2L, now, "dailyCount", "3", "0", ChangeSource.USER));
    em.persistAndFlush(
        new SettingChange(1L, now, "quietStart", "23:00", "22:00", ChangeSource.DATA));
    em.clear();

    assertThat(settingChangeRepository.findByUserIdOrderById(1L))
        .extracting(SettingChange::getField)
        .containsExactly("maxHarshness", "quietStart");
  }

  // ---- 설정 근거 (#227) ----

  @Test
  @DisplayName("내 근거만 나오고, 누가 정했는지 · 이유 · 잠금이 그대로 저장된다")
  void findSources() {
    em.persistAndFlush(new SettingSource(1L, "dailyCount", ChangeSource.USER, "내가 정했어요", true));
    em.persistAndFlush(
        new SettingSource(2L, "dailyCount", ChangeSource.TEST, "검사 결과로 정했어요", false));
    em.clear();

    assertThat(settingSourceRepository.findByUserId(1L))
        .singleElement()
        .satisfies(
            source -> {
              assertThat(source.getSource()).isEqualTo(ChangeSource.USER);
              assertThat(source.getReason()).isEqualTo("내가 정했어요");
              assertThat(source.isLocked()).isTrue();
            });
  }

  @Test
  @DisplayName("한 사람의 같은 칸에 근거를 두 줄 넣으면 DB 가 막는다")
  void oneSourcePerField() {
    em.persistAndFlush(new SettingSource(1L, "dailyCount", ChangeSource.TEST, "검사", false));

    assertThatThrownBy(
            () ->
                em.persistAndFlush(
                    new SettingSource(1L, "dailyCount", ChangeSource.USER, "내가 정했어요", true)))
        .isInstanceOf(PersistenceException.class);
  }
}
