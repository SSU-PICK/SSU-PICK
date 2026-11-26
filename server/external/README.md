# external/ — 팀원 수집분(교외·인스타)을 서버 DB에 합치는 곳

- collectors/  : 팀원 수집기(.py). 서버가 수집할 때마다(한국시간 8·11·14·17·20·23시) 자동 실행된다.
                 각 수집기는 결과를 ../inbox/<이름>.json 으로 저장한다.
- inbox/       : 공통 형식(notice.schema.json) JSON 파일. 여기 있는 파일은 수집 때마다 DB에 합쳐진다.
                 수집기 없이 JSON 파일만 넣어도 된다.
- 형식 검사:   python validate_common.py external/inbox/<파일>.json
- 바로 반영:   python lab.py collect   (관리자 키는 /etc/ssu-pick.env 에서 읽음)
- 형식이 틀린 공지는 DB에 들어가지 않고, 서버 로그에 [건너뜀] 이유가 찍힌다.

## 평강 데이터 (자동)
수집 때마다 collectors/ 안의 파일이 이름 순서대로 돈다.
1. collectors/1_pg_live.py   : 평강 수집기(~/ssu-pick/SSUPICK)로 LH·마이홈 실제 API 재수집 → pg_data/lh_live.json, myhome_live.json
                               키는 /etc/ssu-pick.env. 실패하면 예전 파일 유지. 3시간에 한 번만.
                               온통청년도 실시간(서울·전국 + 마감 전만 반영). PG_LIVE=0 전부 끄기 / PG_LIVE_ONTONG=0 온통청년만 끄기
2. collectors/2_pg_convert.py: pg_data 전부 → inbox/pg_<출처>.json (우리 형식). 실시간 파일이 스냅샷보다 우선.
                               설정(파일 맨 위): LH 접수마감 숨김, 학교 공지 180일, 개인정보 글 제외, 출처 통째로 빼기
- pg_data/ : 평강 원본(v1.0). 스냅샷 lh_myhome_snapshot.json / ontongyouth.json / mongddang_list.json + mongddang_detail.json /
             school_notices.json + ai_college_detail.json. 새 버전을 받으면 같은 이름으로 덮어쓰기. 그 밖의 v1.0 *.json 도 자동 변환.
- 평강 데이터는 검토 전이라 전부 needs_review=true → 앱에 '원문 확인 필요' 표시

## 학과·학생회·청년몽땅 (우리 크롤러)
- ../site_collect.py : AI대학·SW학부 공지, 총학·AI SW 학생회 링크트리, 청년몽땅 → site_notices.json → AI 추출 → DB
- 우리 크롤러가 받은 출처는 2_pg_convert.py 가 평강 파일에서 자동으로 뺀다 (중복 방지)
- 끄기: /etc/ssu-pick.env 에 COLLECT_SITES=0
