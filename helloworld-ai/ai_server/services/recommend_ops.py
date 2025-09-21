# services/recommend_ops.py
from __future__ import annotations
import uuid
from typing import List, Optional, Dict, Any

from django.db import transaction, IntegrityError
from django.db.models import F

from api.models import (
    RecommendationSession,
    ExposureCandidate,
    ItemRec,
    RecommendationDelivery,   # ✅ precompute 결과도 recommend_delivery에 기록
)

AUTO_SOURCE = "auto_restrict"


def _get_session(
    attach_to_session: Optional[str],
    user_ref: str,
    category: Optional[str],
) -> RecommendationSession:
    """
    - attach_to_session 있으면 해당 세션에 붙이고
    - 없으면 자동 restrict 세션 생성
    """
    if attach_to_session:
        try:
            sid = uuid.UUID(str(attach_to_session))
            sess = RecommendationSession.objects.filter(id=sid).first()
            if sess:
                return sess
        except Exception:
            pass

    return RecommendationSession.objects.create(
        user_ref=user_ref,
        trigger="restrict",
        mode="restrict",
        context={},
    )


def _pick_one_candidate(cat: str) -> Optional[ExposureCandidate]:
    """
    카테고리별 후보 중 1개 선택. pre_score 내림차순 우선, 없으면 임의.
    """
    qs = (
        ExposureCandidate.objects
        .filter(content__category__iexact=cat)
        .select_related("content")
    )

    # Django/DB별 nulls_last 호환을 위해 try/except
    try:
        cand = qs.order_by(F("pre_score").desc(nulls_last=True)).first()
        if cand:
            return cand
    except Exception:
        pass

    return qs.order_by("-pre_score").first() or qs.first()


@transaction.atomic
def precompute_for_categories(
    user_ref: str,
    cats: List[str],
    attach_to_session: Optional[str] = None,
) -> List[Dict[str, Any]]:
    """
    텔레메트리 restrict 시 카테고리별로 1건씩 미리 추천/저장.

    - 같은 세션에 묶고 싶으면 attach_to_session 전달
    - 저장:
        • ItemRec(session, content, user_ref, score, reason='cts')
        • RecommendationDelivery(item_kind='CONTENT', context={...})
    - 반환: [{category, session_id, content_id}, ...]
    """
    out: List[Dict[str, Any]] = []

    # 모든 카테고리를 한 세션에 묶어야 하면 base_session 사용
    base_session = _get_session(attach_to_session, user_ref, None) if attach_to_session else None

    for cat in cats:
        cand = _pick_one_candidate(cat)
        if not cand:
            continue

        sess = base_session or _get_session(None, user_ref, cat)

        # --- ItemRec upsert(세션 내 동일 content 중복 방지) -----------------------
        score = getattr(cand, "pre_score", None) or 0.0
        try:
            # unique_together (session, content) 가정
            rec, created = ItemRec.objects.get_or_create(
                session=sess,
                content_id=cand.content_id,
                defaults={
                    "user_ref": user_ref,
                    "score": score,
                    "reason": "cts",
                },
            )
            if not created:
                # 기존 레코드가 있으면 점수/사유 정도만 보정
                dirty = False
                if rec.score is None or rec.score < score:
                    rec.score = score
                    dirty = True
                if not rec.reason:
                    rec.reason = "cts"
                    dirty = True
                if dirty:
                    rec.save(update_fields=["score", "reason"])
        except IntegrityError:
            # 경쟁 상황 등 예외 시 최소 한 번 저장 시도
            ItemRec.objects.filter(session=sess, content_id=cand.content_id).update(
                score=score
            )

        # --- RecommendationDelivery 로그 ---------------------------------------
        c = cand.content  # may be None(이상 케이스) → 안전하게 접근
        try:
            thumb = (getattr(c, "thumbnail_url", "") or "") or ((cand.x_item_vec or {}).get("thumb_url") or "")
        except Exception:
            thumb = ""

        RecommendationDelivery.objects.create(
            session=sess,
            user_ref=user_ref,
            category=(cat or (getattr(c, "category", "") or "")),
            item_kind="CONTENT",                 # ✅ 필드명: item_kind
            content_id=cand.content_id,
            title=(getattr(c, "title", "") or ""),
            url=(getattr(c, "url", "") or ""),
            thumbnail=thumb,                     # ✅ 필드명: thumbnail
            rank=None,
            score=score,
            reason="cts",
            context={"origin": "telemetry", "candidate_id": cand.id},  # ✅ 필드명: context
        )

        out.append({
            "category": cat,
            "session_id": str(sess.id),
            "content_id": cand.content_id,
        })

    return out
