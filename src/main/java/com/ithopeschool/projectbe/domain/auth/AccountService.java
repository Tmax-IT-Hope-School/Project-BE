package com.ithopeschool.projectbe.domain.auth;

import com.ithopeschool.projectbe.domain.auth.GoogleIdTokenVerifier.GoogleProfile;
import com.ithopeschool.projectbe.domain.auth.dto.PasswordChangeRequest;
import com.ithopeschool.projectbe.domain.auth.dto.ProfileResponse;
import com.ithopeschool.projectbe.domain.auth.dto.ProfileUpdateRequest;
import com.ithopeschool.projectbe.domain.auth.dto.SchoolClassRequest;
import com.ithopeschool.projectbe.domain.auth.dto.SchoolClassResponse;
import com.ithopeschool.projectbe.domain.auth.dto.WithdrawRequest;
import com.ithopeschool.projectbe.domain.user.User;
import com.ithopeschool.projectbe.domain.user.UserRepository;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 로그인한 사람이 자기 계정을 관리하는 로직입니다. (이슈 #101)
//
//   내 정보 보기·고치기 / 내 학년·반 보기·저장 / 비밀번호 바꾸기 / 로그아웃 / 탈퇴
//
// 모든 메서드는 currentUserProvider.getCurrentUserId() 로 시작합니다. 그래서 로그인하지 않은 요청은 401 이 나가고,
// 남의 번호를 보내서 남의 계정을 고치는 일이 생기지 않습니다. (누구의 계정인지를 요청에서 받지 않습니다)
//
// ⚠️ 이메일·실명·비밀번호는 로그에 찍지 않습니다.
@Service
@RequiredArgsConstructor
public class AccountService {

  // 비밀번호가 틀렸을 때의 문장입니다. 비밀번호 바꾸기와 탈퇴가 같이 씁니다.
  private static final String WRONG_PASSWORD_MESSAGE = "비밀번호가 맞지 않아요";

  private final CurrentUserProvider currentUserProvider;
  private final UserRepository userRepository;
  private final AccountRepository accountRepository;
  private final LoginTokenRepository loginTokenRepository;
  private final PasswordHasher passwordHasher;
  // 구글로 가입한 사람이 탈퇴할 때, 구글 로그인을 한 번 더 했는지 확인합니다.
  private final GoogleIdTokenVerifier googleIdTokenVerifier;
  // 탈퇴할 때 "userId 가 있는 모든 테이블" 을 찾아 지우는 데 씁니다.
  private final EntityManager entityManager;

  // 내 정보 보기
  @Transactional(readOnly = true)
  public ProfileResponse getProfile() {
    Long userId = currentUserProvider.getCurrentUserId();
    return ProfileResponse.of(findUser(userId), findAccount(userId));
  }

  // 내 정보 고치기: 닉네임·학교·일기 분석 동의를 받은 값으로 바꿉니다.
  @Transactional
  public ProfileResponse updateProfile(ProfileUpdateRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    User user = findUser(userId);
    Account account = findAccount(userId);

    // 엔티티의 값을 바꾸기만 하면, 이 메서드가 끝날 때 JPA 가 바뀐 것을 알아채고 DB 에 반영합니다. (save 를 부르지 않아도 됩니다)
    user.changeNickname(request.nickname());
    // 기존 실명은 별도 삭제 정책이 정해질 때까지 보존하지만, 요청값으로 갱신하거나 응답에 노출하지 않습니다.
    account.updateProfile(
        account.getName(), request.schoolCode(), request.schoolName(), request.diaryAnalysis());
    return ProfileResponse.of(user, account);
  }

  // 내 학년 · 반 보기: 홈 시간표에 씁니다. 아직 안 골랐으면 둘 다 null 입니다. (2026-10-31)
  @Transactional(readOnly = true)
  public SchoolClassResponse getSchoolClass() {
    Long userId = currentUserProvider.getCurrentUserId();
    return SchoolClassResponse.of(findAccount(userId));
  }

  // 내 학년 · 반 저장: 설정에서 고른 값을 저장합니다. 둘 다 null 이면 지웁니다.
  // 새 기기에서 로그인해도 FE 가 이 값을 받아 바로 시간표를 보여 줄 수 있게 서버에도 둡니다.
  @Transactional
  public SchoolClassResponse updateSchoolClass(SchoolClassRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    // 하나만 오면 어느 반인지 알 수 없어서 막습니다. (범위 검사는 SchoolClassRequest 의 @Min · @Max)
    if ((request.grade() == null) != (request.classNo() == null)) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "학년과 반은 같이 보내 주세요");
    }
    Account account = findAccount(userId);
    account.changeSchoolClass(request.grade(), request.classNo());
    return SchoolClassResponse.of(account);
  }

  // 비밀번호 바꾸기: 지금 비밀번호가 맞아야 합니다. 바꾸면 다른 기기의 로그인은 풀립니다.
  @Transactional
  public void changePassword(PasswordChangeRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    Account account = findAccount(userId);
    checkPassword(request.currentPassword(), account);

    account.changePasswordHash(passwordHasher.hash(request.newPassword()));

    // 비밀번호를 바꾸는 이유가 "누가 내 계정에 들어온 것 같아서" 일 수 있습니다.
    // 그래서 지금 이 요청의 토큰만 남기고, 이 사람의 다른 토큰(다른 기기의 로그인)은 모두 지웁니다.
    String currentTokenHash = currentUserProvider.getCurrentTokenHash();
    if (currentTokenHash == null) {
      loginTokenRepository.deleteByUserId(userId);
    } else {
      loginTokenRepository.deleteByUserIdAndTokenHashNot(userId, currentTokenHash);
    }
  }

  // 로그아웃: 지금 요청에 담긴 토큰을 지웁니다. 지운 뒤에는 그 토큰으로 더 부를 수 없습니다. (401)
  @Transactional
  public void logout() {
    // 로그인한 요청인지 먼저 확인합니다. (토큰이 없거나 틀리면 여기서 401)
    // 동의 검사가 없는 쪽을 씁니다. 최신 문구에 동의하지 않은 사람도 로그아웃할 수 있어야 합니다. (이슈 #71)
    currentUserProvider.getCurrentUserIdWithoutConsent();

    String currentTokenHash = currentUserProvider.getCurrentTokenHash();
    if (currentTokenHash != null) {
      loginTokenRepository.deleteByTokenHash(currentTokenHash);
    }
  }

  // 탈퇴: 본인이 맞는지 한 번 더 확인한 뒤, 이 사람의 데이터를 모두 지웁니다. 되돌릴 수 없습니다. (이슈 #69)
  //   이메일 계정: 비밀번호   /   구글 계정: 구글 로그인을 한 번 더 해서 받은 구글 ID 토큰
  // 구글 계정은 accounts 의 줄(google_subject)이 지워지면서 우리 서비스와 구글의 연결도 끊깁니다.
  // (서버는 구글의 토큰을 갖고 있지 않아서, 사용자의 구글 계정 설정에 남은 "앱 연결" 은 서버가 지울 수 없습니다)
  @Transactional
  public void withdraw(WithdrawRequest request) {
    // 동의 검사가 없는 쪽을 씁니다. 최신 문구에 동의하지 않은 사람도 내 데이터를 지울 수 있어야 하기 때문입니다. (이슈 #71)
    Long userId = currentUserProvider.getCurrentUserIdWithoutConsent();
    Account account = findAccount(userId);
    if (account.hasPassword()) {
      if (request.password() == null || request.password().isBlank()) {
        throw new ApiException(ErrorCode.VALIDATION_ERROR, "비밀번호를 입력해 주세요");
      }
      checkPassword(request.password(), account);
    } else {
      checkSameGoogleAccount(request.credential(), account);
    }

    // 이 프로젝트의 규칙: users 를 뺀 모든 테이블에는 "누구의 것인지" 를 적는 userId 가 있습니다.
    // 그 규칙 덕분에, userId 가 있는 테이블을 전부 돌면서 이 사람의 줄을 지우면 빠뜨리는 것이 없습니다.
    // (계정·로그인 토큰·과목·계획·공부 기록이 모두 여기서 지워지고, 앞으로 테이블이 늘어도 이 코드는 그대로입니다)
    for (EntityType<?> entity : entityManager.getMetamodel().getEntities()) {
      boolean hasUserId =
          entity.getAttributes().stream().anyMatch(a -> a.getName().equals("userId"));
      if (hasUserId) {
        // entity.getName() 은 우리가 만든 클래스 이름(예: StudyPlan)이라, 바깥에서 들어온 글자가 아닙니다.
        // 사용자 번호는 SQL 문자열에 붙이지 않고 파라미터(:userId)로 넘깁니다.
        entityManager
            .createQuery("delete from " + entity.getName() + " e where e.userId = :userId")
            .setParameter("userId", userId)
            .executeUpdate();
      }
    }

    // 마지막으로 users 의 그 사람 한 줄을 지웁니다.
    userRepository.deleteById(userId);
  }

  // 입력한 비밀번호가 이 계정의 비밀번호가 맞는지 확인합니다. 틀리면 400 입니다.
  // 401 이 아니라 400 인 이유: FE 는 401 을 받으면 "로그인이 풀렸다" 고 보고 로그인 화면으로 보냅니다.
  // 비밀번호 한 번 틀렸다고 로그인까지 풀리면 안 되기 때문입니다.
  private void checkPassword(String rawPassword, Account account) {
    // 구글로 가입한 계정은 비밀번호가 없습니다. (비밀번호 바꾸기는 이메일 계정만 됩니다. 구글 계정의 탈퇴는 checkSameGoogleAccount)
    if (!account.hasPassword()) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "구글로 가입한 계정은 비밀번호가 없어요");
    }
    if (!passwordHasher.matches(rawPassword, account.getPasswordHash())) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, WRONG_PASSWORD_MESSAGE);
    }
  }

  // 구글 계정의 탈퇴 확인: 보낸 구글 ID 토큰이 진짜이고, 가입할 때 쓴 바로 그 구글 계정인지 봅니다.
  // 다른 사람의 구글 계정으로 로그인해서 내 계정을 지우는 일을 막으려고 고유 번호(google_subject)까지 비교합니다.
  // 401 이 아니라 400 인 이유는 checkPassword 와 같습니다. (확인 실패로 로그인까지 풀리면 안 됨)
  private void checkSameGoogleAccount(String credential, Account account) {
    if (credential == null || credential.isBlank()) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "구글 확인 정보를 보내 주세요");
    }
    if (!googleIdTokenVerifier.isEnabled()) {
      throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "구글 로그인이 아직 켜져 있지 않아요");
    }
    GoogleProfile profile =
        googleIdTokenVerifier
            .verify(credential)
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCode.VALIDATION_ERROR, "구글 확인에 실패했어요. 다시 Google로 확인해 주세요"));
    if (!profile.subject().equals(account.getGoogleSubject())) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "가입할 때 쓴 구글 계정으로 확인해 주세요");
    }
  }

  private User findUser(Long userId) {
    return userRepository
        .findById(userId)
        // 토큰은 맞는데 사용자가 없는 경우(지워진 사용자)입니다. 다시 로그인하게 합니다.
        .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "다시 로그인해 주세요"));
  }

  private Account findAccount(Long userId) {
    return accountRepository
        .findByUserId(userId)
        // 사용자는 있는데 계정이 없는 경우입니다. 내 컴퓨터의 테스트 사용자(테스트 토큰)가 여기에 해당합니다.
        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "이메일로 가입한 계정이 없어요"));
  }
}
