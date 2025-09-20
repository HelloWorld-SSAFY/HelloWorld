# services/spotify_ingest.py  (게이트웨이 로직 인라인 버전)
from __future__ import annotations
import os, time, requests
from typing import List, Dict, Tuple, Optional
from django.db import transaction
from api.models import Content, RecommendationSession, ExposureCandidate

SPOTIFY_CLIENT_ID = os.getenv("SPOTIFY_CLIENT_ID", "")
SPOTIFY_CLIENT_SECRET = os.getenv("SPOTIFY_CLIENT_SECRET", "")
SPOTIFY_MARKET = os.getenv("SPOTIFY_MARKET", "KR")
SPOTIFY_MODE = os.getenv("SPOTIFY_MODE", "remote").strip().lower()  # remote | fallback

# ---- 간단 토큰 캐시 ----
_token_cache: Dict[str, Tuple[str, float]] = {}  # {"app": (token, exp_ts)}

def _get_token() -> str:
    if SPOTIFY_MODE != "remote":
        return ""
    now = time.time()
    tok, exp = _token_cache.get("app", ("", 0.0))
    if tok and now < exp - 60:
        return tok
    resp = requests.post(
        "https://accounts.spotify.com/api/token",
        data={"grant_type": "client_credentials"},
        auth=(SPOTIFY_CLIENT_ID, SPOTIFY_CLIENT_SECRET),
        timeout=8,
    )
    resp.raise_for_status()
    data = resp.json()
    tok = data["access_token"]
    exp = now + int(data.get("expires_in", 3600))
    _token_cache["app"] = (tok, exp)
    return tok

def _search_tracks(q: str, limit: int = 5, market: Optional[str] = None) -> List[Dict]:
    if SPOTIFY_MODE != "remote" or not (SPOTIFY_CLIENT_ID and SPOTIFY_CLIENT_SECRET):
        # fallback 샘플
        return [{
            "id": "stub1",
            "title": "Calm Piano",
            "artists": "Stub Artist",
            "url": "https://open.spotify.com/track/stub1",
            "duration_ms": 180000,
            "album": "Stub Album",
            "thumbnail": None,
            "provider": "spotify",
        }]
    token = _get_token()
    params = {
        "q": q,
        "type": "track",
        "limit": limit,
        "market": market or SPOTIFY_MARKET,
    }
    resp = requests.get(
        "https://api.spotify.com/v1/search",
        params=params,
        headers={"Authorization": f"Bearer {token}"},
        timeout=8,
    )
    resp.raise_for_status()
    out: List[Dict] = []
    for t in resp.json().get("tracks", {}).get("items", []):
        out.append({
            "id": t.get("id"),
            "title": t.get("name"),
            "artists": ", ".join(a.get("name") for a in t.get("artists", [])),
            "url": (t.get("external_urls") or {}).get("spotify"),
            "duration_ms": t.get("duration_ms"),
            "album": (t.get("album") or {}).get("name"),
            "thumbnail": ((t.get("album") or {}).get("images") or [{}])[0].get("url"),
            "provider": "spotify",
        })
    return out

DEFAULT_QUERIES = [
    "태교 음악", "prenatal relaxing music", "pregnancy meditation music",
    "calm instrumental for baby", "relaxing piano"
]

def _pre_score(title: str, duration_ms: Optional[int]) -> float:
    base = 0.6
    try:
        dur = int(duration_ms or 0)
    except Exception:
        dur = 0
    # 2.5~6분 가산, 과도하게 길면 감점
    if 150_000 <= dur <= 360_000: base += 0.1
    elif dur and not (120_000 <= dur <= 600_000): base -= 0.1
    low = (title or "").lower()
    if any(k in low for k in ["instrumental", "piano", "relax", "calm", "태교"]): base += 0.05
    return max(0.0, min(1.0, base))

def _vec(title: str, duration_ms: Optional[int], thumb: Optional[str]) -> Dict:
    return {
        "duration_ms": int(duration_ms or 0),
        "kw_relax": int("relax" in (title or "").lower()),
        "kw_piano": int("piano" in (title or "").lower()),
        "thumb_url": thumb or "",
    }

@transaction.atomic
def ingest_spotify_to_session(session_id: str, max_total: int = 30,
                              query: Optional[str] = None, market: str = "KR") -> Tuple[int, int]:
    """
    지정 세션에 대해 Spotify 트랙을 수집해 Content/ExposureCandidate에 적재.
    반환: (created_candidates, skipped_duplicates)
    """
    try:
        sess = RecommendationSession.objects.select_for_update().get(id=session_id)
    except RecommendationSession.DoesNotExist:
        raise ValueError("INVALID_SESSION")

    bag: List[Dict] = []
    seen = set()

    q_list = [query] if query else DEFAULT_QUERIES
    for q in q_list:
        tracks = _search_tracks(q=q, limit=min(20, max_total), market=market)
        for t in tracks:
            tid = t.get("id")
            if not tid or tid in seen:
                continue
            seen.add(tid)
            bag.append(t)
            if len(bag) >= max_total:
                break
        if len(bag) >= max_total:
            break

    created = skipped = 0
    for t in bag:
        url = t.get("url")
        title = t.get("title") or "Spotify Track"
        thumb = t.get("thumbnail") or ""
        dur = t.get("duration_ms")
        # Content upsert (중복 기준: url)
        content, _ = Content.objects.get_or_create(
            url=url,
            defaults={
                "title": title,
                "category": "MUSIC",
                "is_active": True,
                "provider": "SPOTIFY",
                "external_id": f"sp:{t.get('id')}",
                "thumbnail_url": thumb,  # ✅ 저장
            }
        )
        # 세션 내 중복 후보 방지
        if ExposureCandidate.objects.filter(session=sess, content=content).exists():
            skipped += 1
            continue

        pre = _pre_score(title, dur)
        vec = _vec(title, dur, thumb)  # ✅ thumb_url 보강 저장
        ExposureCandidate.objects.create(
            session=sess, content=content, pre_score=pre, chosen_flag=False, x_item_vec=vec
        )
        created += 1

    return created, skipped
