# services/scoring.py
from __future__ import annotations
import math
import random
from typing import Any, Dict, Optional, Tuple, List

# ─────────────────────────────────────────────────────────────────────
# 유틸
# ─────────────────────────────────────────────────────────────────────
def _clamp(x: float, lo: float, hi: float) -> float:
    return max(lo, min(hi, x))

def _safe(v: Optional[float], default: float = 0.0) -> float:
    return default if v is None else v

def _as_dict(obj: Any) -> Dict[str, Any]:
    """Content/ExposureCandidate/x_item_vec가 dict/객체 섞여 들어와도 안전 접근"""
    if isinstance(obj, dict):
        return obj
    # Django model 인스턴스라면 필요한 필드만 추출
    out = {}
    for k in ("lang", "length_sec", "voice_guided", "channel_quality", "pre_score_base", "title"):
        if hasattr(obj, k):
            out[k] = getattr(obj, k)
    # Content.extra-like: tags/thumbnail_url/url/provider/external_id 등은 x_item_vec이 더 우선
    return out


# ─────────────────────────────────────────────────────────────────────
# 1) pre_score (품질/인기도/신선도)
#    - meta는 인제스트/후보 생성 시 구성한 사전(dict) 기준
# ─────────────────────────────────────────────────────────────────────
CLAMP_PRE_MIN, CLAMP_PRE_MAX = 0.05, 0.95
RECENCY_TAU_DAYS = 21.0  # exp(-age_days/τ)

def compute_pre_score(meta: Dict[str, Any], p95_views: float = 50_000.0) -> float:
    """
    meta 예:
    {
      "views": 12345, "likes": 1200, "comments": 90,
      "duration_sec": 600, "lang": "ko", "channel_quality": 0.6,
      "age_days": 5, "creator_id": "...",
      "tags": ["guided","prenatal_safe"],
      "music": {"tempo":72, "energy":0.25, "valence":0.2, "instrumentalness":0.8, "acousticness":0.7}
    }
    """
    views = _safe(meta.get("views"), 0.0)
    likes = _safe(meta.get("likes"), 0.0)
    comments = _safe(meta.get("comments"), 0.0)
    channel_quality = _safe(meta.get("channel_quality"), 0.5)
    age_days = _safe(meta.get("age_days"), 30.0)

    # 인기도/참여도 정규화
    s_views = math.log1p(views) / max(1e-6, math.log1p(max(1.0, p95_views)))
    like_rate = likes / max(1.0, views)
    comment_rate = comments / max(1.0, views)
    s_engage = 0.7 * like_rate + 0.3 * comment_rate

    recency = math.exp(-age_days / RECENCY_TAU_DAYS)

    pre = 0.5 * channel_quality + 0.3 * s_views + 0.2 * s_engage
    pre *= recency
    return _clamp(pre, CLAMP_PRE_MIN, CLAMP_PRE_MAX)


def fallback_pre_score(
    candidate_meta: Dict[str, Any],
    content_meta: Dict[str, Any],
    pre_score_field: Optional[float] = None,
) -> float:
    """
    우선순위: ExposureCandidate.pre_score → Content.pre_score_base → Content.channel_quality → 0.5
    """
    if pre_score_field is not None:
        return _clamp(float(pre_score_field), CLAMP_PRE_MIN, CLAMP_PRE_MAX)
    cs = _as_dict(content_meta)
    if cs.get("pre_score_base") is not None:
        return _clamp(float(cs["pre_score_base"]), CLAMP_PRE_MIN, CLAMP_PRE_MAX)
    if cs.get("channel_quality") is not None:
        return _clamp(float(cs["channel_quality"]), CLAMP_PRE_MIN, CLAMP_PRE_MAX)
    # 마지막 폴백: meta로 계산 시도, 실패하면 0.5
    try:
        return compute_pre_score(candidate_meta)
    except Exception:
        return 0.5


# ─────────────────────────────────────────────────────────────────────
# 2) context boost (컨텐츠 레벨만, 곱셈형) — 카테고리명 사용 금지
#    clamp: [0.7, 1.3]
# ─────────────────────────────────────────────────────────────────────
CLAMP_BOOST_MIN, CLAMP_BOOST_MAX = 0.70, 1.30

def _trimester_by_week(week: Optional[int]) -> Optional[int]:
    if week is None:
        return None
    if week <= 13:
        return 1
    if week <= 27:
        return 2
    return 3

def _lang_boost(item_lang: str, pref_lang: str = "ko") -> Tuple[float, Optional[str]]:
    if not item_lang:
        return 1.0, None
    if item_lang.lower() == (pref_lang or "ko").lower():
        return 1.10, "lang"
    return 0.98, None  # 살짝 감점

def _duration_boost(length_sec: Optional[int], pref_range: Tuple[int,int] = (300, 900)) -> Tuple[float, Optional[str]]:
    if not length_sec:
        return 1.0, None
    lo, hi = pref_range
    if lo <= length_sec <= hi:
        return 1.08, "duration"
    # 멀어질수록 점감 (±10분까지 선형)
    center = (lo + hi) / 2
    span = max(60.0, (hi - lo) / 2)
    dist = min(600.0, abs(length_sec - center))
    boost = 1.08 - 0.08 * (dist / 600.0)
    return _clamp(boost, 0.94, 1.08), None

def _guided_boost(voice_guided: Optional[bool], prefer_guided: bool = True) -> Tuple[float, Optional[str]]:
    if voice_guided is None:
        return 1.0, None
    if prefer_guided and voice_guided:
        return 1.05, "guided"
    if prefer_guided and not voice_guided:
        return 0.97, None
    if not prefer_guided and not voice_guided:
        return 1.03, "unguided"
    return 0.99, None

def _safety_boost(tags: List[str], trimester: Optional[int]) -> Tuple[float, Optional[str]]:
    """
    안전/금기 태그 기반 가중. trimester 높을수록 금기 페널티 강화.
    friendly: prenatal_safe/restorative/gentle/beginner (+)
    risky   : inversion/deep_twist/hot/supine_risky (−)
    """
    if not tags:
        return 1.0, None
    t = trimester or 2
    friendly = {"prenatal_safe", "restorative", "gentle", "beginner"}
    risky = {"inversion", "deep_twist", "hot", "supine_risky"}

    b = 1.0
    if any(tag in friendly for tag in tags):
        b *= 1.06
    risky_hits = sum(tag in risky for tag in tags)
    if risky_hits:
        # 1분기 -2%, 2분기 -5%, 3분기 -9% per hit(최대 2hit 반영)
        per_hit = {1: 0.98, 2: 0.95, 3: 0.91}[t]
        for _ in range(min(risky_hits, 2)):
            b *= per_hit
    return _clamp(b, 0.85, 1.08), ("safety" if b != 1.0 else None)

def _music_boost(music: Dict[str, Any]) -> Tuple[float, Optional[str]]:
    """
    임산부 릴랙스 기본 프리셋:
    - tempo 55~80, energy 낮음(<=0.35), instrumentalness 높음(>=0.6), acousticness 높음(>=0.5)
    """
    if not music:
        return 1.0, None
    tempo = music.get("tempo")
    energy = music.get("energy")
    instrumentalness = music.get("instrumentalness")
    acousticness = music.get("acousticness")

    b = 1.0
    reason = None

    if isinstance(tempo, (int, float)):
        if 55 <= tempo <= 80:
            b *= 1.05
            reason = (reason or "music")
        elif 45 <= tempo <= 95:
            b *= 1.02

    if isinstance(energy, (int, float)):
        if energy <= 0.35:
            b *= 1.03
            reason = (reason or "music")
        elif energy >= 0.7:
            b *= 0.97

    if isinstance(instrumentalness, (int, float)) and instrumentalness >= 0.6:
        b *= 1.03
        reason = (reason or "music")

    if isinstance(acousticness, (int, float)) and acousticness >= 0.5:
        b *= 1.02
        reason = (reason or "music")

    return _clamp(b, 0.93, 1.08), reason

def _novelty_penalty(creator_id: Optional[str], seen_creators: Optional[set]) -> Tuple[float, Optional[str]]:
    """
    동일 creator 반복 노출 감점(신규성). 세션 내 이미 본 크리에이터면 -3%.
    """
    if not creator_id or not seen_creators:
        return 1.0, None
    return (0.97, "novelty") if creator_id in seen_creators else (1.0, None)

def compute_context_boost(
    content_obj: Any,
    x_item_vec: Optional[Dict[str, Any]] = None,
    prefs: Optional[Dict[str, Any]] = None,
    pregnancy_week: Optional[int] = None,
    seen_creators: Optional[set] = None,
) -> Tuple[float, List[str]]:
    """
    반환: (boost, reasons)
    - 카테고리명은 절대 사용하지 않음(컨텐츠 속성만).
    """
    prefs = prefs or {}
    ci = _as_dict(content_obj)
    xv = x_item_vec or {}

    # 언어
    b1, r1 = _lang_boost(ci.get("lang", "") or xv.get("lang", ""), prefs.get("lang", "ko"))
    # 길이
    b2, r2 = _duration_boost(ci.get("length_sec") or xv.get("duration_sec"), prefs.get("duration_range_sec", (300, 900)))
    # 가이드
    b3, r3 = _guided_boost(ci.get("voice_guided"), prefs.get("prefer_guided", True))
    # 안전/금기
    tags = set()
    for src in (ci.get("tags"), xv.get("tags")):
        if isinstance(src, list):
            tags.update(str(x).lower() for x in src)
    b4, r4 = _safety_boost(list(tags), _trimester_by_week(pregnancy_week))

    # 음악 피처
    music = xv.get("music") if isinstance(xv.get("music"), dict) else {}
    b5, r5 = _music_boost(music)

    # 신규성(creator)
    creator_id = xv.get("creator_id") or ci.get("creator_id")
    b6, r6 = _novelty_penalty(creator_id, seen_creators)

    boost = b1 * b2 * b3 * b4 * b5 * b6
    boost = _clamp(boost, CLAMP_BOOST_MIN, CLAMP_BOOST_MAX)

    reasons = [r for r in (r1, r2, r3, r4, r5, r6) if r]
    return boost, reasons


# ─────────────────────────────────────────────────────────────────────
# 3) Thompson Sampling 샘플링
#    θ ~ Beta(a_u + λ a_g + α0, b_u + λ b_g + β0)
# ─────────────────────────────────────────────────────────────────────
def sample_theta(
    a_u: Optional[float], b_u: Optional[float],
    a_g: Optional[float], b_g: Optional[float],
    alpha0: float = 1.0, beta0: float = 1.0, lam: float = 0.3,
    rng: Optional[random.Random] = None
) -> float:
    ru_a = max(0.0, a_u or 0.0)
    ru_b = max(0.0, b_u or 0.0)
    rg_a = max(0.0, a_g or 0.0)
    rg_b = max(0.0, b_g or 0.0)

    alpha = max(1e-6, ru_a + lam * rg_a + alpha0)
    beta = max(1e-6, ru_b + lam * rg_b + beta0)

    rng = rng or random
    # Python 표준 random.betavariate(a, b)
    return rng.betavariate(alpha, beta)


# ─────────────────────────────────────────────────────────────────────
# 4) 최종 스코어
#    score = pre × boost × θ
# ─────────────────────────────────────────────────────────────────────
def score_item(
    pre: float,
    boost: float,
    theta: float,
) -> float:
    return pre * boost * theta


# ─────────────────────────────────────────────────────────────────────
# 5) 고수준 헬퍼: 한 아이템 스코어링(Reason 문자열 생성 포함)
# ─────────────────────────────────────────────────────────────────────
def score_with_reason(
    *,
    content_obj: Any,
    x_item_vec: Dict[str, Any],
    prefs: Dict[str, Any],
    pregnancy_week: Optional[int],
    seen_creators: Optional[set],
    pre_score_field: Optional[float],
    global_alpha: Optional[float], global_beta: Optional[float],
    user_alpha: Optional[float], user_beta: Optional[float],
    alpha0: float = 1.0, beta0: float = 1.0, lam: float = 0.3,
    rng: Optional[random.Random] = None
) -> Tuple[float, float, float, str]:
    """
    반환: (score, pre, boost, theta, reason_str)
    """
    pre = fallback_pre_score(x_item_vec, content_obj, pre_score_field)
    boost, reasons = compute_context_boost(content_obj, x_item_vec, prefs, pregnancy_week, seen_creators)
    theta = sample_theta(user_alpha, user_beta, global_alpha, global_beta, alpha0, beta0, lam, rng)
    score = score_item(pre, boost, theta)
    reason = f"pre={pre:.2f}×boost={boost:.2f}×θ={theta:.2f}" + (f" ({','.join(reasons)})" if reasons else "")
    return score, pre, boost, theta, reason
