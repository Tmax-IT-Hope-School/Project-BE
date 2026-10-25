package com.ithopeschool.projectbe.domain.quizresult;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 확인 문제(퀴즈) 결과 테이블. 한 줄 = 퀴즈 한 번(문제 묶음). (2026-10-24, 통계 탭)
// 설계는 학생이 정했습니다: 퀴즈 한 번은 한 단원만, 문제마다 걸린 시간은 한 칸에 숫자 목록("45,30,62")으로.
// 모든 칸이 꼭 있어야 합니다. (과목을 모르는 퀴즈는 저장하지 않음)
@Entity
@Table(
    name = "quiz_results",
    indexes =
        @Index(name = "idx_quiz_results_user_id_quiz_date", columnList = "user_id, quiz_date"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizResult {

  // 문제마다 걸린 시간을 한 칸에 적을 때 숫자 사이에 넣는 글자
  private static final String SECONDS_SEPARATOR = ",";

  // 줄을 구분하는 고유 번호
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 누가 푼 퀴즈인지 (users.id)
  @Column(nullable = false)
  private Long userId;

  // 어느 과목의 퀴즈인지 (subjects.id)
  @Column(nullable = false)
  private Long subjectId;

  // 어느 단원의 퀴즈인지. 예) 함수, 문학 (퀴즈 한 번 = 한 단원)
  @Column(nullable = false, length = 30)
  private String unit;

  // 퀴즈를 푼 날짜 (하루는 새벽 4시 기준, DayRule). date 는 DB 예약어라 quiz_date 로 씁니다.
  @Column(nullable = false)
  private LocalDate quizDate;

  // 맞은 문제 수
  @Column(nullable = false)
  private int correctCount;

  // 전체 문제 수
  @Column(nullable = false)
  private int totalCount;

  // 문제마다 걸린 시간(초)을 문제 순서대로 쉼표로 이은 글자. 예) "45,30,62"
  @Column(nullable = false, length = 200)
  private String questionSeconds;

  public QuizResult(
      Long userId,
      Long subjectId,
      String unit,
      LocalDate quizDate,
      int correctCount,
      int totalCount,
      List<Integer> questionSeconds) {
    this.userId = userId;
    this.subjectId = subjectId;
    this.unit = unit;
    this.quizDate = quizDate;
    this.correctCount = correctCount;
    this.totalCount = totalCount;
    this.questionSeconds = joinSeconds(questionSeconds);
  }

  // 문제마다 걸린 시간을 숫자 목록으로 돌려줍니다. "45,30,62" -> [45, 30, 62]
  public List<Integer> questionSecondsList() {
    List<Integer> seconds = new ArrayList<>();
    for (String part : questionSeconds.split(SECONDS_SEPARATOR)) {
      if (!part.isBlank()) {
        seconds.add(Integer.parseInt(part.trim()));
      }
    }
    return seconds;
  }

  // 걸린 시간을 모두 더한 값(초)
  public int totalSeconds() {
    int sum = 0;
    for (int second : questionSecondsList()) {
      sum += second;
    }
    return sum;
  }

  // [45, 30, 62] -> "45,30,62"
  private static String joinSeconds(List<Integer> seconds) {
    List<String> parts = new ArrayList<>();
    for (Integer second : seconds) {
      parts.add(String.valueOf(second));
    }
    return String.join(SECONDS_SEPARATOR, parts);
  }
}
