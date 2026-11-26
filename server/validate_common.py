"""common_notices.json 이 notice.schema.json 규칙에 맞는지 검사한다.
실행:  python validate_common.py                 → common_notices.json 검사
      python validate_common.py 내파일.json      → 다른 파일 검사 (팀원 수집 결과 확인용)"""
import json
import sys
from collections import Counter

import jsonschema

schema = json.load(open("notice.schema.json", encoding="utf-8"))
data = json.load(open(sys.argv[1] if len(sys.argv) > 1 else "common_notices.json", encoding="utf-8"))
if isinstance(data, dict):
    data = data.get("items", [])
v = jsonschema.Draft202012Validator(schema)

bad = 0
for n in data:
    errs = list(v.iter_errors(n))
    if not errs and n.get("id") != f"{n.get('source')}:{n.get('source_id')}":
        print(f"[X] {n.get('id')}\n     id → 'source:source_id' 모양이어야 함 (예: ontong_youth:R2026001)")
        bad += 1
        continue
    if errs:
        bad += 1
        if bad <= 10:
            print(f"[X] {n.get('id')}")
            for e in errs[:3]:
                print("    ", "/".join(map(str, e.path)) or "(전체)", "→", e.message[:120])

print(f"\n총 {len(data)}건 중 통과 {len(data) - bad}건 / 오류 {bad}건")
print("출처별:", dict(Counter(n["source"] for n in data)))
print("마감 상태:", dict(Counter(n["deadline_status"] for n in data)))
print("확인 필요(needs_review):", sum(1 for n in data if n.get("needs_review")))
sys.exit(1 if bad else 0)
