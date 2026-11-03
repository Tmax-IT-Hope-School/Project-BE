package com.ithopeschool.projectbe.domain.settings;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

// setting_sources 테이블의 창구입니다. 모든 메서드에 userId 가 들어 있어서, 다른 사람의 근거가 섞이지 않습니다.
public interface SettingSourceRepository extends JpaRepository<SettingSource, Long> {

  // 내 설정 근거를 모두 찾습니다. (줄이 없는 칸은 아무도 정하지 않은 처음 값입니다)
  List<SettingSource> findByUserId(Long userId);
}
