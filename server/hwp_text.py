"""HWP / HWPX 첨부에서 글자만 뽑는다. (Gemini는 한글 문서를 직접 못 읽으므로, 글로 바꿔서 보낸다)
- HWPX(새 형식): zip 안의 Contents/section*.xml 에서 글자(<hp:t>)를 읽는다.
- HWP(옛 형식, 5.x): olefile 로 열어 BodyText/Section* 를 풀고(zlib) 문단 글자 레코드만 읽는다.
  배포용(암호화) 문서는 본문을 못 읽어서 '미리보기 글자(PrvText)'만 쓴다.
쓰는 법: text = hwp_bytes_to_text(data)   → 실패하면 "" (예외를 밖으로 던지지 않는다)"""
from __future__ import annotations

import io
import re
import struct
import zipfile
import zlib

OLE_MAGIC = b"\xd0\xcf\x11\xe0\xa1\xb1\x1a\xe1"
PARA_TEXT = 67                                  # HWPTAG_BEGIN(16) + 51
_EXT_CTRL = {1, 2, 3, 11, 12, 14, 15, 16, 17, 18, 21, 22, 23}   # 8글자 차지하는 확장 제어 문자
_INL_CTRL = {4, 5, 6, 7, 8, 9, 19, 20}                          # 8글자 차지하는 인라인 제어 문자 (9 = 탭)


def kind_of(data: bytes) -> str | None:
    """바이트 내용으로 HWP/HWPX 인지 판단 (download.php 처럼 이름으로 알 수 없는 링크용)"""
    if data[:8] == OLE_MAGIC:
        return "hwp"
    if data[:2] == b"PK":
        try:
            names = zipfile.ZipFile(io.BytesIO(data)).namelist()
        except zipfile.BadZipFile:
            return None
        if any(n.startswith("Contents/section") for n in names):
            return "hwpx"
    return None


def para_text(payload: bytes) -> str:
    """문단 글자 레코드(UTF-16) → 글자. 제어 문자는 건너뛰고 탭/줄바꿈만 살린다."""
    out, i, n = [], 0, len(payload) // 2
    while i < n:
        c = struct.unpack_from("<H", payload, i * 2)[0]
        if c >= 32:
            out.append(chr(c))
            i += 1
        elif c in _EXT_CTRL or c in _INL_CTRL:
            if c == 9:
                out.append("\t")
            i += 8
        else:                                   # 0, 10, 13, 24~31 : 1글자짜리 제어 문자
            if c in (10, 13):
                out.append("\n")
            i += 1
    return "".join(out)


def records(data: bytes):
    """레코드 단위로 (tag, payload) 를 꺼낸다"""
    pos = 0
    while pos + 4 <= len(data):
        h = struct.unpack_from("<I", data, pos)[0]
        tag, size = h & 0x3FF, (h >> 20) & 0xFFF
        pos += 4
        if size == 0xFFF:
            size = struct.unpack_from("<I", data, pos)[0]
            pos += 4
        yield tag, data[pos:pos + size]
        pos += size


def hwp5_text(data: bytes) -> str:
    import olefile                              # requirements.txt 에 추가됨
    ole = olefile.OleFileIO(io.BytesIO(data))
    try:
        header = ole.openstream("FileHeader").read()
        if not header.startswith(b"HWP Document File"):
            return ""
        flags = struct.unpack_from("<I", header, 36)[0]
        compressed, distribute = bool(flags & 1), bool(flags & 4)
        sections = sorted((e for e in ole.listdir() if len(e) == 2 and e[0] == "BodyText"),
                          key=lambda e: int(re.sub(r"\D", "", e[1]) or 0))
        parts = []
        if not distribute:
            for e in sections:
                raw = ole.openstream(e).read()
                if compressed:
                    raw = zlib.decompress(raw, -15)
                parts += [para_text(p) for tag, p in records(raw) if tag == PARA_TEXT]
        text = "\n".join(parts)
        if not text.strip() and ole.exists("PrvText"):       # 배포용 문서 등: 앞부분 미리보기 글자라도 쓴다
            text = ole.openstream("PrvText").read().decode("utf-16-le", "ignore")
        return text
    finally:
        ole.close()


def hwpx_text(data: bytes) -> str:
    z = zipfile.ZipFile(io.BytesIO(data))
    secs = sorted((n for n in z.namelist() if re.fullmatch(r"Contents/section\d+\.xml", n)),
                  key=lambda n: int(re.sub(r"\D", "", n)))
    out = []
    for n in secs:
        xml = z.read(n).decode("utf-8", "ignore")
        for p in re.findall(r"<hp:p[ >].*?</hp:p>", xml, re.S):          # 문단
            t = "".join(re.findall(r"<hp:t(?:\s[^>]*)?>(.*?)</hp:t>", p, re.S))
            t = re.sub(r"<[^>]+>", "", t)
            out.append(t.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").replace("&quot;", '"'))
    return "\n".join(out)


def tidy(text: str, limit: int = 12000) -> str:
    lines = [re.sub(r"[ \t ]+", " ", ln).strip() for ln in text.splitlines()]
    return "\n".join(ln for ln in lines if ln)[:limit]


def hwp_bytes_to_text(data: bytes) -> str:
    try:
        k = kind_of(data)
        if k == "hwp":
            return tidy(hwp5_text(data))
        if k == "hwpx":
            return tidy(hwpx_text(data))
    except Exception:                           # noqa: BLE001  깨진 파일·모르는 형식 → 글자 없음으로 처리
        return ""
    return ""


if __name__ == "__main__":                      # 직접 확인용:  python hwp_text.py 파일.hwp
    import sys
    for f in sys.argv[1:]:
        t = hwp_bytes_to_text(open(f, "rb").read())
        print(f"=== {f}: {len(t)}자\n{t[:800]}\n")
