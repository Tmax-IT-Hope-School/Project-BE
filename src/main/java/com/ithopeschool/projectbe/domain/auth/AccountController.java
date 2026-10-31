package com.ithopeschool.projectbe.domain.auth;

import com.ithopeschool.projectbe.domain.auth.dto.PasswordChangeRequest;
import com.ithopeschool.projectbe.domain.auth.dto.ProfileResponse;
import com.ithopeschool.projectbe.domain.auth.dto.ProfileUpdateRequest;
import com.ithopeschool.projectbe.domain.auth.dto.SchoolClassRequest;
import com.ithopeschool.projectbe.domain.auth.dto.SchoolClassResponse;
import com.ithopeschool.projectbe.domain.auth.dto.WithdrawRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// "내 계정" API 의 입구입니다. 전부 로그인이 필요합니다. (docs/API.md "내 계정")
// 가입·로그인(AuthController)과 같은 /api/v1/auth 아래에 두되, 로그인한 뒤에 쓰는 것만 이 파일에 모았습니다.
@Tag(name = "내 계정")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AccountController {

  private final AccountService accountService;

  @Operation(summary = "내 정보 보기 (이메일·이름·닉네임·학교·일기 분석 동의)")
  @GetMapping("/profile")
  public ProfileResponse getProfile() {
    return accountService.getProfile();
  }

  @Operation(summary = "내 정보 고치기 (이름·닉네임·학교·일기 분석 동의를 한꺼번에 보냄)")
  @PutMapping("/profile")
  public ProfileResponse updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
    return accountService.updateProfile(request);
  }

  @Operation(summary = "내 학년 · 반 보기 (홈 시간표용 · 안 골랐으면 둘 다 null)")
  @GetMapping("/school-class")
  public SchoolClassResponse getSchoolClass() {
    return accountService.getSchoolClass();
  }

  @Operation(summary = "내 학년 · 반 저장 (둘 다 보내거나, 지우려면 둘 다 null)")
  @PutMapping("/school-class")
  public SchoolClassResponse updateSchoolClass(@Valid @RequestBody SchoolClassRequest request) {
    return accountService.updateSchoolClass(request);
  }

  @Operation(summary = "비밀번호 바꾸기 (지금 비밀번호 확인 · 다른 기기의 로그인은 풀림)")
  @PutMapping("/password")
  // 돌려줄 내용이 없어서 204 로 답합니다. (docs/API.md 4장)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changePassword(@Valid @RequestBody PasswordChangeRequest request) {
    accountService.changePassword(request);
  }

  @Operation(summary = "로그아웃 (서버의 토큰을 지움)")
  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout() {
    accountService.logout();
  }

  @Operation(summary = "탈퇴 (비밀번호 확인 · 계정과 기록을 모두 지움 · 되돌릴 수 없음)")
  @PostMapping("/withdraw")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void withdraw(@Valid @RequestBody WithdrawRequest request) {
    accountService.withdraw(request);
  }
}
