package com.ithopeschool.projectbe.domain.card;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
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

// 서버 전체를 진짜로 켜고(가짜 없이) 카드의 과목(이슈 #404)을 처음부터 끝까지 따라가 봅니다.
//   과목 만들기 → 과목을 붙여 카드 만들기 → 과목 없이 카드로 공부 기록 저장(카드의 과목이 채워짐)
//   → 기록이 없는 과목을 지우면 카드의 과목만 비워짐 → 남의 과목은 붙일 수 없음
// 끝나면 두 사람을 탈퇴시켜서 만든 데이터를 모두 지웁니다.
@SpringBootTest
@AutoConfigureMockMvc
class CardSubjectFlowTest {

  private static final String PASSWORD = "study2026";
  private static final String CARD =
      "{\"title\":\"수학 문제집\",\"date\":\"2026-10-12\",\"startTime\":\"19:00\","
          + "\"durationMinutes\":60,\"checklist\":[{\"text\":\"42쪽\"}],\"subjectId\":\"%s\"}";

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("카드에 붙인 과목이 카드 공부 기록에 채워지고, 과목을 지우면 카드의 과목만 비워지고, 남의 과목은 404")
  void cardSubjectFromStartToEnd() throws Exception {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String token = signupAndLogin("card-subject-a-" + suffix + "@starflow.com", "에이에이");
    String other = signupAndLogin("card-subject-b-" + suffix + "@starflow.com", "비이비이");
    try {
      String math = createSubject(token, "수학");
      String english = createSubject(token, "영어");

      // 1) 과목을 붙여 카드를 만들면 응답 · 목록에 subjectId 가 글자로 나옵니다.
      String mathCard = createCard(token, math);
      send(get("/api/v1/cards"), token, null)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].id").value(mathCard))
          .andExpect(jsonPath("$[0].subjectId").value(math));

      // 2) 과목 없이 그 카드로 켠 공부 기록은 카드의 과목으로 저장됩니다.
      Instant end = Instant.now().minusSeconds(3600);
      Instant start = end.minusSeconds(3600);
      String session =
          "{\"subjectId\":\"\",\"startedAt\":%d,\"endedAt\":%d,\"pauseCount\":0,\"kind\":\"study\",\"cardId\":\"%s\"}"
              .formatted(start.toEpochMilli(), end.toEpochMilli(), mathCard);
      send(post("/api/v1/study-sessions"), token, session)
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.subjectId").value(math))
          .andExpect(jsonPath("$.cardId").value(mathCard));

      // 3) 기록이 생긴 과목은 지금처럼 지울 수 없습니다. (409)
      send(delete("/api/v1/subjects/" + math), token, null).andExpect(status().isConflict());

      // 4) 기록이 없는 과목을 지우면 카드는 남고 카드의 과목만 비워집니다.
      String englishCard = createCard(token, english);
      send(delete("/api/v1/subjects/" + english), token, null).andExpect(status().isNoContent());
      send(get("/api/v1/cards"), token, null)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(2))
          .andExpect(jsonPath("$[1].id").value(englishCard))
          .andExpect(jsonPath("$[1].subjectId").isEmpty());

      // 5) 남의 과목은 만들기 · 바꾸기 모두 404 이고, 바꾸기에서 "" 를 보내면 과목이 비워집니다.
      String othersSubject = createSubject(other, "국어");
      send(post("/api/v1/cards"), token, CARD.formatted(othersSubject))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.error.message").value("과목을 찾을 수 없어요"));
      send(patch("/api/v1/cards/" + mathCard), token, "{\"subjectId\":\"" + othersSubject + "\"}")
          .andExpect(status().isNotFound());
      send(patch("/api/v1/cards/" + mathCard), token, "{\"subjectId\":\"\"}")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.subjectId").isEmpty());
    } finally {
      withdraw(token);
      withdraw(other);
    }
  }

  private String createSubject(String token, String name) throws Exception {
    ResultActions result =
        send(
            post("/api/v1/subjects"), token, "{\"name\":\"%s\",\"color\":\"red\"}".formatted(name));
    result.andExpect(status().isCreated());
    return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
  }

  private String createCard(String token, String subjectId) throws Exception {
    ResultActions result = send(post("/api/v1/cards"), token, CARD.formatted(subjectId));
    result.andExpect(status().isCreated()).andExpect(jsonPath("$.subjectId").value(subjectId));
    return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
  }

  // 새 사용자를 가입시키고 로그인해서 토큰을 돌려줍니다.
  private String signupAndLogin(String email, String nickname) throws Exception {
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
    send(post("/api/v1/auth/signup"), null, body).andExpect(status().isCreated());
    ResultActions login =
        send(
            post("/api/v1/auth/login"),
            null,
            "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD));
    login.andExpect(status().isOk());
    return JsonPath.read(login.andReturn().getResponse().getContentAsString(), "$.token");
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
