package com.ithopeschool.projectbe.domain.settings.dto;

import com.ithopeschool.projectbe.domain.settings.CareSetting;
import com.ithopeschool.projectbe.domain.settings.ChangeSource;
import com.ithopeschool.projectbe.domain.settings.SettingSource;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 관리 설정을 FE 에 알려 주는 모양입니다. 이름은 FE 의 MessageSettings · CareLimits 와 같습니다.
//   { "callTime": "21:00", "dailyCount": 3, "quietStart": "23:00", "quietEnd": "07:00",
//     "maxHarshness": 2, "allowFocusMode": false,
//     "maxCareLevel": 3, "pendingMaxCareLevel": 4, "pendingEffectiveDate": "2026-10-18",
//     "lockApps": ["유튜브"],
//     "pendingMaxHarshness": null, "pendingHarshnessEffectiveDate": null,
//     "sources": { "dailyCount": { "source": "USER", "reason": "내가 정했어요", "locked": true },
//                  "callTime": { "source": null, "reason": null, "locked": false }, ... } }
// - maxCareLevel · maxHarshness 는 "오늘 적용되는" 값입니다. 올린 값이 아직 적용 전이면 pending... 칸에 있습니다.
// - 바뀔 값이 없으면 pending... 칸은 null 입니다.
// - sources 에는 설정 칸마다 근거가 있습니다. 아무도 정하지 않은 칸(처음 값)은 source · reason 이 null, locked 는 false.
// - 누구의 설정인지(userId)는 담지 않습니다.
// 이슈 #227 에서 칸을 뒤에 더하기만 했습니다. (원래 칸을 쓰던 FE 가 깨지지 않게)
public record SettingsResponse(
    String callTime,
    int dailyCount,
    String quietStart,
    String quietEnd,
    int maxHarshness,
    boolean allowFocusMode,
    int maxCareLevel,
    Integer pendingMaxCareLevel,
    LocalDate pendingEffectiveDate,
    List<String> lockApps,
    Integer pendingMaxHarshness,
    LocalDate pendingHarshnessEffectiveDate,
    Map<String, SourceInfo> sources) {

  // 근거를 보여 주는 설정 칸 이름들 (요청·응답의 JSON 이름과 같습니다)
  public static final List<String> FIELDS =
      List.of(
          "callTime",
          "dailyCount",
          "quietStart",
          "quietEnd",
          "maxHarshness",
          "allowFocusMode",
          "maxCareLevel",
          "lockApps");

  // 설정 칸 하나의 근거: 누가 정했나 · 왜 · 잠겼나
  public record SourceInfo(ChangeSource source, String reason, boolean locked) {

    // 아무도 정하지 않은 칸 (처음 값 그대로)
    static final SourceInfo NONE = new SourceInfo(null, null, false);
  }

  // LocalTime 을 그냥 글자로 바꾸면 "21:00:00" 처럼 초가 붙을 수 있어서, 몇 시 몇 분만 꺼냅니다.
  private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm");

  // 설정 한 줄 · 잠금 대상 앱 이름들 · 근거 줄들로, 그날(today) 기준의 응답을 만듭니다.
  public static SettingsResponse of(
      CareSetting setting, List<String> lockApps, List<SettingSource> sources, LocalDate today) {
    boolean hasPending = setting.hasPendingOn(today);
    boolean hasPendingHarshness = setting.hasPendingHarshnessOn(today);
    return new SettingsResponse(
        format(setting.getCallTime()),
        setting.getDailyMessageCount(),
        format(setting.getQuietStart()),
        format(setting.getQuietEnd()),
        setting.maxHarshnessOn(today),
        setting.isAllowAppLock(),
        setting.maxCareLevelOn(today),
        hasPending ? setting.getPendingMaxCareLevel() : null,
        hasPending ? setting.getPendingEffectiveDate() : null,
        lockApps,
        hasPendingHarshness ? setting.getPendingMaxHarshness() : null,
        hasPendingHarshness ? setting.getPendingHarshnessEffectiveDate() : null,
        sourcesOf(sources));
  }

  // 모든 칸을 먼저 "아무도 안 정함" 으로 채우고, 근거 줄이 있는 칸만 덮어씁니다.
  private static Map<String, SourceInfo> sourcesOf(List<SettingSource> sources) {
    Map<String, SourceInfo> result = new LinkedHashMap<>();
    for (String field : FIELDS) {
      result.put(field, SourceInfo.NONE);
    }
    for (SettingSource source : sources) {
      result.put(
          source.getField(),
          new SourceInfo(source.getSource(), source.getReason(), source.isLocked()));
    }
    return result;
  }

  // "21:00" 모양의 글자로 바꿉니다. (Service 가 "바꾼 기록" 을 적을 때도 같은 모양을 씁니다)
  public static String format(LocalTime time) {
    return time.format(HOUR_MINUTE);
  }
}
