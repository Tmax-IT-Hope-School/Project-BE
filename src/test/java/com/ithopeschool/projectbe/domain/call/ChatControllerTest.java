package com.ithopeschool.projectbe.domain.call;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ithopeschool.projectbe.domain.call.dto.ChatReplyResponse;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Controller 만 띄워서 요청 모양 검사와 응답 모양을 확인합니다. 진짜 로직(ChatService)은 가짜입니다. (이슈 #238)
@WebMvcTest(ChatController.class)
class ChatControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ChatService chatService;

  private ResultActions postJson(String url, String body) throws Exception {
    return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  @Test
  @DisplayName("채팅을 열면 200 과 채팅 번호 · AI 의 첫 말을 돌려준다 (요청 모양은 통화 시작과 같음)")
  void starts() throws Exception {
    given(chatService.start(any(), any(), any(), anyBoolean()))
        .willReturn(ChatReplyResponse.of(12L, "안녕! 어제는 어땠어?"));

    postJson("/api/v1/chats", "{\"careLevel\":2,\"maxHarshness\":3}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chatId").value("12"))
        .andExpect(jsonPath("$.reply").value("안녕! 어제는 어땠어?"))
        .andExpect(jsonPath("$.showHelp").value(false))
        .andExpect(jsonPath("$.helpLines").isEmpty());
  }

  @Test
  @DisplayName("학년 · 반을 주소에 실으면 그대로 넘긴다 (오늘 시간표를 대본에 넣으려고)")
  void startsWithClass() throws Exception {
    given(chatService.start(any(), eq(2), eq(1), eq(true)))
        .willReturn(ChatReplyResponse.of(12L, "안녕!"));

    mockMvc
        .perform(
            post("/api/v1/chats")
                .param("grade", "2")
                .param("classNo", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"careLevel\":2,\"maxHarshness\":3}"))
        .andExpect(status().isOk());
    verify(chatService).start(any(), eq(2), eq(1), eq(true));
  }

  @Test
  @DisplayName("greet=false 로 열면 첫 인사 없이 채팅 번호만 돌려준다 (reply 는 null, 채팅 수정 1)")
  void startsWithoutGreeting() throws Exception {
    given(chatService.start(any(), any(), any(), eq(false)))
        .willReturn(ChatReplyResponse.opened(12L));

    mockMvc
        .perform(
            post("/api/v1/chats")
                .param("greet", "false")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"careLevel\":2,\"maxHarshness\":3}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chatId").value("12"))
        .andExpect(jsonPath("$.reply").doesNotExist());
    verify(chatService).start(any(), any(), any(), eq(false));
  }

  @Test
  @DisplayName("위기 표현이면 showHelp 와 도움받을 곳(1388 · 109)이 담긴다")
  void crisisReplyHasHelpLines() throws Exception {
    given(chatService.send(eq(12L), any())).willReturn(ChatReplyResponse.crisis(12L, "안내"));

    postJson("/api/v1/chats/12/messages", "{\"lines\":[{\"speaker\":\"me\",\"text\":\"…\"}]}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.showHelp").value(true))
        .andExpect(jsonPath("$.helpLines[0].number").value("1388"))
        .andExpect(jsonPath("$.helpLines[1].number").value("109"));
  }

  @Test
  @DisplayName("대화가 비었거나 61줄 이상이거나 말한 사람이 틀리면 400 이고 로직을 부르지 않는다")
  void rejectsBadMessages() throws Exception {
    postJson("/api/v1/chats/12/messages", "{\"lines\":[]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("대화(lines)는 1 ~ 60줄까지 보낼 수 있어요"));

    String line = "{\"speaker\":\"me\",\"text\":\"응\"}";
    String sixtyOne = "{\"lines\":[" + String.join(",", Collections.nCopies(61, line)) + "]}";
    postJson("/api/v1/chats/12/messages", sixtyOne).andExpect(status().isBadRequest());

    postJson("/api/v1/chats/12/messages", "{\"lines\":[{\"speaker\":\"teacher\",\"text\":\"응\"}]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("speaker 는 ai 또는 me 예요"));

    // 다른 방 이야기(otherRooms)는 12줄 · 한 줄 200자까지 (2026-10-22)
    String thirteen = String.join(",", Collections.nCopies(13, "\"상담: 응\""));
    postJson(
            "/api/v1/chats/12/messages",
            "{\"lines\":[" + line + "],\"otherRooms\":[" + thirteen + "]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("다른 방 이야기(otherRooms)는 12줄까지 보낼 수 있어요"));
    String longLine = "\"" + "가".repeat(201) + "\"";
    postJson(
            "/api/v1/chats/12/messages",
            "{\"lines\":[" + line + "],\"otherRooms\":[" + longLine + "]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("다른 방 이야기 한 줄은 200자 이하로 보내 주세요"));

    verifyNoInteractions(chatService);
  }

  @Test
  @DisplayName("주제(about)와 함께 열 수 있고, 응답에 이유 값(tags)이 담긴다")
  void startsWithTopic() throws Exception {
    given(chatService.start(any(), any(), any(), anyBoolean()))
        .willReturn(
            ChatReplyResponse.withButtons(
                12L, "40분 했네! 어땠어?", Collections.emptyList(), null, List.of("phone")));

    postJson(
            "/api/v1/chats",
            "{\"careLevel\":2,\"maxHarshness\":3,\"about\":{\"kind\":\"SESSION_REVIEW\","
                + "\"lines\":[\"카드: 미적분 3단원 40분\"]}}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tags[0]").value("phone"));
  }

  @Test
  @DisplayName("주제 이름이 틀리거나 · 사실 줄이 없거나 · 9줄 이상이거나 · 한 줄이 150자를 넘으면 400 이고 로직을 부르지 않는다")
  void rejectsBadTopic() throws Exception {
    String base = "{\"careLevel\":2,\"maxHarshness\":3,\"about\":";
    postJson("/api/v1/chats", base + "{\"kind\":\"HOMEWORK\",\"lines\":[\"a\"]}}")
        .andExpect(status().isBadRequest());
    postJson("/api/v1/chats", base + "{\"kind\":\"CARD_FAIL\",\"lines\":[]}}")
        .andExpect(status().isBadRequest());
    postJson("/api/v1/chats", base + "{\"kind\":\"CARD_FAIL\",\"lines\":[\" \"]}}")
        .andExpect(status().isBadRequest());
    String nineLines = "\"a\",".repeat(8) + "\"a\"";
    postJson("/api/v1/chats", base + "{\"kind\":\"CARD_FAIL\",\"lines\":[" + nineLines + "]}}")
        .andExpect(status().isBadRequest());
    String longLine = "가".repeat(151);
    postJson("/api/v1/chats", base + "{\"kind\":\"CARD_FAIL\",\"lines\":[\"" + longLine + "\"]}}")
        .andExpect(status().isBadRequest());
    verifyNoInteractions(chatService);
  }

  @Test
  @DisplayName("방(room)은 COACH · PLANNER · TUTOR(와 예전 이름 GOAL_MATE)만 받고, 다른 글자면 400 이다")
  void checksRoom() throws Exception {
    given(chatService.start(any(), any(), any(), anyBoolean()))
        .willReturn(ChatReplyResponse.of(12L, "안녕!"));

    postJson("/api/v1/chats", "{\"careLevel\":2,\"maxHarshness\":3,\"room\":\"TUTOR\"}")
        .andExpect(status().isOk());
    postJson("/api/v1/chats", "{\"careLevel\":2,\"maxHarshness\":3,\"room\":\"PLANNER\"}")
        .andExpect(status().isOk());
    postJson("/api/v1/chats", "{\"careLevel\":2,\"maxHarshness\":3,\"room\":\"GOAL_MATE\"}")
        .andExpect(status().isOk());
    postJson("/api/v1/chats", "{\"careLevel\":2,\"maxHarshness\":3,\"room\":\"FRIEND\"}")
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("말 보내기에 사진(image)을 붙일 수 있고, 5,600,000자를 넘으면 400 이다")
  void sendsImage() throws Exception {
    given(chatService.send(eq(12L), any())).willReturn(ChatReplyResponse.of(12L, "어디까지 풀었어?"));

    postJson(
            "/api/v1/chats/12/messages",
            "{\"lines\":[{\"speaker\":\"me\",\"text\":\"이 문제\"}],"
                + "\"image\":\"data:image/jpeg;base64,AAAA\"}")
        .andExpect(status().isOk());
    String tooBig = "a".repeat(5_600_001);
    postJson(
            "/api/v1/chats/12/messages",
            "{\"lines\":[{\"speaker\":\"me\",\"text\":\"이 문제\"}],\"image\":\"" + tooBig + "\"}")
        .andExpect(status().isBadRequest());
    verify(chatService).send(eq(12L), any());
  }

  @Test
  @DisplayName("AI 의 긴 말(과목 쌤의 문제 목록 · 풀이)도 1000자까지는 다시 보낼 수 있고, 1001자면 400 이다 (2026-10-31)")
  void acceptsLongAiLineUpTo1000() throws Exception {
    given(chatService.send(eq(12L), any())).willReturn(ChatReplyResponse.of(12L, "다 맞았어!"));
    String me = "{\"speaker\":\"me\",\"text\":\"1번 무단 통치\"}";

    postJson(
            "/api/v1/chats/12/messages",
            "{\"lines\":[{\"speaker\":\"ai\",\"text\":\"" + "가".repeat(1000) + "\"}," + me + "]}")
        .andExpect(status().isOk());
    postJson(
            "/api/v1/chats/12/messages",
            "{\"lines\":[{\"speaker\":\"ai\",\"text\":\"" + "가".repeat(1001) + "\"}," + me + "]}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.message").value("자막 한 줄(text)은 1000자까지예요"));
    verify(chatService).send(eq(12L), any());
  }
}
