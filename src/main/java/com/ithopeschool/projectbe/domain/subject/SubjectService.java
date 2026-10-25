package com.ithopeschool.projectbe.domain.subject;

import com.ithopeschool.projectbe.domain.subject.dto.SubjectCreateRequest;
import com.ithopeschool.projectbe.domain.subject.dto.SubjectResponse;
import com.ithopeschool.projectbe.domain.subject.dto.SubjectUpdateRequest;
import com.ithopeschool.projectbe.domain.user.User;
import com.ithopeschool.projectbe.global.auth.CurrentUserProvider;
import com.ithopeschool.projectbe.global.exception.ApiException;
import com.ithopeschool.projectbe.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 과목 목록·추가·수정·삭제 로직입니다. (이슈 #10, docs/API.md "과목")
//
// 모든 메서드는 currentUserProvider.getCurrentUserId() 로 시작합니다. 그래서 로그인하지 않은 요청은 401 이 나가고,
// 모든 조회·수정에 "내 userId" 조건이 붙어서 남의 과목은 보이지도, 바뀌지도 않습니다.
// (남의 과목 번호를 보내면 "그런 번호가 없다" 는 것과 똑같이 404 로 답합니다)
@Service
@RequiredArgsConstructor
public class SubjectService {

  private final CurrentUserProvider currentUserProvider;
  private final SubjectRepository subjectRepository;
  private final EntityManager entityManager;

  // 내 과목 목록 (만든 순서). 하나도 없으면 빈 목록입니다.
  @Transactional(readOnly = true)
  public List<SubjectResponse> getSubjects() {
    Long userId = currentUserProvider.getCurrentUserId();
    return subjectRepository.findByUserIdOrderById(userId).stream()
        .map(SubjectResponse::from)
        .toList();
  }

  // 과목 추가: 이름이 내 과목 중에 없어야 하고, 색은 다섯 가지 중 하나여야 합니다.
  @Transactional
  public SubjectResponse create(SubjectCreateRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    SubjectColor color = parseColor(request.color());

    if (subjectRepository.existsByUserIdAndName(userId, request.name())) {
      throw duplicateName();
    }

    try {
      Subject saved = subjectRepository.saveAndFlush(new Subject(userId, request.name(), color));
      return SubjectResponse.from(saved);
    } catch (DataIntegrityViolationException e) {
      // 위에서 확인한 직후에 같은 이름이 한 번 더 들어온 경우입니다. (추가 버튼을 빠르게 두 번 누름 등)
      // DB 의 "같은 사용자 안에서 이름 중복 불가" 규칙(uk_subjects_user_id_name)이 막아 준 것이라 같은 답을 줍니다.
      throw duplicateName();
    }
  }

  // 과목 바꾸기: 보낸 것만 바꿉니다. 이름을 바꾸면 내 다른 과목과 겹치지 않아야 합니다.
  @Transactional
  public SubjectResponse update(Long id, SubjectUpdateRequest request) {
    Long userId = currentUserProvider.getCurrentUserId();
    Subject subject = findMySubject(userId, id);

    if (request.name() == null && request.color() == null) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "바꿀 이름이나 색을 보내 주세요");
    }

    // 색을 먼저 검사합니다. 틀린 색이면 이름도 바뀌지 않습니다.
    if (request.color() != null) {
      subject.changeColor(parseColor(request.color()));
    }

    // 지금 이름과 같은 이름을 보내면 바꿀 것이 없으니 겹침 검사도 하지 않습니다. ("수학" 을 "수학" 으로)
    if (request.name() != null && !request.name().equals(subject.getName())) {
      if (subjectRepository.existsByUserIdAndName(userId, request.name())) {
        throw duplicateName();
      }
      subject.changeName(request.name());
    }

    // 엔티티의 값을 바꾸기만 하면 이 메서드가 끝날 때 JPA 가 DB 에 반영합니다.
    // 여기서 미리 반영해 보는 이유: 겹침 검사 직후에 같은 이름이 들어오는 경우를 지금 잡아서 409 로 답하려는 것입니다.
    try {
      subjectRepository.flush();
    } catch (DataIntegrityViolationException e) {
      throw duplicateName();
    }
    return SubjectResponse.from(subject);
  }

  // 과목 삭제: 이 과목으로 만든 계획이나 공부 기록이 있으면 지울 수 없습니다. (409)
  //
  // 왜 막나요? 계획·기록은 과목 번호(subjectId)로 과목을 가리킵니다. 과목만 지우면 가리킬 곳이 없어져서
  // 화면에서 "무슨 과목인지 모르는 기록" 이 됩니다. 기록을 같이 지우면 학생의 공부 기록이 사라집니다.
  // 지금 쓰는 과목이라도 이름이나 색은 바꿀 수 있습니다. (이 정책은 docs/API.md "과목" 에 적혀 있습니다)
  //
  // 카드는 막지 않습니다. 이 과목을 붙인 카드는 지우지 않고 과목만 비웁니다. ("과목 없음", 이슈 #404)
  @Transactional
  public void delete(Long id) {
    Long userId = currentUserProvider.getCurrentUserId();
    // 루틴 생성과 같은 사용자 행을 잠가서 검사 직후 과목이 삭제되는 경쟁을 막습니다.
    entityManager.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE);
    Subject subject = findMySubject(userId, id);

    if (subjectRepository.hasPlans(userId, id) || subjectRepository.hasStudySessions(userId, id)) {
      throw new ApiException(ErrorCode.CONFLICT, "이 과목으로 만든 계획이나 공부 기록이 있어서 지울 수 없어요");
    }
    if (subjectRepository.hasRoutines(userId, id)) {
      throw new ApiException(ErrorCode.CONFLICT, "이 과목으로 만든 루틴이 있어서 지울 수 없어요");
    }
    subjectRepository.clearCardSubject(userId, id);
    subjectRepository.delete(subject);
  }

  // 내 과목 중에서 번호로 찾습니다. 없는 번호이거나 남의 과목이면 똑같이 404 입니다.
  private Subject findMySubject(Long userId, Long id) {
    return subjectRepository
        .findByIdAndUserId(id, userId)
        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "과목을 찾을 수 없어요"));
  }

  // FE 가 보낸 색 글자("blue")를 색으로 바꿉니다. 다섯 가지가 아니면 400 입니다.
  private SubjectColor parseColor(String value) {
    SubjectColor color = SubjectColor.fromValue(value);
    if (color == null) {
      // 허용하는 색 목록은 enum 에서 가져와서, 색이 늘어나도 이 문장이 저절로 맞춰집니다.
      String allowed =
          Arrays.stream(SubjectColor.values())
              .map(SubjectColor::toValue)
              .collect(Collectors.joining(", "));
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "색은 " + allowed + " 중에서 골라 주세요");
    }
    return color;
  }

  private ApiException duplicateName() {
    return new ApiException(ErrorCode.CONFLICT, "이미 같은 이름의 과목이 있어요");
  }
}
