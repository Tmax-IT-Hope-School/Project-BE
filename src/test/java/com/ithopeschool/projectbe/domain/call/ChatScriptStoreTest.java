package com.ithopeschool.projectbe.domain.call;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 상담 채팅의 대본을 기억해 두는 곳의 테스트입니다.
class ChatScriptStoreTest {

  private static final Instant NOW = Instant.parse("2026-10-17T09:00:00Z");

  // 지금 시각을 바꿀 수 있는 시계 (2시간이 지난 뒤를 흉내 냅니다)
  private static final class MovableClock extends Clock {
    private Instant now = NOW;

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  private final MovableClock clock = new MovableClock();
  private final ChatScriptStore store = new ChatScriptStore(clock);

  @Test
  @DisplayName("내 채팅의 대본만 꺼낼 수 있다 (남의 번호로는 비어 있음)")
  void onlyTheOwnerCanRead() {
    store.save(12L, 7L, "대본");

    assertThat(store.find(12L, 7L)).contains("대본");
    assertThat(store.find(12L, 8L)).isEmpty();
    assertThat(store.find(13L, 7L)).isEmpty();
  }

  @Test
  @DisplayName("2시간이 지나면 잊고, 지우면 바로 비어 있다")
  void expiresAndRemoves() {
    store.save(12L, 7L, "대본");
    clock.now = NOW.plus(ChatScriptStore.KEEP);
    assertThat(store.find(12L, 7L)).contains("대본");

    clock.now = NOW.plus(ChatScriptStore.KEEP).plusSeconds(1);
    assertThat(store.find(12L, 7L)).isEmpty();

    store.save(13L, 7L, "다른 대본");
    store.remove(13L);
    assertThat(store.find(13L, 7L)).isEmpty();
  }

  @Test
  @DisplayName("주제와 함께 기억하고, 대본만 바꿔도 주제는 그대로다 (남의 번호로는 비어 있음)")
  void remembersTopic() {
    store.save(12L, 7L, "대본", ChatTopic.CARD_FAIL);
    store.save(12L, 7L, "대본 + 위기 칸");

    assertThat(store.find(12L, 7L)).contains("대본 + 위기 칸");
    assertThat(store.findTopic(12L, 7L)).contains(ChatTopic.CARD_FAIL);
    assertThat(store.findTopic(12L, 8L)).isEmpty();
  }

  @Test
  @DisplayName("주제 없이 연 채팅은 주제가 비어 있다")
  void noTopic() {
    store.save(12L, 7L, "대본");

    assertThat(store.findTopic(12L, 7L)).isEmpty();
  }

  @Test
  @DisplayName("방을 기억하고, 대본만 바꿔도 방은 그대로다. 방을 안 주면 코치다")
  void remembersRoom() {
    store.save(12L, 7L, "대본", null, ChatRoom.TUTOR);
    store.save(12L, 7L, "대본 + 위기 칸");
    store.save(13L, 7L, "코치 대본", null);

    assertThat(store.findRoom(12L, 7L)).contains(ChatRoom.TUTOR);
    assertThat(store.findRoom(13L, 7L)).contains(ChatRoom.COACH);
    assertThat(store.findRoom(14L, 7L)).isEmpty();
  }

  @Test
  @DisplayName("[＋ 새 대화] 표시를 기억하고, 대본만 바꿔도 그대로다. 안 주거나 · 남의 번호거나 · 잊었으면 false (2026-10-31)")
  void remembersFresh() {
    store.save(12L, 7L, "대본", null, ChatRoom.COACH, null, true);
    store.save(12L, 7L, "대본 + 위기 칸");
    store.save(13L, 7L, "보통 대본", null, ChatRoom.COACH);

    assertThat(store.isFresh(12L, 7L)).isTrue();
    assertThat(store.isFresh(13L, 7L)).isFalse();
    assertThat(store.isFresh(12L, 8L)).isFalse();
    assertThat(store.isFresh(14L, 7L)).isFalse();

    clock.now = NOW.plus(ChatScriptStore.KEEP).plusSeconds(1);
    assertThat(store.isFresh(12L, 7L)).isFalse();
  }
}
