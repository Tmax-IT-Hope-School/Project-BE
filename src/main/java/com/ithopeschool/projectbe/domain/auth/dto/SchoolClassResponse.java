package com.ithopeschool.projectbe.domain.auth.dto;

import com.ithopeschool.projectbe.domain.auth.Account;

// "내 학년 · 반" 의 응답 모양입니다. 아직 안 골랐으면 둘 다 null 입니다.
//   { "grade": 2, "classNo": 1 }
public record SchoolClassResponse(Integer grade, Integer classNo) {

  public static SchoolClassResponse of(Account account) {
    return new SchoolClassResponse(account.getSchoolGrade(), account.getSchoolClassNo());
  }
}
