package com.ithopeschool.projectbe.domain.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 설정 바꾼 기록 테이블. 한 줄 = 설정 칸 하나를 한 번 바꾼 것. (이슈 #22 "언제 무엇을 바꿨는지 기록이 남는다")
//   예: 10/17 21:00 에 사용자(USER)가 독설 수위(maxHarshness)를 "3" 에서 "5" 로
// 한 번에 칸 두 개를 바꾸면 두 줄이 생깁니다. 처음 설정을 만들 때는 기록하지 않고, 바꿀 때만 기록합니다.
// 값은 숫자·시각·참거짓 등 모양이 제각각이라 글자로 적습니다. (예: "3", "23:00", "true")
@Entity
@Table(name = "setting_changes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettingChange {

  // 이전 값 · 새 값의 최대 글자 수 (잠금 대상 앱 목록을 "유튜브, 인스타그램" 처럼 이어 적어도 들어가는 길이)
  public static final int MAX_VALUE_LENGTH = 1000;

  // 기록 한 줄을 구분하는 고유 번호. 나중에 바꾼 것일수록 큽니다.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 설정을 바꾼 사람(users.id)
  @Column(nullable = false)
  private Long userId;

  // 바꾼 시각
  @Column(nullable = false)
  private Instant changedAt;

  // 바꾼 칸의 이름. API 의 JSON 이름과 같습니다. 예: "maxHarshness"
  @Column(nullable = false, length = 30)
  private String field;

  // 바꾸기 전 값 (글자)
  @Column(nullable = false, length = MAX_VALUE_LENGTH)
  private String oldValue;

  // 바꾼 뒤 값 (글자)
  @Column(nullable = false, length = MAX_VALUE_LENGTH)
  private String newValue;

  // 누가 바꿨는지 (TEST / DATA / USER). 이슈 #227 전의 옛 기록은 모두 사용자가 바꾼 것이라 USER 입니다.
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private ChangeSource source;

  public SettingChange(
      Long userId,
      Instant changedAt,
      String field,
      String oldValue,
      String newValue,
      ChangeSource source) {
    this.userId = userId;
    this.changedAt = changedAt;
    this.field = field;
    this.oldValue = oldValue;
    this.newValue = newValue;
    this.source = source;
  }
}
