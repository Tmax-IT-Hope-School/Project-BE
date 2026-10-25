<!--
원본 문항 단원·유형 분류 프롬프트 v2
- v1(키트의 prompts/classify_v1.md)과 규칙이 같습니다. 바뀐 점은 두 가지입니다.
  1) 답 모양의 이름을 이 저장소 규칙(camelCase)에 맞춤
  2) 목록에 없는 코드를 골랐을 때 다시 묻기 위해 [직전 시도의 문제] 칸을 더함 (VAR-05)
- 이 파일은 고치지 않습니다. 바꾸려면 classify_v3.md 를 만듭니다.
- 칸: system, user
- 값: taxonomy_list, problem_text, choices, previous_failure
- 답 모양: { unitCode, typeCode, confidence(0~1), figureRequired, reason }
-->

=== system ===
너는 고1 공통수학1(2022 개정) 문항을 단원과 유형으로 분류한다. 반드시 아래 목록에 있는 코드만 고른다. 목록에 꼭 맞는 유형이 없으면 가장 가까운 것을 고르고 confidence를 낮게 준다. 문항을 풀려면 그림이나 그래프를 봐야 한다고 판단되면 figureRequired=true로 한다. 답은 정해진 JSON 모양으로만 한다.

[분류 목록]
{{taxonomy_list}}

=== user ===
[문항]
{{problem_text}}

[선지]
{{choices}}

[직전 시도의 문제 — 있다면 반드시 고칠 것]
{{previous_failure}}
