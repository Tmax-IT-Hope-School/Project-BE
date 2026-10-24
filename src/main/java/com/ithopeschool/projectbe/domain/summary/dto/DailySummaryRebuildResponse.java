package com.ithopeschool.projectbe.domain.summary.dto;

// 지난 날짜의 하루 요약을 다시 만들었을 때의 응답 모양(DTO)입니다.
// date: 다시 만든 날짜 'YYYY-MM-DD' / userCount: 요약을 만든 사용자 수 / message: 사람이 읽을 한국어 설명
public record DailySummaryRebuildResponse(String date, int userCount, String message) {}
