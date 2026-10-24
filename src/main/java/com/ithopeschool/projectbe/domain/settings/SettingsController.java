package com.ithopeschool.projectbe.domain.settings;

import com.ithopeschool.projectbe.domain.settings.dto.SettingsResponse;
import com.ithopeschool.projectbe.domain.settings.dto.SettingsUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 관리 설정 API 의 입구입니다. 요청을 받아 SettingsService 를 부르기만 합니다. (docs/API.md "관리 설정")
// 두 주소 모두 로그인이 필요합니다.
@Tag(name = "관리 설정")
@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class SettingsController {

  private final SettingsService settingsService;

  @Operation(summary = "내 관리 설정 (한 번도 안 바꿨으면 처음 값 · 토큰 필요)")
  @GetMapping
  public SettingsResponse getSettings() {
    return settingsService.getSettings();
  }

  @Operation(
      summary =
          "관리 설정 바꾸기 (바꿀 칸만 보냄 · 보낸 칸은 '내가 정함'으로 잠김 · 제재 한도·말투 수위는 낮추면 즉시, 높이면 다음 날부터 · 바꾼 기록이 남음 · 토큰 필요)")
  @PatchMapping
  public SettingsResponse update(@Valid @RequestBody SettingsUpdateRequest request) {
    return settingsService.update(request);
  }
}
