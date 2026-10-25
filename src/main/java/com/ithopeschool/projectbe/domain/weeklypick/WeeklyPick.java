package com.ithopeschool.projectbe.domain.weeklypick;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// "이번 주에 고른 것" 테이블. 한 줄 = 한 사람이 한 주에 해 보기로 고른 것 하나. (이슈 #406)
// 주간 리포트 아래 [다음 주에 하나만 해 볼까요?] 에서 고른 것입니다. (FE 의 src/lib/weeklyPicks.ts 와 같은 칸)
// 한 사람의 한 주(월요일)에는 한 줄만 있습니다. 다시 고르면 같은 줄을 덮어씁니다. (user_id, week_start 는 겹칠 수 없음)
//
// '지켰어?' 를 따로 적지 않습니다. 그 주에 해냈는지는 FE 가 타이머 기록으로 봅니다.
@Entity
@Table(
    name = "weekly_picks",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_weekly_picks_user_id_week_start",
            columnNames = {"user_id", "week_start"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeeklyPick {

  // 해 볼 것(title) · 한 줄 설명(hint)의 최대 글자 수 (DB 칸 길이와 같은 값)
  public static final int MAX_TEXT_LENGTH = 60;

  // 줄을 구분하는 고유 번호. DB 가 1, 2, 3 ... 순서로 만들어 줍니다.
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 이 줄의 주인(users.id). 다른 사람의 것이 섞이지 않게 하는 기준입니다.
  @Column(nullable = false)
  private Long userId;

  // 이 방법을 해 보는 주. 그 주의 월요일 날짜입니다.
  @Column(nullable = false)
  private LocalDate weekStart;

  // 해 볼 것 한 줄 (예: 폰을 다른 방에 두기)
  @Column(nullable = false, length = MAX_TEXT_LENGTH)
  private String title;

  // 왜 이걸 하는지 짧게 (예: 손이 안 닿으면 덜 멈춰요)
  @Column(nullable = false, length = MAX_TEXT_LENGTH)
  private String hint;

  // true 면 "이번 주는 넘어가기" 를 고른 것 (해 볼 것이 없는 주)
  @Column(nullable = false)
  private boolean skip;

  // 학생이 고른 시각. FE 가 보낸 값이고, 안 보냈으면 서버가 저장한 시각입니다.
  @Column(nullable = false)
  private Instant decidedAt;

  // 이 줄을 처음 만든 시각
  @Column(nullable = false)
  private Instant createdAt;

  // 마지막으로 덮어쓴 시각. 처음 만들 때는 createdAt 과 같습니다.
  @Column(nullable = false)
  private Instant updatedAt;

  public WeeklyPick(
      Long userId,
      LocalDate weekStart,
      String title,
      String hint,
      boolean skip,
      Instant decidedAt,
      Instant now) {
    this.userId = userId;
    this.weekStart = weekStart;
    this.createdAt = now;
    change(title, hint, skip, decidedAt, now);
  }

  // 같은 주에 다시 고르면 새로 고른 것으로 바꿉니다. 처음 만든 시각(createdAt)은 그대로 둡니다.
  public void change(String title, String hint, boolean skip, Instant decidedAt, Instant now) {
    this.title = title;
    this.hint = hint;
    this.skip = skip;
    this.decidedAt = decidedAt;
    this.updatedAt = now;
  }
}
