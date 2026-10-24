package com.ithopeschool.projectbe.domain.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 설정 근거 테이블. 한 줄 = 사용자 한 명의 설정 칸 하나의 근거. (이슈 #227)
//   예: 1번 사용자의 dailyCount 는 USER 가 정함, 이유 "내가 정했어요", 잠김
// 값 자체는 care_settings 에 있고, 여기에는 "누가 · 왜 · 잠겼나" 만 적습니다.
// 아무도 정하지 않은 칸(처음 값 그대로)은 줄이 없습니다. 그때 응답의 source 는 null 입니다.
// 한 사용자의 같은 칸은 한 줄뿐입니다. 다시 정하면 그 줄을 고칩니다.
@Entity
@Table(
    name = "setting_sources",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_setting_sources_user_id_field",
            columnNames = {"user_id", "field"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettingSource {

  // 이유 한 줄의 최대 글자 수
  public static final int MAX_REASON_LENGTH = 100;

  // 한 줄을 구분하는 고유 번호. DB 가 1, 2, 3 ... 순서로 만들어 줍니다.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 이 근거의 주인(users.id)
  @Column(nullable = false)
  private Long userId;

  // 설정 칸 이름. API 의 JSON 이름과 같습니다. 예: "maxHarshness" (setting_changes.field 와 같은 이름)
  @Column(nullable = false, length = 30)
  private String field;

  // 누가 정했는지 (TEST / DATA / USER)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private ChangeSource source;

  // 왜 이 값인지 한 줄. 예: "시작 저항이 높게 나와서 켰어요", 사용자가 정하면 "내가 정했어요"
  @Column(nullable = false, length = MAX_REASON_LENGTH)
  private String reason;

  // 잠겼으면 true. 사용자가 직접 바꾼 칸은 잠기고, AI(자동 매핑)는 다시 바꾸지 않습니다.
  @Column(nullable = false)
  private boolean locked;

  public SettingSource(
      Long userId, String field, ChangeSource source, String reason, boolean locked) {
    this.userId = userId;
    this.field = field;
    this.source = source;
    this.reason = reason;
    this.locked = locked;
  }

  // 같은 칸을 다시 정했을 때 근거를 고칩니다.
  public void change(ChangeSource source, String reason, boolean locked) {
    this.source = source;
    this.reason = reason;
    this.locked = locked;
  }
}
