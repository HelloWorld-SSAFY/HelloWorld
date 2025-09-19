# services/youtube_ingest.py
from __future__ import annotations
import os, re, logging, requests
from typing import List, Dict, Tuple
from requests.adapters import HTTPAdapter, Retry
from django.db import transaction
from django.utils import timezone
from api.models import Content, RecommendationSession, ExposureCandidate
from services.policy_service import categories_for_trigger

log = logging.getLogger(__name__)
BASE = "https://www.googleapis.com/youtube/v3"
YOUTUBE_API_KEY = os.getenv("YOUTUBE_API_KEY") or os.getenv("YT_API_KEY")

def _http():
    s = requests.Session()
    r = Retry(total=3, backoff_factor=0.3, status_forcelist=[429,500,502,503,504], allowed_methods=["GET"])
    s.mount("https://", HTTPAdapter(max_retries=r)); s.mount("http://", HTTPAdapter(max_retries=r))
    s.headers.update({"Accept":"application/json"})
    return s

def _iso8601_to_sec(s: str) -> int:
    """Parse ISO8601 duration like PT10M30S -> seconds (robust, no indexing on Match)."""
    if not s:
        return 0
    m = re.fullmatch(r"PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?", s)
    if not m:
        return 0
    h = int(m.group(1) or 0)
    mi = int(m.group(2) or 0)
    se = int(m.group(3) or 0)
    return h * 3600 + mi * 60 + se

def _default_queries(category: str) -> List[str]:
    c = category.upper()
    if c == "MEDITATION":
        return ["명상 호흡 3분", "마인드풀니스 명상", "guided meditation korean", "breathing exercise 5 min"]
    if c == "YOGA":
        return ["초보 요가 10분", "스트레칭 요가 5분", "beginner yoga korean", "prenatal yoga safe"]
    return [category]

def _search_ids(s: requests.Session, q: str, want: int, region="KR", lang="ko") -> List[str]:
    p = {
        "key": YOUTUBE_API_KEY, "part": "snippet", "type": "video", "q": q,
        "maxResults": min(want, 50), "regionCode": region, "relevanceLanguage": lang,
        "safeSearch": "moderate", "videoEmbeddable": "true", "order": "relevance"
    }
    r = s.get(f"{BASE}/search", params=p, timeout=10); r.raise_for_status()
    data = r.json()
    return [it["id"]["videoId"] for it in data.get("items", []) if it.get("id", {}).get("videoId")]

def _video_meta(s: requests.Session, ids: List[str]) -> Dict[str, dict]:
    out: Dict[str, dict] = {}
    for i in range(0, len(ids), 50):
        chunk = ids[i:i+50]
        p = {"key": YOUTUBE_API_KEY, "part": "snippet,contentDetails", "id": ",".join(chunk)}
        r = s.get(f"{BASE}/videos", params=p, timeout=10); r.raise_for_status()
        for it in r.json().get("items", []):
            vid = it["id"]; sn = it.get("snippet", {}); cd = it.get("contentDetails", {})
            thumbs = sn.get("thumbnails", {}) or {}
            thumb = (thumbs.get("high") or {}).get("url") or (thumbs.get("default") or {}).get("url")
            out[vid] = {
                "title": (sn.get("title") or "").strip(),
                "channel_title": sn.get("channelTitle") or "",
                "duration_sec": _iso8601_to_sec(cd.get("duration")),
                "thumb_url": thumb,
            }
    return out

def _pre_score(title: str, dur: int) -> float:
    base = 0.5
    # 길이: 3~15분 허용, 4~8분 가산
    if 240 <= dur <= 480: base += 0.15
    elif not (180 <= dur <= 900): base -= 0.15
    t = title.lower()
    # 키워드
    if any(k in t for k in ["guided", "가이드", "초보", "beginner"]): base += 0.05
    if any(k in t for k in ["asmr", "sleep", "수면"]): base -= 0.05
    return max(0.0, min(1.0, base))

def _vec(title: str, dur: int) -> Dict:
    t = title.lower()
    return {
        "duration_sec": dur,
        "kws_guided": int(any(k in t for k in ["guided", "가이드"])),
        "kws_beginner": int(any(k in t for k in ["초보", "beginner"])),
        "kws_sleep": int(any(k in t for k in ["asmr", "sleep", "수면"])),
        "hour_bucket": timezone.localtime().hour // 4,  # 0..5
    }

def _cat_name(x):
    # dict/obj/str 어떤 형식이 와도 안전하게 이름을 추출
    if isinstance(x, str):
        return x
    if isinstance(x, dict):
        return x.get("category") or x.get("cat") or x.get("name") or x.get("code")
    return (
        getattr(x, "category", None)
        or getattr(x, "cat", None)
        or getattr(x, "name", None)
        or getattr(x, "code", None)
    )

@transaction.atomic
def ingest_youtube_to_session(session_id: str, category: str, max_total: int = 30,
                              queries: List[str] | None = None,
                              region="KR", lang="ko") -> Tuple[int,int]:
    """
    지정 세션에 대해 YouTube 후보를 수집해 Content/ExposureCandidate에 적재.
    반환: (created_candidates, skipped_duplicates)
    """
    if not YOUTUBE_API_KEY:
        raise RuntimeError("YOUTUBE_API_KEY가 설정되어 있지 않습니다.")
    try:
        sess = RecommendationSession.objects.select_for_update().get(id=session_id)
    except RecommendationSession.DoesNotExist:
        raise ValueError("INVALID_SESSION")

    category = category.upper().strip()
    if category not in {"MEDITATION","YOGA"}:
        raise ValueError("CATEGORY_NOT_ALLOWED")

    # 세션 허용 카테고리 확인 (dict/str/obj 모두 안전)
    catlist = categories_for_trigger(sess.trigger)
    allowed = {(_cat_name(c) or "").upper() for c in catlist if _cat_name(c)}
    if category.upper() not in allowed:
        raise ValueError("CATEGORY_NOT_ALLOWED_FOR_SESSION")

    s = _http()
    bag, seen = [], set()
    for q in (queries or _default_queries(category)):
        ids = _search_ids(s, q, max_total, region, lang)
        for vid in ids:
            if vid in seen: continue
            seen.add(vid); bag.append(vid)
            if len(bag) >= max_total: break
        if len(bag) >= max_total: break

    meta = _video_meta(s, bag)

    created = skipped = 0
    for vid in bag:
        m = meta.get(vid) or {}
        title = m.get("title") or "YouTube 영상"
        dur = int(m.get("duration_sec") or 0)
        url = f"https://www.youtube.com/watch?v={vid}"

        # Content upsert(간단 중복 판정: url)
        content, _ = Content.objects.get_or_create(
            url=url,
            defaults={
                "title": title,
                "category": category,
                "is_active": True,
                **({"provider": "YOUTUBE"} if hasattr(Content, "provider") else {}),
                **({"external_id": f"yt:{vid}"} if hasattr(Content, "external_id") else {}),
                **({"duration_sec": dur} if hasattr(Content, "duration_sec") else {}),
                **({"channel_title": m.get("channel_title","")} if hasattr(Content, "channel_title") else {}),
                **({"thumbnail_url": m.get("thumb_url","")} if hasattr(Content, "thumbnail_url") else {}),
            }
        )

        # 세션 내 중복 후보 방지
        if ExposureCandidate.objects.filter(session=sess, content=content).exists():
            skipped += 1
            continue

        pre = _pre_score(title, dur)
        vec = _vec(title, dur)
        ExposureCandidate.objects.create(
            session=sess, content=content, pre_score=pre, chosen_flag=False, x_item_vec=vec
        )
        created += 1

    log.info(f"[YT→Candidates] session={session_id} cat={category} created={created} skipped={skipped}")
    return created, skipped
