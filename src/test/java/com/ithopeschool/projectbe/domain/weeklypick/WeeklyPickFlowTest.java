package com.ithopeschool.projectbe.domain.weeklypick;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// 서버 전체를 진짜로 켜고(가짜 없이) 고르기 → 다시 고르기(덮어쓰기) → 목록 → 탈퇴를 이어서 해 봅니다. (이슈 #406)
// 새 사용자 두 명(에이·비이)을 가입시켜서 "남의 것은 목록에 안 보이는지", "탈퇴하면 같이 지워지는지" 를 봅니다.
@SpringBootTest
@AutoConfigureMockMvc
class WeeklyPickFlowTest {

  private static final String PASSWORD = "study2026";
  private static final LocalDate MONDAY = LocalDate.parse("2026-10-26");

  @Autowired private MockMvc mockMvc;
  @Autowired private WeeklyPickRepository weeklyPickRepository;

  @Test
  @DisplayName("같은 주에 다시 고르면 덮어쓰고, 남의 목록에는 안 보이고, 탈퇴하면 같이 지워진다")
  void pickOverwriteListWithdraw() throws Exception {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    Long userIdA = signup("pick-a-" + suffix + "@starflow.com", "에이에이");
    signup("pick-b-" + suffix + "@starflow.com", "비이비이");
    String tokenA = login("pick-a-" + suffix + "@starflow.com");
    String tokenB = login("pick-b-" + suffix + "@starflow.com");

    // 에이가 고르고, 마음을 바꿔 같은 주에 다시 고릅니다. 번호는 그대로입니다.
    ResultActions first =
        send(
            put("/api/v1/weekly-picks/" + MONDAY),
            tokenA,
            "{\"title\":\"폰을 다른 방에 두기\",\"hint\":\"손이 안 닿으면 덜 멈춰요\",\"skip\":false,\"decidedAt\":1792800000000}");
    first.andExpect(status().isOk()).andExpect(jsonPath("$.decidedAt").value(1792800000000L));
    String id = JsonPath.read(first.andReturn().getResponse().getContentAsString(), "$.id");

    send(
            put("/api/v1/weekly-picks/" + MONDAY),
            tokenA,
            "{\"title\":\"이번 주는 넘어가기\",\"hint\":\"괜찮아요. 다음 리포트에서 다시 골라요\",\"skip\":true}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.skip").value(true));

    // 에이의 목록에는 덮어쓴 한 줄만, 비이의 목록에는 아무것도 없습니다.
    String period = "?from=" + MONDAY.minusWeeks(4) + "&to=" + MONDAY;
    send(get("/api/v1/weekly-picks" + period), tokenA, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(id))
        .andExpect(jsonPath("$[0].weekStart").value(MONDAY.toString()))
        .andExpect(jsonPath("$[0].title").value("이번 주는 넘어가기"));
    send(get("/api/v1/weekly-picks" + period), tokenB, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    // 월요일이 아니면 400 입니다.
    send(
            put("/api/v1/weekly-picks/" + MONDAY.plusDays(2)),
            tokenA,
            "{\"title\":\"해 볼 것\",\"hint\":\"설명\",\"skip\":false}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("weekStart 는 그 주의 월요일 날짜로 보내 주세요"));

    // 탈퇴하면 에이가 고른 것도 같이 지워집니다. (AccountService.withdraw 가 userId 가 있는 모든 테이블을 지움)
    withdraw(tokenA);
    withdraw(tokenB);
    assertThat(weeklyPickRepository.findByUserIdAndWeekStart(userIdA, MONDAY)).isEmpty();
  }

  private Long signup(String email, String nickname) throws Exception {
    String body =
        """
        {
          "email": "%s",
          "password": "%s",
          "name": "김티맥",
          "nickname": "%s",
          "schoolCode": null,
          "schoolName": null,
          "consents": { "terms": true, "privacy": true, "diaryAnalysis": false, "over14": true, "version": "v1" }
        }
        """
            .formatted(email, PASSWORD, nickname);
    ResultActions result = send(post("/api/v1/auth/signup"), null, body);
    result.andExpect(status().isCreated());
    return Long.valueOf(
        JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id"));
  }

  private String login(String email) throws Exception {
    ResultActions result =
        send(
            post("/api/v1/auth/login"),
            null,
            "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD));
    result.andExpect(status().isOk());
    return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.token");
  }

  private void withdraw(String token) throws Exception {
    send(post("/api/v1/auth/withdraw"), token, "{\"password\":\"" + PASSWORD + "\"}")
        .andExpect(status().isNoContent());
  }

  // 이 토큰을 Authorization 헤더에 붙이고(null 이면 로그인 안 한 요청), 본문이 있으면 JSON 으로 담아 부릅니다.
  private ResultActions send(MockHttpServletRequestBuilder builder, String token, String body)
      throws Exception {
    if (token != null) {
      builder.header("Authorization", "Bearer " + token);
    }
    if (body != null) {
      builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }
    return mockMvc.perform(builder);
  }
}
