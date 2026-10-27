package com.ithopeschool.projectbe.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ithopeschool.projectbe.domain.auth.GoogleIdTokenVerifier.GoogleProfile;
import com.ithopeschool.projectbe.domain.auth.dto.GoogleSignupRequest;
import com.ithopeschool.projectbe.domain.auth.dto.LoginRequest;
import com.ithopeschool.projectbe.domain.auth.dto.PasswordChangeRequest;
import com.ithopeschool.projectbe.domain.auth.dto.ProfileResponse;
import com.ithopeschool.projectbe.domain.auth.dto.ProfileUpdateRequest;
import com.ithopeschool.projectbe.domain.auth.dto.SignupRequest;
import com.ithopeschool.projectbe.domain.auth.dto.WithdrawRequest;
import com.ithopeschool.projectbe.domain.bias.CognitiveBias;
import com.ithopeschool.projectbe.domain.bias.Weakness;
import com.ithopeschool.projectbe.domain.call.CallRecord;
import com.ithopeschool.projectbe.domain.card.Card;
import com.ithopeschool.projectbe.domain.card.ChecklistItem;
import com.ithopeschool.projectbe.domain.checkin.CheckIn;
import com.ithopeschool.projectbe.domain.checkin.CheckInSource;
import com.ithopeschool.projectbe.domain.checkin.EmotionTag;
import com.ithopeschool.projectbe.domain.consent.ConsentRecordRepository;
import com.ithopeschool.projectbe.domain.consent.ConsentService;
import com.ithopeschool.projectbe.domain.experiment.Experiment;
import com.ithopeschool.projectbe.domain.experiment.ExperimentStrategy;
import com.ithopeschool.projectbe.domain.goal.Goal;
import com.ithopeschool.projectbe.domain.goal.GoalKind;
import com.ithopeschool.projectbe.domain.insight.Insight;
import com.ithopeschool.projectbe.domain.onboarding.FailureCause;
import com.ithopeschool.projectbe.domain.onboarding.Grade;
import com.ithopeschool.projectbe.domain.onboarding.OnboardingAnswer;
import com.ithopeschool.projectbe.domain.onboarding.SubjectLevel;
import com.ithopeschool.projectbe.domain.plan.PlanKind;
import com.ithopeschool.projectbe.domain.plan.StudyPlan;
import com.ithopeschool.projectbe.domain.psychtest.PsychItem;
import com.ithopeschool.projectbe.domain.psychtest.PsychTest;
import com.ithopeschool.projectbe.domain.risk.RiskForecast;
import com.ithopeschool.projectbe.domain.risk.RiskLevel;
import com.ithopeschool.projectbe.domain.risk.RiskReason;
import com.ithopeschool.projectbe.domain.session.SessionMemo;
import com.ithopeschool.projectbe.domain.session.SessionMode;
import com.ithopeschool.projectbe.domain.session.SessionPause;
import com.ithopeschool.projectbe.domain.session.SessionTags.GoodReason;
import com.ithopeschool.projectbe.domain.session.SessionTags.PauseTag;
import com.ithopeschool.projectbe.domain.session.StudySession;
import com.ithopeschool.projectbe.domain.subject.Subject;
import com.ithopeschool.projectbe.domain.subject.SubjectColor;
import com.ithopeschool.projectbe.domain.thinkingtest.ThinkingTest;
import com.ithopeschool.projectbe.domain.user.UserRepository;
import com.ithopeschool.projectbe.domain.variant.SourceProblem;
import com.ithopeschool.projectbe.domain.variant.Variant;
import com.ithopeschool.projectbe.domain.variant.VariantChoice;
import com.ithopeschool.projectbe.domain.variant.VariantFormat;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.mock.web.MockHttpServletRequest;

// "내 계정" 로직을 테스트용 DB(H2)에 실제로 넣고 꺼내 보며 확인합니다.
// 두 사람(에이·비이)을 가입시켜 두고, 한 사람이 한 일이 다른 사람에게 번지지 않는지도 같이 봅니다.
@DataJpaTest
class AccountServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-11T06:00:00Z");
  private static final String PASSWORD = "study2026";

  @Autowired private UserRepository userRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private LoginTokenRepository loginTokenRepository;
  @Autowired private ConsentRecordRepository consentRecordRepository;
  @Autowired private EntityManager entityManager;

  // 테스트가 빨리 끝나게 되풀이 횟수는 가장 작은 값으로 둡니다.
  private final PasswordHasher passwordHasher = new PasswordHasher(PasswordHasher.MIN_ITERATIONS);
  private final TokenHasher tokenHasher = new TokenHasher();
  // 구글 확인은 진짜 구글에 묻지 않고 가짜로 둡니다. (구글 계정 탈퇴 테스트에서 답을 정해 줍니다)
  private final GoogleIdTokenVerifier googleIdTokenVerifier = mock(GoogleIdTokenVerifier.class);
  // 가짜 요청입니다. Authorization 헤더를 바꿔 끼워서 "누가 부른 요청인지" 를 흉내 냅니다.
  private MockHttpServletRequest request;
  private AuthService authService;
  private AccountService accountService;

  // 에이가 로그인해서 받은 토큰 (기기 두 대에서 로그인한 것처럼 두 개), 비이의 토큰
  private String tokenOfA;
  private String secondTokenOfA;
  private String tokenOfB;
  private Long idOfA;
  private Long idOfB;

  @BeforeEach
  void setUp() {
    request = new MockHttpServletRequest();
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    CurrentUserProvider currentUserProvider =
        new CurrentUserProvider(
            request, loginTokenRepository, consentRecordRepository, tokenHasher, clock, "");
    authService =
        new AuthService(
            userRepository,
            accountRepository,
            loginTokenRepository,
            passwordHasher,
            tokenHasher,
            currentUserProvider,
            googleIdTokenVerifier,
            new ConsentService(currentUserProvider, consentRecordRepository, clock),
            clock);
    accountService =
        new AccountService(
            currentUserProvider,
            userRepository,
            accountRepository,
            loginTokenRepository,
            passwordHasher,
            googleIdTokenVerifier,
            entityManager);

    idOfA = Long.valueOf(authService.signup(signupRequest("a@starflow.com", "에이")).id());
    idOfB = Long.valueOf(authService.signup(signupRequest("b@starflow.com", "비이")).id());
    tokenOfA = authService.login(new LoginRequest("a@starflow.com", PASSWORD)).token();
    secondTokenOfA = authService.login(new LoginRequest("a@starflow.com", PASSWORD)).token();
    tokenOfB = authService.login(new LoginRequest("b@starflow.com", PASSWORD)).token();
  }

  private static SignupRequest signupRequest(String email, String nickname) {
    return new SignupRequest(
        email,
        PASSWORD,
        "김티맥",
        nickname,
        "7530560",
        "한국디지털미디어고등학교",
        new SignupRequest.Consents(true, true, false, true, "v1"));
  }

  // 이 토큰으로 부른 요청인 것처럼 만듭니다. null 이면 로그인하지 않은 요청입니다.
  private void callWith(String token) {
    request.removeHeader("Authorization");
    if (token != null) {
      request.addHeader("Authorization", "Bearer " + token);
    }
  }

  private static void assertError(Runnable action, ErrorCode code, String message) {
    assertThatThrownBy(action::run)
        .isInstanceOf(ApiException.class)
        .hasMessage(message)
        .extracting(e -> ((ApiException) e).getErrorCode())
        .isEqualTo(code);
  }

  // ---- 내 정보 보기 ----

  @Test
  @DisplayName("내 정보에는 내 이메일·이름·닉네임·학교·동의가 나오고, 남의 것은 나오지 않는다")
  void profileShowsMine() {
    callWith(tokenOfA);
    ProfileResponse mine = accountService.getProfile();

    assertThat(mine.email()).isEqualTo("a@starflow.com");
    assertThat(mine.name()).isEqualTo("에이");
    assertThat(mine.nickname()).isEqualTo("에이");
    assertThat(mine.schoolCode()).isEqualTo("7530560");
    assertThat(mine.schoolName()).isEqualTo("한국디지털미디어고등학교");
    assertThat(mine.diaryAnalysis()).isFalse();

    callWith(tokenOfB);
    assertThat(accountService.getProfile().nickname()).isEqualTo("비이");
  }

  @Test
  @DisplayName("로그인하지 않으면 다섯 가지 모두 401")
  void everythingRequiresLogin() {
    callWith(null);
    String message = "로그인이 필요해요";

    assertError(() -> accountService.getProfile(), ErrorCode.UNAUTHORIZED, message);
    assertError(
        () ->
            accountService.updateProfile(new ProfileUpdateRequest("김티맥", "새별명", null, null, true)),
        ErrorCode.UNAUTHORIZED,
        message);
    assertError(
        () -> accountService.changePassword(new PasswordChangeRequest(PASSWORD, "newpass2026")),
        ErrorCode.UNAUTHORIZED,
        message);
    assertError(() -> accountService.logout(), ErrorCode.UNAUTHORIZED, message);
    assertError(
        () -> accountService.withdraw(new WithdrawRequest(PASSWORD, null)),
        ErrorCode.UNAUTHORIZED,
        message);
    // 아무것도 바뀌지 않았습니다.
    assertThat(userRepository.count()).isEqualTo(2);
  }

  // ---- 내 정보 고치기 ----

  @Test
  @DisplayName("기존 DB 실명은 응답에서 숨기고, 프로필 수정으로 덮어쓰거나 삭제하지 않는다")
  void legacyRealNameIsNeverReturnedOrReplaced() {
    Account account = accountRepository.findByUserId(idOfA).orElseThrow();
    account.updateProfile("기존실명", null, null, false);
    callWith(tokenOfA);
    assertThat(accountService.getProfile().name()).isEqualTo("에이");

    ProfileResponse updated =
        accountService.updateProfile(new ProfileUpdateRequest("새실명", "새별명", null, null, false));
    assertThat(updated.name()).isEqualTo("새별명");
    assertThat(account.getName()).isEqualTo("기존실명");
  }

  @Test
  @DisplayName("이름·닉네임·학교·일기 분석 동의를 고치면 DB 에 반영되고, 남의 정보는 그대로다")
  void updateProfileChangesOnlyMine() {
    callWith(tokenOfA);
    ProfileResponse updated =
        accountService.updateProfile(
            new ProfileUpdateRequest("김하늘", "하늘이", "1234567", "별빛고등학교", true));

    assertThat(updated.name()).isEqualTo("하늘이");
    assertThat(updated.nickname()).isEqualTo("하늘이");
    assertThat(updated.schoolName()).isEqualTo("별빛고등학교");
    assertThat(updated.diaryAnalysis()).isTrue();
    // 이메일은 바뀌지 않습니다.
    assertThat(updated.email()).isEqualTo("a@starflow.com");

    entityManager.flush();
    entityManager.clear();
    assertThat(userRepository.findById(idOfA).orElseThrow().getNickname()).isEqualTo("하늘이");
    assertThat(accountRepository.findByUserId(idOfA).orElseThrow().isDiaryAnalysisAgreed())
        .isTrue();
    assertThat(userRepository.findById(idOfB).orElseThrow().getNickname()).isEqualTo("비이");
    assertThat(accountRepository.findByUserId(idOfB).orElseThrow().getName()).isNull();
  }

  @Test
  @DisplayName("학교를 null 로 보내면 학교가 지워지고, 선택 동의를 다시 끌 수 있다")
  void schoolCanBeClearedAndConsentWithdrawn() {
    callWith(tokenOfA);
    accountService.updateProfile(
        new ProfileUpdateRequest("김티맥", "에이", "7530560", "한국디지털미디어고등학교", true));

    ProfileResponse updated =
        accountService.updateProfile(new ProfileUpdateRequest("김티맥", "에이", null, null, false));

    assertThat(updated.schoolCode()).isNull();
    assertThat(updated.schoolName()).isNull();
    assertThat(updated.diaryAnalysis()).isFalse();
  }

  // ---- 비밀번호 바꾸기 ----

  @Test
  @DisplayName("지금 비밀번호가 틀리면 400 이고 비밀번호가 바뀌지 않는다 (401 이 아니라서 로그인이 풀리지 않는다)")
  void changePasswordNeedsCurrentPassword() {
    callWith(tokenOfA);

    assertError(
        () -> accountService.changePassword(new PasswordChangeRequest("wrong2026", "newpass2026")),
        ErrorCode.VALIDATION_ERROR,
        "비밀번호가 맞지 않아요");

    assertThat(authService.login(new LoginRequest("a@starflow.com", PASSWORD)).token())
        .isNotBlank();
  }

  @Test
  @DisplayName("비밀번호를 바꾸면 옛 비밀번호로는 로그인되지 않고 새 비밀번호로 로그인된다")
  void changePasswordWorks() {
    callWith(tokenOfA);
    accountService.changePassword(new PasswordChangeRequest(PASSWORD, "newpass2026"));

    assertError(
        () -> authService.login(new LoginRequest("a@starflow.com", PASSWORD)),
        ErrorCode.UNAUTHORIZED,
        "이메일 또는 비밀번호가 맞지 않아요");
    assertThat(authService.login(new LoginRequest("a@starflow.com", "newpass2026")).token())
        .isNotBlank();
    // 저장된 값에 새 비밀번호가 그대로 들어 있지 않습니다.
    assertThat(accountRepository.findByUserId(idOfA).orElseThrow().getPasswordHash())
        .doesNotContain("newpass2026");
  }

  @Test
  @DisplayName("비밀번호를 바꾸면 지금 기기는 그대로이고 다른 기기의 로그인만 풀린다. 남의 로그인은 그대로다")
  void changePasswordLogsOutOtherDevices() {
    callWith(tokenOfA);
    accountService.changePassword(new PasswordChangeRequest(PASSWORD, "newpass2026"));

    // 지금 기기(tokenOfA)는 계속 쓸 수 있습니다.
    assertThat(accountService.getProfile().nickname()).isEqualTo("에이");
    // 다른 기기(secondTokenOfA)는 풀렸습니다.
    callWith(secondTokenOfA);
    assertError(() -> accountService.getProfile(), ErrorCode.UNAUTHORIZED, "다시 로그인해 주세요");
    // 비이의 로그인은 그대로입니다.
    callWith(tokenOfB);
    assertThat(accountService.getProfile().nickname()).isEqualTo("비이");
  }

  // ---- 로그아웃 ----

  @Test
  @DisplayName("로그아웃하면 그 토큰으로는 더 부를 수 없고, 다른 기기와 남의 로그인은 그대로다")
  void logoutDeletesOnlyThisToken() {
    callWith(tokenOfA);
    accountService.logout();

    assertError(() -> accountService.getProfile(), ErrorCode.UNAUTHORIZED, "다시 로그인해 주세요");
    callWith(secondTokenOfA);
    assertThat(accountService.getProfile().nickname()).isEqualTo("에이");
    callWith(tokenOfB);
    assertThat(accountService.getProfile().nickname()).isEqualTo("비이");
  }

  // ---- 탈퇴 ----

  // 에이와 비이에게 과목·계획·공부 기록을 하나씩 만들어 둡니다.
  private void giveBothSomeRecords() {
    for (Long userId : new Long[] {idOfA, idOfB}) {
      Subject subject = new Subject(userId, "수학", SubjectColor.RED);
      entityManager.persist(subject);
      entityManager.persist(
          new StudyPlan(userId, subject.getId(), LocalDate.of(2026, 10, 11), "3단원", 60));
      entityManager.persist(
          new StudySession(userId, subject.getId(), NOW.minusSeconds(3600), NOW, 0));
    }
    entityManager.flush();
  }

  private long countRows(String entityName, Long userId) {
    return entityManager
        .createQuery(
            "select count(e) from " + entityName + " e where e.userId = :userId", Long.class)
        .setParameter("userId", userId)
        .getSingleResult();
  }

  @Test
  @DisplayName("비밀번호가 틀리면 탈퇴되지 않는다 (400, 아무것도 지워지지 않음)")
  void withdrawNeedsPassword() {
    giveBothSomeRecords();
    callWith(tokenOfA);

    assertError(
        () -> accountService.withdraw(new WithdrawRequest("wrong2026", null)),
        ErrorCode.VALIDATION_ERROR,
        "비밀번호가 맞지 않아요");

    assertThat(userRepository.existsById(idOfA)).isTrue();
    assertThat(countRows("Subject", idOfA)).isEqualTo(1);
    assertThat(accountService.getProfile().nickname()).isEqualTo("에이");
  }

  @Test
  @DisplayName("이메일 계정이 비밀번호 없이 구글 확인 정보만 보내면 탈퇴되지 않는다 (400)")
  void emailAccountNeedsPasswordNotGoogle() {
    callWith(tokenOfA);

    assertError(
        () -> accountService.withdraw(new WithdrawRequest(null, GOOGLE_CREDENTIAL)),
        ErrorCode.VALIDATION_ERROR,
        "비밀번호를 입력해 주세요");
    assertThat(userRepository.existsById(idOfA)).isTrue();
  }

  // ---- 구글로 가입한 사람의 탈퇴 (이슈 #69) ----

  private static final String GOOGLE_CREDENTIAL = "google-id-token-of-c";
  private static final String GOOGLE_SUBJECT = "google-subject-c";

  // 구글로 가입한 사람 씨를 만들고, 씨의 로그인 토큰으로 부른 것처럼 합니다. 씨의 사용자 번호를 돌려줍니다.
  private Long googleUserLoggedIn() {
    given(googleIdTokenVerifier.isEnabled()).willReturn(true);
    given(googleIdTokenVerifier.verify(GOOGLE_CREDENTIAL))
        .willReturn(Optional.of(new GoogleProfile(GOOGLE_SUBJECT, "c@gmail.com", "김씨")));
    String token =
        authService
            .googleSignup(
                new GoogleSignupRequest(
                    GOOGLE_CREDENTIAL,
                    "김씨",
                    "씨씨",
                    null,
                    null,
                    new SignupRequest.Consents(true, true, false, true, "v1")))
            .token();
    callWith(token);
    return accountRepository.findByGoogleSubject(GOOGLE_SUBJECT).orElseThrow().getUserId();
  }

  @Test
  @DisplayName("구글 계정은 구글 로그인을 한 번 더 해서 받은 확인 정보로 탈퇴하고, 계정(구글 연결)까지 지워진다")
  void googleAccountWithdraws() {
    Long idOfC = googleUserLoggedIn();

    accountService.withdraw(new WithdrawRequest(null, GOOGLE_CREDENTIAL));
    entityManager.flush();
    entityManager.clear();

    assertThat(userRepository.existsById(idOfC)).isFalse();
    assertThat(accountRepository.findByGoogleSubject(GOOGLE_SUBJECT)).isEmpty();
    // 남의 것은 그대로
    assertThat(userRepository.existsById(idOfA)).isTrue();
  }

  @Test
  @DisplayName("구글 계정이 확인 정보 없이(비밀번호만) 보내면 400 이고 아무것도 지워지지 않는다")
  void googleAccountNeedsCredential() {
    Long idOfC = googleUserLoggedIn();

    assertError(
        () -> accountService.withdraw(new WithdrawRequest(PASSWORD, null)),
        ErrorCode.VALIDATION_ERROR,
        "구글 확인 정보를 보내 주세요");
    assertThat(userRepository.existsById(idOfC)).isTrue();
  }

  @Test
  @DisplayName("다른 사람의 구글 계정으로 확인하면 400 이고 아무것도 지워지지 않는다")
  void otherGoogleAccountCannotWithdraw() {
    Long idOfC = googleUserLoggedIn();
    given(googleIdTokenVerifier.verify("someone-elses-token"))
        .willReturn(Optional.of(new GoogleProfile("other-subject", "x@gmail.com", "남")));

    assertError(
        () -> accountService.withdraw(new WithdrawRequest(null, "someone-elses-token")),
        ErrorCode.VALIDATION_ERROR,
        "가입할 때 쓴 구글 계정으로 확인해 주세요");
    assertThat(userRepository.existsById(idOfC)).isTrue();
  }

  @Test
  @DisplayName("가짜이거나 시간이 지난 구글 확인 정보면 400 이고 아무것도 지워지지 않는다")
  void invalidGoogleCredential() {
    Long idOfC = googleUserLoggedIn();
    given(googleIdTokenVerifier.verify("expired-token")).willReturn(Optional.empty());

    assertError(
        () -> accountService.withdraw(new WithdrawRequest(null, "expired-token")),
        ErrorCode.VALIDATION_ERROR,
        "구글 확인에 실패했어요. 다시 Google로 확인해 주세요");
    assertThat(userRepository.existsById(idOfC)).isTrue();
  }

  @Test
  @DisplayName("서버에 구글 로그인이 꺼져 있으면 503 이고 아무것도 지워지지 않는다")
  void googleOff() {
    Long idOfC = googleUserLoggedIn();
    given(googleIdTokenVerifier.isEnabled()).willReturn(false);

    assertError(
        () -> accountService.withdraw(new WithdrawRequest(null, GOOGLE_CREDENTIAL)),
        ErrorCode.SERVICE_UNAVAILABLE,
        "구글 로그인이 아직 켜져 있지 않아요");
    assertThat(userRepository.existsById(idOfC)).isTrue();
  }

  @Test
  @DisplayName("탈퇴하면 사용자·계정·토큰·과목·계획·공부 기록이 모두 지워지고, 남의 것은 그대로다")
  void withdrawDeletesEverythingOfMine() {
    giveBothSomeRecords();
    callWith(tokenOfA);

    accountService.withdraw(new WithdrawRequest(PASSWORD, null));
    entityManager.flush();
    entityManager.clear();

    assertThat(userRepository.existsById(idOfA)).isFalse();
    for (String table :
        new String[] {"Account", "LoginToken", "Subject", "StudyPlan", "StudySession"}) {
      assertThat(countRows(table, idOfA)).as("에이의 %s", table).isZero();
      assertThat(countRows(table, idOfB)).as("비이의 %s", table).isEqualTo(1);
    }
    assertThat(userRepository.existsById(idOfB)).isTrue();
  }

  @Test
  @DisplayName("탈퇴한 뒤에는 그 토큰과 이메일·비밀번호가 통하지 않고, 같은 이메일로 다시 가입할 수 있다")
  void afterWithdraw() {
    callWith(tokenOfA);
    accountService.withdraw(new WithdrawRequest(PASSWORD, null));
    entityManager.flush();
    entityManager.clear();

    assertError(() -> accountService.getProfile(), ErrorCode.UNAUTHORIZED, "다시 로그인해 주세요");
    callWith(secondTokenOfA);
    assertError(() -> accountService.getProfile(), ErrorCode.UNAUTHORIZED, "다시 로그인해 주세요");
    assertError(
        () -> authService.login(new LoginRequest("a@starflow.com", PASSWORD)),
        ErrorCode.UNAUTHORIZED,
        "이메일 또는 비밀번호가 맞지 않아요");

    assertThat(authService.signup(signupRequest("a@starflow.com", "다시 온 에이")).name())
        .isEqualTo("다시 온 에이");
  }

  @Test
  @DisplayName("탈퇴할 때 지우는 대상은 'userId 가 있는 모든 테이블' 이다 (테이블이 늘어도 그대로)")
  void everyTableWithUserIdIsCovered() {
    // 새 테이블을 만들면 이 숫자가 늘어납니다. 그 테이블에 userId 가 있으면 탈퇴 때 자동으로 지워집니다.
    long tablesWithUserId =
        entityManager.getMetamodel().getEntities().stream()
            .filter(e -> e.getAttributes().stream().anyMatch(a -> a.getName().equals("userId")))
            .count();
    long allTables = entityManager.getMetamodel().getEntities().size();

    // users 와 학교 공개 자료(PUBLIC_TABLES)만 userId 가 없습니다.
    assertThat(tablesWithUserId).isEqualTo(allTables - 1 - PUBLIC_TABLES.size());
  }

  @Test
  @DisplayName("탈퇴하면 통화 기록·생각 습관 테스트(딸린 점수·약점 테이블까지)도 지워지고, 남의 것은 그대로다")
  void withdrawDeletesCallsAndThinkingTests() {
    for (Long userId : new Long[] {idOfA, idOfB}) {
      CallRecord call = new CallRecord(userId, LocalDate.of(2026, 10, 11), NOW, null);
      call.finish(NOW, "요약", CognitiveBias.FUTURE_SELF, "근거", null, null, "A", "B", null);
      entityManager.persist(call);
      Map<CognitiveBias, Double> scores = new EnumMap<>(CognitiveBias.class);
      for (CognitiveBias bias : CognitiveBias.values()) {
        scores.put(bias, 4.0);
      }
      entityManager.persist(
          new ThinkingTest(userId, NOW, 2, scores, EnumSet.of(Weakness.GAME, Weakness.SLEEP)));
    }
    entityManager.flush();
    callWith(tokenOfA);

    accountService.withdraw(new WithdrawRequest(PASSWORD, null));
    entityManager.flush();
    entityManager.clear();

    for (String table : new String[] {"CallRecord", "ThinkingTest"}) {
      assertThat(countRows(table, idOfA)).as("에이의 %s", table).isZero();
      assertThat(countRows(table, idOfB)).as("비이의 %s", table).isEqualTo(1);
    }
    // 딸린 테이블(점수 8줄 · 약점 2줄)도 에이 것은 남지 않고, 비이 것만 남습니다.
    assertThat(countCollectionRows("thinking_test_scores")).isEqualTo(8);
    assertThat(countCollectionRows("thinking_test_weaknesses")).isEqualTo(2);
  }

  @Test
  @DisplayName("탈퇴하면 심리검사(딸린 답 테이블까지)도 지워지고, 남의 것은 그대로다")
  void withdrawDeletesPsychTests() {
    for (Long userId : new Long[] {idOfA, idOfB}) {
      Map<PsychItem, Integer> answers = new EnumMap<>(PsychItem.class);
      for (PsychItem item : PsychItem.values()) {
        answers.put(item, 4);
      }
      entityManager.persist(new PsychTest(userId, NOW, answers));
    }
    entityManager.flush();
    callWith(tokenOfA);

    accountService.withdraw(new WithdrawRequest(PASSWORD, null));
    entityManager.flush();
    entityManager.clear();

    assertThat(countRows("PsychTest", idOfA)).isZero();
    assertThat(countRows("PsychTest", idOfB)).isEqualTo(1);
    // 딸린 답 테이블도 에이 것(35줄)은 남지 않고, 비이 것 35줄만 남습니다.
    assertThat(countCollectionRows("psych_test_answers")).isEqualTo(35);
  }

  // ---- 실제 DB 의 모든 테이블 확인 (이슈 #69 "모든 테이블에서 그 사용자의 행이 사라지는 테스트") ----

  // user_id 칸이 없는데도 탈퇴 때 지워지는 "딸린 테이블" 과, 그 줄이 가리키는 부모 테이블 · 부모를 가리키는 칸입니다.
  // 부모 줄(예: thinking_tests 의 한 줄)이 지워지면 같이 지워집니다.
  // ⚠️ user_id 가 없는 새 테이블을 만들면 아래 테스트가 실패합니다. 탈퇴 때 지워지는지 확인한 뒤 여기에 한 줄 더해 주세요.
  private static final Map<String, String[]> CHILD_TABLES =
      // 딸린 테이블이 10개를 넘어서 Map.of 대신 Map.ofEntries 를 씁니다. (Map.of 는 10쌍까지만 받습니다)
      Map.ofEntries(
          Map.entry("THINKING_TEST_SCORES", new String[] {"THINKING_TESTS", "THINKING_TEST_ID"}),
          Map.entry(
              "THINKING_TEST_WEAKNESSES", new String[] {"THINKING_TESTS", "THINKING_TEST_ID"}),
          // 온보딩 설문(onboarding_answers)에 매달린 "계획이 무너진 때" 와 "과목별 수준" (이슈 #21)
          Map.entry(
              "ONBOARDING_ANSWER_FAILURE_CAUSES",
              new String[] {"ONBOARDING_ANSWERS", "ONBOARDING_ANSWER_ID"}),
          Map.entry(
              "ONBOARDING_ANSWER_SUBJECT_LEVELS",
              new String[] {"ONBOARDING_ANSWERS", "ONBOARDING_ANSWER_ID"}),
          // 카드(cards)에 매달린 체크리스트와 예상 실패 원인 (이슈 #222)
          Map.entry("CARD_CHECKLIST_ITEMS", new String[] {"CARDS", "CARD_ID"}),
          Map.entry("CARD_EXPECTED_FAIL_CAUSES", new String[] {"CARDS", "CARD_ID"}),
          // 공부 기록(study_sessions)에 매달린 일시정지 · 체크한 항목 · 잘 된 이유 · 메모 (이슈 #224)
          Map.entry("SESSION_PAUSES", new String[] {"STUDY_SESSIONS", "STUDY_SESSION_ID"}),
          Map.entry("SESSION_CHECKED_ITEMS", new String[] {"STUDY_SESSIONS", "STUDY_SESSION_ID"}),
          Map.entry("SESSION_GOOD_REASONS", new String[] {"STUDY_SESSIONS", "STUDY_SESSION_ID"}),
          Map.entry("SESSION_MEMOS", new String[] {"STUDY_SESSIONS", "STUDY_SESSION_ID"}),
          // 목표(goals)에 매달린 "목표값을 고치기 전의 값" (이슈 #221)
          Map.entry("GOAL_TARGET_SNAPSHOTS", new String[] {"GOALS", "GOAL_ID"}),
          // 체크인(checkins)에 매달린 하루 첫 태그의 감정들 (이슈 #220)
          Map.entry("CHECKIN_EMOTIONS", new String[] {"CHECKINS", "CHECKIN_ID"}),
          // 일기(diaries)에 매달린 AI 가 뽑은 감정 · 원인 · 키워드 (이슈 #60)
          Map.entry("DIARY_EMOTIONS", new String[] {"DIARIES", "DIARY_ID"}),
          Map.entry("DIARY_CAUSES", new String[] {"DIARIES", "DIARY_ID"}),
          Map.entry("DIARY_KEYWORDS", new String[] {"DIARIES", "DIARY_ID"}),
          // 보여 준 인사이트(insights)에 매달린 근거 날짜 (이슈 #43)
          Map.entry("INSIGHT_EVIDENCE_DATES", new String[] {"INSIGHTS", "INSIGHT_ID"}),
          // 위험 예측(risk_forecasts)에 매달린 "위험하다고 본 이유" (이슈 #49)
          Map.entry("RISK_FORECAST_REASONS", new String[] {"RISK_FORECASTS", "RISK_FORECAST_ID"}),
          // 심리검사(psych_tests)에 매달린 문장 14개의 답 (이슈 #219)
          Map.entry("PSYCH_TEST_ANSWERS", new String[] {"PSYCH_TESTS", "PSYCH_TEST_ID"}),
          // 자기 실험(experiments)에 매달린 "실험 규칙을 붙인 카드" (이슈 #52)
          Map.entry("EXPERIMENT_CARDS", new String[] {"EXPERIMENTS", "EXPERIMENT_ID"}),
          // 변형 문제: 원본 문항 · 변형 문항에 매달린 선지 (docs/VARIANT-SPEC.md 4장)
          Map.entry(
              "SOURCE_PROBLEM_CHOICES", new String[] {"SOURCE_PROBLEMS", "SOURCE_PROBLEM_ID"}),
          Map.entry("VARIANT_CHOICES", new String[] {"VARIANTS", "VARIANT_ID"}));

  // 사람이 아니라 학교마다 한 벌인 공개 자료(NEIS 학사일정, 이슈 #282)입니다. 개인 정보가 없고
  // 같은 학교 학생들이 같이 쓰므로 탈퇴해도 지우지 않습니다.
  private static final Set<String> PUBLIC_TABLES = Set.of("SCHOOL_EVENTS", "SCHOOL_EVENT_FETCHES");

  @Test
  @DisplayName("탈퇴하면 실제 DB 의 모든 테이블에서 그 사람의 줄이 0 개다 (user_id 가 없는 새 테이블이 생기면 실패해서 알려 준다)")
  void everyTableInTheDbIsEmptiedForTheUser() {
    giveBothSomeRecords();
    for (Long userId : new Long[] {idOfA, idOfB}) {
      Map<CognitiveBias, Double> scores = new EnumMap<>(CognitiveBias.class);
      for (CognitiveBias bias : CognitiveBias.values()) {
        scores.put(bias, 4.0);
      }
      entityManager.persist(new ThinkingTest(userId, NOW, 2, scores, EnumSet.of(Weakness.GAME)));
      // 딸린 테이블(무너진 때 · 과목별 수준)에도 줄이 있어야, 아래 "부모 없이 남은 줄" 검사가 빈 테이블을 훑고 지나가지 않습니다.
      OnboardingAnswer.Values answer = new OnboardingAnswer.Values();
      answer.grade = Grade.HIGH_2;
      answer.targetExam = "수능";
      answer.targetExamDate = LocalDate.of(2027, 11, 18);
      answer.dailyAvailableMinutes = 180;
      answer.pastFailureCauses = EnumSet.of(FailureCause.WEEKEND);
      answer.subjectLevels = List.of(new SubjectLevel(1L, 2, null, 4));
      entityManager.persist(new OnboardingAnswer(userId, NOW, answer));
      Card card =
          new Card(
              userId,
              "수학",
              LocalDate.of(2026, 10, 12),
              60,
              List.of(new ChecklistItem("i1", "42쪽")),
              NOW);
      card.changeExpectedFailCauses(List.of("폰을 볼 것 같다"));
      entityManager.persist(card);
      // 카드로 켠 공부 기록 하나 (딸린 테이블 네 개에 한 줄씩)
      StudySession cardSession =
          new StudySession(
              userId,
              null,
              NOW.minusSeconds(7200),
              NOW.minusSeconds(3600),
              1,
              60_000,
              null,
              60,
              null,
              PlanKind.STUDY);
      cardSession.recordCard(card.getId(), SessionMode.NORMAL, false);
      cardSession.recordPauses(
          List.of(
              new SessionPause(
                  NOW.minusSeconds(7000), NOW.minusSeconds(6940), PauseTag.TOILET, null)));
      cardSession.recordEndReport(
          Map.of("i1", NOW.minusSeconds(4000)),
          null,
          null,
          null,
          null,
          EnumSet.of(GoodReason.FOCUS));
      cardSession.recordMemos(List.of(new SessionMemo(NOW.minusSeconds(5000), "메모")));
      entityManager.persist(cardSession);
      // 목표값을 한 번 고친 목표 (딸린 테이블 goal_target_snapshots 에 줄이 생깁니다)
      Goal goal =
          new Goal(
              userId,
              GoalKind.SCORE,
              new Goal.Target("수학 3등급", LocalDate.of(2026, 11, 19), "5등급", null, null),
              new Goal.Woop("자신감", "후회", "폰", "폰을 다른 방에"),
              NOW);
      goal.changeTarget(
          new Goal.Target("수학 4등급", LocalDate.of(2026, 11, 19), "5등급", null, null),
          "시간이 모자라서",
          NOW);
      entityManager.persist(goal);
      // 감정 태그가 붙은 체크인 (딸린 테이블 checkin_emotions 에 줄이 생깁니다)
      CheckIn checkIn =
          new CheckIn(
              userId,
              LocalDate.of(2026, 10, 15),
              CheckInSource.QUICK,
              3,
              null,
              null,
              null,
              null,
              null,
              null,
              null);
      checkIn.replaceEmotions(EnumSet.of(EmotionTag.STUCK, EmotionTag.TIRED));
      entityManager.persist(checkIn);
      // 카드에 붙인 자기 실험 (딸린 테이블 experiment_cards 에 줄이 생깁니다)
      entityManager.persist(
          new Experiment(
              userId,
              "폰 때문에 놓친다",
              null,
              ExperimentStrategy.PHONE_BAG,
              Set.of(card.getId()),
              LocalDate.of(2026, 10, 15),
              10,
              NOW));
      // 인사이트의 근거 날짜(딸린 테이블)에도 줄이 있어야 합니다.
      entityManager.persist(
          new Insight(
              userId,
              "오전에 더 오래 집중했어요",
              "morning-vs-night",
              false,
              LocalDate.of(2026, 10, 17),
              List.of(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 10))));
      // 위험 예측의 딸린 테이블(이유)도 같은 이유로 줄을 넣어 둡니다.
      entityManager.persist(
          new RiskForecast(
              userId,
              LocalDate.of(2026, 10, 12),
              RiskLevel.WATCH,
              EnumSet.of(RiskReason.LATE_SLEEP)));
      // 변형 문제: 객관식 원본과 변형의 선지(딸린 테이블)에도 줄을 넣어 둡니다.
      SourceProblem source =
          new SourceProblem(userId, "원본 문제", List.of("1", "2"), "1", "2", false, false, NOW, NOW);
      entityManager.persist(source);
      entityManager.persist(
          Variant.verified(
              userId,
              source.getId(),
              0,
              VariantFormat.MC5,
              "변형 문제",
              List.of(new VariantChoice("1", "1", ""), new VariantChoice("2", "2", "실수")),
              0,
              null,
              "1",
              "해설",
              "k = 1; k",
              "{}",
              "test-model",
              "variant_v2",
              1,
              NOW));
    }
    entityManager.flush();
    callWith(tokenOfA);

    accountService.withdraw(new WithdrawRequest(PASSWORD, null));
    entityManager.flush();
    entityManager.clear();

    // 비이의 감정 2줄만 남고, 에이의 감정은 체크인과 함께 지워집니다.
    assertThat(countCollectionRows("checkin_emotions")).isEqualTo(2);

    // information_schema 는 DB 가 자기 테이블 정보를 적어 두는 곳입니다. 엔티티 목록이 아니라 "실제로 만들어진 테이블" 을 봅니다.
    List<String> tables =
        nativeList(
            "select table_name from information_schema.tables where table_schema = 'PUBLIC'");
    assertThat(tables).contains("USERS", "ACCOUNTS", "SUBJECTS", "THINKING_TEST_SCORES");

    for (String table : tables) {
      if (table.equals("USERS")) {
        assertThat(nativeCount("select count(*) from users where id = :userId", idOfA))
            .as("users 의 에이 줄")
            .isZero();
      } else if (hasUserIdColumn(table)) {
        // 표 이름은 DB 가 알려 준 글자라 바깥에서 들어온 값이 아닙니다. 사용자 번호는 파라미터로 넘깁니다.
        assertThat(nativeCount("select count(*) from " + table + " where user_id = :userId", idOfA))
            .as("%s 에 남은 에이의 줄", table)
            .isZero();
      } else if (PUBLIC_TABLES.contains(table)) {
        // 학교 공개 자료라 그 사람의 줄이 따로 없습니다.
        continue;
      } else if (CHILD_TABLES.containsKey(table)) {
        String parent = CHILD_TABLES.get(table)[0];
        String parentIdColumn = CHILD_TABLES.get(table)[1];
        // 부모 줄이 없는데 남은 딸린 줄(고아)이 없어야 합니다.
        long orphans =
            ((Number)
                    entityManager
                        .createNativeQuery(
                            "select count(*) from "
                                + table
                                + " c where not exists (select 1 from "
                                + parent
                                + " p where p.id = c."
                                + parentIdColumn
                                + ")")
                        .getSingleResult())
                .longValue();
        assertThat(orphans).as("%s 에 부모 없이 남은 줄", table).isZero();
      } else {
        throw new AssertionError(
            "테이블 "
                + table
                + " 에 user_id 가 없어서 탈퇴 때 지워지는지 알 수 없어요. "
                + "user_id 를 넣거나, 딸린 테이블이면 CHILD_TABLES 에 더해 주세요.");
      }
    }
    // 남의 것은 그대로입니다.
    assertThat(nativeCount("select count(*) from subjects where user_id = :userId", idOfB))
        .isEqualTo(1);
  }

  private boolean hasUserIdColumn(String table) {
    return !nativeList(
            "select column_name from information_schema.columns where table_schema = 'PUBLIC'"
                + " and table_name = '"
                + table
                + "' and column_name = 'USER_ID'")
        .isEmpty();
  }

  @SuppressWarnings("unchecked")
  private List<String> nativeList(String sql) {
    return entityManager.createNativeQuery(sql).getResultList();
  }

  private long nativeCount(String sql, Long userId) {
    return ((Number)
            entityManager.createNativeQuery(sql).setParameter("userId", userId).getSingleResult())
        .longValue();
  }

  // 딸린 테이블(@ElementCollection)은 엔티티가 아니라서 SQL 로 줄 수를 셉니다. (표 이름은 우리가 정한 글자)
  private long countCollectionRows(String table) {
    return ((Number)
            entityManager.createNativeQuery("select count(*) from " + table).getSingleResult())
        .longValue();
  }
}
