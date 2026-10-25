package com.ithopeschool.projectbe.domain.subject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.ithopeschool.projectbe.domain.subject.dto.SubjectCreateRequest;
import com.ithopeschool.projectbe.domain.subject.dto.SubjectResponse;
import com.ithopeschool.projectbe.domain.subject.dto.SubjectUpdateRequest;
import com.ithopeschool.projectbe.domain.user.User;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

// 과목 로직의 규칙을 하나씩 확인합니다. DB 는 가짜(Repository 를 흉내 낸 것)로 바꾸고 "어떻게 물었는지" 를 봅니다.
// 진짜 DB 에 넣고 꺼내는 확인은 SubjectRepositoryTest, 서버 전체 흐름은 SubjectFlowTest 가 합니다.
class SubjectServiceTest {

  // 지금 요청한 사람(나)의 번호
  private static final Long ME = 1L;

  private CurrentUserProvider currentUserProvider;
  private SubjectRepository subjectRepository;
  private SubjectService subjectService;
  private EntityManager entityManager;

  @BeforeEach
  void setUp() {
    currentUserProvider = mock(CurrentUserProvider.class);
    subjectRepository = mock(SubjectRepository.class);
    entityManager = mock(EntityManager.class);
    subjectService = new SubjectService(currentUserProvider, subjectRepository, entityManager);
    given(currentUserProvider.getCurrentUserId()).willReturn(ME);
  }

  // DB 가 번호를 매겨 준 것처럼 id 를 넣은 과목을 만듭니다.
  private static Subject subject(long id, long userId, String name, SubjectColor color) {
    Subject subject = new Subject(userId, name, color);
    ReflectionTestUtils.setField(subject, "id", id);
    return subject;
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
  @DisplayName("로그인하지 않았으면 네 가지 모두 401 이고, DB 는 건드리지 않는다")
  void everythingRequiresLogin() {
    given(currentUserProvider.getCurrentUserId())
        .willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요"));

    assertError(() -> subjectService.getSubjects(), ErrorCode.UNAUTHORIZED, "로그인이 필요해요");
    assertError(
        () -> subjectService.create(new SubjectCreateRequest("수학", "blue")),
        ErrorCode.UNAUTHORIZED,
        "로그인이 필요해요");
    assertError(
        () -> subjectService.update(1L, new SubjectUpdateRequest("국어", null)),
        ErrorCode.UNAUTHORIZED,
        "로그인이 필요해요");
    assertError(() -> subjectService.delete(1L), ErrorCode.UNAUTHORIZED, "로그인이 필요해요");
    verifyNoInteractions(subjectRepository);
  }

  // ---- 목록 ----

  @Test
  @DisplayName("목록은 내 userId 로만 찾고, FE 모양(id 글자 · 소문자 색)으로 바꿔 준다")
  void getSubjects() {
    given(subjectRepository.findByUserIdOrderById(ME))
        .willReturn(
            List.of(
                subject(3, ME, "수학", SubjectColor.BLUE),
                subject(5, ME, "영어", SubjectColor.PURPLE)));

    List<SubjectResponse> result = subjectService.getSubjects();

    assertThat(result)
        .containsExactly(
            new SubjectResponse("3", "수학", "blue"), new SubjectResponse("5", "영어", "purple"));
    verify(subjectRepository).findByUserIdOrderById(ME);
    verifyNoMoreInteractions(subjectRepository);
  }

  @Test
  @DisplayName("과목이 없으면 빈 목록")
  void getSubjectsEmpty() {
    given(subjectRepository.findByUserIdOrderById(ME)).willReturn(List.of());

    assertThat(subjectService.getSubjects()).isEmpty();
  }

  // ---- 추가 ----

  @Test
  @DisplayName("추가하면 내 userId 로 저장하고, 저장된 과목을 FE 모양으로 돌려준다")
  void create() {
    given(subjectRepository.existsByUserIdAndName(ME, "수학")).willReturn(false);
    given(subjectRepository.saveAndFlush(any(Subject.class)))
        .willAnswer(
            call -> {
              Subject toSave = call.getArgument(0);
              ReflectionTestUtils.setField(toSave, "id", 7L);
              return toSave;
            });

    SubjectResponse result = subjectService.create(new SubjectCreateRequest("수학", "blue"));

    assertThat(result).isEqualTo(new SubjectResponse("7", "수학", "blue"));
    ArgumentCaptor<Subject> captor = ArgumentCaptor.forClass(Subject.class);
    verify(subjectRepository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getUserId()).isEqualTo(ME);
    assertThat(captor.getValue().getName()).isEqualTo("수학");
    assertThat(captor.getValue().getColor()).isEqualTo(SubjectColor.BLUE);
  }

  @Test
  @DisplayName("같은 이름의 내 과목이 있으면 409 이고 저장하지 않는다")
  void createDuplicate() {
    given(subjectRepository.existsByUserIdAndName(ME, "수학")).willReturn(true);

    assertError(
        () -> subjectService.create(new SubjectCreateRequest("수학", "blue")),
        ErrorCode.CONFLICT,
        "이미 같은 이름의 과목이 있어요");
    verify(subjectRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("겹침 검사를 통과한 직후 같은 이름이 들어와 DB 가 막아도 500 이 아니라 409")
  void createRaceBecomes409() {
    given(subjectRepository.existsByUserIdAndName(ME, "수학")).willReturn(false);
    given(subjectRepository.saveAndFlush(any(Subject.class)))
        .willThrow(new DataIntegrityViolationException("uk_subjects_user_id_name"));

    assertError(
        () -> subjectService.create(new SubjectCreateRequest("수학", "blue")),
        ErrorCode.CONFLICT,
        "이미 같은 이름의 과목이 있어요");
  }

  @Test
  @DisplayName("다섯 가지 밖의 색이면 400 이고, 허용하는 색을 알려 주고, 저장하지 않는다")
  void createUnknownColor() {
    for (String bad : new String[] {"pink", "BLUE", " blue", "#ff0000", ""}) {
      assertError(
          () -> subjectService.create(new SubjectCreateRequest("수학", bad)),
          ErrorCode.VALIDATION_ERROR,
          "색은 red, orange, green, blue, purple 중에서 골라 주세요");
    }
    verify(subjectRepository, never()).saveAndFlush(any());
  }

  // ---- 바꾸기 ----

  @Test
  @DisplayName("이름만 보내면 이름만 바뀌고 색은 그대로다")
  void updateNameOnly() {
    Subject math = subject(3, ME, "수학", SubjectColor.BLUE);
    given(subjectRepository.findByIdAndUserId(3L, ME)).willReturn(Optional.of(math));
    given(subjectRepository.existsByUserIdAndName(ME, "국어")).willReturn(false);

    SubjectResponse result = subjectService.update(3L, new SubjectUpdateRequest("국어", null));

    assertThat(result).isEqualTo(new SubjectResponse("3", "국어", "blue"));
    assertThat(math.getName()).isEqualTo("국어");
    assertThat(math.getColor()).isEqualTo(SubjectColor.BLUE);
  }

  @Test
  @DisplayName("색만 보내면 색만 바뀌고, 이름이 겹치는지는 묻지도 않는다")
  void updateColorOnly() {
    Subject math = subject(3, ME, "수학", SubjectColor.BLUE);
    given(subjectRepository.findByIdAndUserId(3L, ME)).willReturn(Optional.of(math));

    SubjectResponse result = subjectService.update(3L, new SubjectUpdateRequest(null, "red"));

    assertThat(result).isEqualTo(new SubjectResponse("3", "수학", "red"));
    verify(subjectRepository, never()).existsByUserIdAndName(anyLong(), anyString());
  }

  @Test
  @DisplayName("이름과 색을 같이 보내면 둘 다 바뀐다")
  void updateBoth() {
    Subject math = subject(3, ME, "수학", SubjectColor.BLUE);
    given(subjectRepository.findByIdAndUserId(3L, ME)).willReturn(Optional.of(math));

    SubjectResponse result = subjectService.update(3L, new SubjectUpdateRequest("국어", "green"));

    assertThat(result).isEqualTo(new SubjectResponse("3", "국어", "green"));
  }

  @Test
  @DisplayName("지금과 같은 이름을 보내면(수학 → 수학) 겹침으로 보지 않고 통과한다")
  void updateToSameNameIsFine() {
    Subject math = subject(3, ME, "수학", SubjectColor.BLUE);
    given(subjectRepository.findByIdAndUserId(3L, ME)).willReturn(Optional.of(math));
    // 이름 검사를 한다면 "수학" 이 이미 있다고 답하는 DB 입니다. 그래도 통과해야 합니다.
    given(subjectRepository.existsByUserIdAndName(ME, "수학")).willReturn(true);

    SubjectResponse result = subjectService.update(3L, new SubjectUpdateRequest("수학", "red"));

    assertThat(result).isEqualTo(new SubjectResponse("3", "수학", "red"));
  }

  @Test
  @DisplayName("내 다른 과목과 같은 이름으로 바꾸려 하면 409 이고 이름은 그대로다")
  void updateToDuplicateName() {
    Subject math = subject(3, ME, "수학", SubjectColor.BLUE);
    given(subjectRepository.findByIdAndUserId(3L, ME)).willReturn(Optional.of(math));
    given(subjectRepository.existsByUserIdAndName(ME, "영어")).willReturn(true);

    assertError(
        () -> subjectService.update(3L, new SubjectUpdateRequest("영어", null)),
        ErrorCode.CONFLICT,
        "이미 같은 이름의 과목이 있어요");
    assertThat(math.getName()).isEqualTo("수학");
  }

  @Test
  @DisplayName("겹침 검사 직후 같은 이름이 들어와 DB 가 막아도 500 이 아니라 409")
  void updateRaceBecomes409() {
    Subject math = subject(3, ME, "수학", SubjectColor.BLUE);
    given(subjectRepository.findByIdAndUserId(3L, ME)).willReturn(Optional.of(math));
    given(subjectRepository.existsByUserIdAndName(ME, "영어")).willReturn(false);
    doThrow(new DataIntegrityViolationException("uk_subjects_user_id_name"))
        .when(subjectRepository)
        .flush();

    assertError(
        () -> subjectService.update(3L, new SubjectUpdateRequest("영어", null)),
        ErrorCode.CONFLICT,
        "이미 같은 이름의 과목이 있어요");
  }

  @Test
  @DisplayName("다섯 가지 밖의 색이면 400 이고, 같이 보낸 이름도 바뀌지 않는다")
  void updateUnknownColor() {
    Subject math = subject(3, ME, "수학", SubjectColor.BLUE);
    given(subjectRepository.findByIdAndUserId(3L, ME)).willReturn(Optional.of(math));

    assertError(
        () -> subjectService.update(3L, new SubjectUpdateRequest("국어", "pink")),
        ErrorCode.VALIDATION_ERROR,
        "색은 red, orange, green, blue, purple 중에서 골라 주세요");
    assertThat(math.getName()).isEqualTo("수학");
    assertThat(math.getColor()).isEqualTo(SubjectColor.BLUE);
  }

  @Test
  @DisplayName("이름도 색도 보내지 않으면 400")
  void updateNothing() {
    Subject math = subject(3, ME, "수학", SubjectColor.BLUE);
    given(subjectRepository.findByIdAndUserId(3L, ME)).willReturn(Optional.of(math));

    assertError(
        () -> subjectService.update(3L, new SubjectUpdateRequest(null, null)),
        ErrorCode.VALIDATION_ERROR,
        "바꿀 이름이나 색을 보내 주세요");
  }

  @Test
  @DisplayName("없는 번호이거나 남의 과목이면 똑같이 404 (내 userId 와 함께 찾기 때문)")
  void updateNotFound() {
    // 남의 과목(번호 4)은 "내 userId 와 번호" 로 찾으면 나오지 않습니다.
    given(subjectRepository.findByIdAndUserId(4L, ME)).willReturn(Optional.empty());

    assertError(
        () -> subjectService.update(4L, new SubjectUpdateRequest("국어", "red")),
        ErrorCode.NOT_FOUND,
        "과목을 찾을 수 없어요");
    verify(subjectRepository).findByIdAndUserId(4L, ME);
    verify(subjectRepository, never()).existsByUserIdAndName(anyLong(), anyString());
  }

  // ---- 삭제 ----

  @Test
  @DisplayName("계획도 공부 기록도 없는 내 과목은 지워진다")
  void deleteUnused() {
    Subject math = subject(3, ME, "수학", SubjectColor.BLUE);
    given(subjectRepository.findByIdAndUserId(3L, ME)).willReturn(Optional.of(math));
    given(subjectRepository.hasPlans(ME, 3L)).willReturn(false);
    given(subjectRepository.hasStudySessions(ME, 3L)).willReturn(false);

    subjectService.delete(3L);

    verify(subjectRepository).delete(math);
  }

  @Test
  @DisplayName("과목을 지우면 그 과목을 붙인 카드의 과목을 먼저 비운다 (카드는 지우지 않음, 이슈 #404)")
  void deleteClearsCardSubjectFirst() {
    Subject math = subject(3, ME, "수학", SubjectColor.BLUE);
    given(subjectRepository.findByIdAndUserId(3L, ME)).willReturn(Optional.of(math));

    subjectService.delete(3L);

    InOrder order = inOrder(subjectRepository);
    order.verify(subjectRepository).clearCardSubject(ME, 3L);
    order.verify(subjectRepository).delete(math);
  }

  @Test
  @DisplayName("계획 · 기록 · 루틴 때문에 못 지우면(409) 카드의 과목도 비우지 않는다")
  void conflictKeepsCardSubject() {
    given(subjectRepository.findByIdAndUserId(3L, ME))
        .willReturn(Optional.of(subject(3L, ME, "수학", SubjectColor.BLUE)));
    given(subjectRepository.hasStudySessions(ME, 3L)).willReturn(true);

    assertError(
        () -> subjectService.delete(3L), ErrorCode.CONFLICT, "이 과목으로 만든 계획이나 공부 기록이 있어서 지울 수 없어요");
    verify(subjectRepository, never()).clearCardSubject(anyLong(), anyLong());
  }

  @Test
  @DisplayName("이 과목으로 만든 계획이 있으면 409 이고 지우지 않는다")
  void deleteWithPlans() {
    Subject math = subject(3, ME, "수학", SubjectColor.BLUE);
    given(subjectRepository.findByIdAndUserId(3L, ME)).willReturn(Optional.of(math));
    given(subjectRepository.hasPlans(ME, 3L)).willReturn(true);

    assertError(
        () -> subjectService.delete(3L), ErrorCode.CONFLICT, "이 과목으로 만든 계획이나 공부 기록이 있어서 지울 수 없어요");
    verify(subjectRepository, never()).delete(any(Subject.class));
  }

  @Test
  @DisplayName("이 과목으로 공부한 기록이 있으면(계획이 없어도) 409 이고 지우지 않는다")
  void deleteWithSessions() {
    Subject math = subject(3, ME, "수학", SubjectColor.BLUE);
    given(subjectRepository.findByIdAndUserId(3L, ME)).willReturn(Optional.of(math));
    given(subjectRepository.hasPlans(ME, 3L)).willReturn(false);
    given(subjectRepository.hasStudySessions(ME, 3L)).willReturn(true);

    assertError(
        () -> subjectService.delete(3L), ErrorCode.CONFLICT, "이 과목으로 만든 계획이나 공부 기록이 있어서 지울 수 없어요");
    verify(subjectRepository, never()).delete(any(Subject.class));
  }

  @Test
  @DisplayName("없는 번호이거나 남의 과목이면 404 이고, 계획·기록을 묻지도 지우지도 않는다")
  void deleteNotFound() {
    given(subjectRepository.findByIdAndUserId(4L, ME)).willReturn(Optional.empty());

    assertError(() -> subjectService.delete(4L), ErrorCode.NOT_FOUND, "과목을 찾을 수 없어요");
    verify(subjectRepository, never()).hasPlans(anyLong(), anyLong());
    verify(subjectRepository, never()).hasStudySessions(anyLong(), anyLong());
    verify(subjectRepository, never()).delete(any(Subject.class));
  }

  @Test
  void deleteWithRoutine() {
    given(subjectRepository.findByIdAndUserId(3L, ME))
        .willReturn(Optional.of(subject(3L, ME, "수학", SubjectColor.BLUE)));
    given(subjectRepository.hasRoutines(ME, 3L)).willReturn(true);
    assertError(() -> subjectService.delete(3L), ErrorCode.CONFLICT, "이 과목으로 만든 루틴이 있어서 지울 수 없어요");
    verify(subjectRepository, never()).delete(any());
    InOrder order = inOrder(entityManager, subjectRepository);
    order.verify(entityManager).find(User.class, ME, LockModeType.PESSIMISTIC_WRITE);
    order.verify(subjectRepository).findByIdAndUserId(3L, ME);
    order.verify(subjectRepository).hasPlans(ME, 3L);
    order.verify(subjectRepository).hasStudySessions(ME, 3L);
    order.verify(subjectRepository).hasRoutines(ME, 3L);
  }
}
