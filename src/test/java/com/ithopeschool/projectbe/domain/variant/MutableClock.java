package com.ithopeschool.projectbe.domain.variant;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

// 테스트에서 "지금" 을 마음대로 옮길 수 있는 시계입니다. (다음 날로 넘기기 등)
public class MutableClock extends Clock {

  private Instant now;

  public MutableClock(Instant now) {
    this.now = now;
  }

  public void set(Instant instant) {
    this.now = instant;
  }

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
