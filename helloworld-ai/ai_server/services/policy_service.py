from __future__ import annotations
from typing import List, Dict, Any, Optional
from datetime import datetime
from django.utils import timezone
from api.models import TriggerCategoryPolicy
from services.anomaly import KST

# ── 기본 정책(하드코딩 폴백) ───────────────────────────────────────────
_DEFAULTS: dict[str, list[dict[str, Any]]] = {
    # 스트레스 지수 상승 > 호흡, 음악, 명상
    "stress_up": [
        {"code": "BREATHING", "title": "호흡", "priority": 1, "requires_location": False},
        {"code": "MUSIC",     "title": "음악", "priority": 2, "requires_location": False},
        {"code": "MEDITATION","title": "명상", "priority": 3, "requires_location": False},
    ],
    # 심박수 저하 > 호흡, 요가, 걷기
    "hr_low": [
        {"code": "BREATHING", "title": "호흡", "priority": 1, "requires_location": False},
        {"code": "YOGA",      "title": "요가", "priority": 2, "requires_location": False},
        {"code": "WALKING",   "title": "걷기", "priority": 3, "requires_location": False},
    ],
    # 심박수 상승 > 호흡, 명상, 요가
    "hr_high": [
        {"code": "BREATHING",  "title": "호흡", "priority": 1, "requires_location": False},
        {"code": "MEDITATION", "title": "명상", "priority": 2, "requires_location": False},
        {"code": "YOGA",       "title": "요가", "priority": 3, "requires_location": False},
    ],
    # 기존 그대로
    "steps_low": [
        {"code": "OUTING",     "title": "나들이",   "priority": 1, "requires_location": True},
        {"code": "WALKING",    "title": "걷기",     "priority": 2, "requires_location": False},
        # {"code": "STRETCHING", "title": "스트레칭", "priority": 3, "requires_location": False},
    ],
}

# ── 트리거 정규화 ─────────────────────────────────────────────────────
def _normalize_trigger(trigger: Optional[str]) -> str:
    """
    입력 트리거 문자열을 표준코드로 정규화.
      - 소문자화, 하이픈→언더스코어
      - 흔한 별칭을 표준 코드로 매핑
    """
    t = (trigger or "").strip().lower().replace("-", "_")
    aliases = {
        "hr_up": "hr_high",
        "hr_increase": "hr_high",
        "heart_rate_high": "hr_high",
        "hr_down": "hr_low",
        "hr_decrease": "hr_low",
        "heart_rate_low": "hr_low",
        "stress_high": "stress_up",
        "stress_increase": "stress_up",
        "stress_up": "stress_up",
        "steps_down": "steps_low",
        "steps_decrease": "steps_low",
    }
    return aliases.get(t, t)

# 4시간 버킷과 호환되는 간단한 시간대 라벨(필요 시 정책 tod_bucket과 매칭)
def _tod_bucket(now_kst: datetime) -> str:
    h = now_kst.hour
    if 5 <= h < 10:   return "morning"
    if 10 <= h < 17:  return "day"
    if 17 <= h < 22:  return "evening"
    return "night"

def _apply_filters(rows: List[Dict[str, Any]], gestational_week: Optional[int]) -> List[Dict[str, Any]]:
    """
    DB행에는 min_gw/max_gw/tod_bucket 같은 조건이 있을 수 있으므로
    dict(row)에 들어있다면 필터링. (폴백 기본값에는 없음)
    """
    if not rows:
        return rows
    now_kst = timezone.now().astimezone(KST)
    tod = _tod_bucket(now_kst)
    out = []
    for r in rows:
        # 임신 주차 필터
        min_gw = r.get("min_gw"); max_gw = r.get("max_gw")
        if gestational_week is not None:
            if min_gw is not None and gestational_week < min_gw:   continue
            if max_gw is not None and gestational_week > max_gw:   continue
        # 시간대 버킷 필터
        rb = r.get("tod_bucket")
        if rb and rb != tod:                                       continue
        out.append(r)
    return out

def _from_db(trigger: str) -> List[Dict[str, Any]]:
    trig = _normalize_trigger(trigger)
    qs = (TriggerCategoryPolicy.objects
          .filter(trigger=trig, is_active=True)
          .order_by("priority"))
    return [{
        "code": r.category,
        "title": r.title or r.category,
        "priority": r.priority,
        "requires_location": getattr(r, "requires_location", False),
        # 선택 필드들(있을 수도/없을 수도)
        "min_gw": getattr(r, "min_gw", None),
        "max_gw": getattr(r, "max_gw", None),
        "tod_bucket": getattr(r, "tod_bucket", None),
    } for r in qs]

def categories_for_trigger(trigger: str, gestational_week: Optional[int] = None) -> List[Dict[str, Any]]:
    """
    1) DB 우선 조회
    2) 없으면 기본값 폴백(_DEFAULTS)
    3) 임신주차/시간대 필터 적용
       - gestational_week는 기본값 None (API에서 안 넘길 때 하위호환)
    """
    trig = _normalize_trigger(trigger)
    rows = _from_db(trig)
    if not rows:
        rows = _DEFAULTS.get(trig, [])
    rows = _apply_filters(rows, gestational_week)
    # priority로 정렬(폴백도 priority 포함)
    rows = sorted(rows, key=lambda x: x.get("priority", 999))
    return rows
