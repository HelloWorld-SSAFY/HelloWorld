# api/models.py
from __future__ import annotations
import uuid
from django.db import models


# ─────────────────────────────────────────────────────────────────────
# 공통/선택 목록
# ─────────────────────────────────────────────────────────────────────
MODE_CHOICES = (
    ("normal", "normal"),
    ("restrict", "restrict"),
    ("emergency", "emergency"),
)

FEEDBACK_TYPE_CHOICES = (
    ("ACCEPT", "ACCEPT"),
    ("COMPLETE", "COMPLETE"),
    ("EFFECT", "EFFECT"),
)


# ─────────────────────────────────────────────────────────────────────
# 콘텐츠 (외부 제공자 매핑 + 금기/활성 여부)
# 장소 추천 지원을 위해 type/lat/lng/tags/safety_flags 추가
# ─────────────────────────────────────────────────────────────────────
class Content(models.Model):
    provider = models.CharField(max_length=50)             # 예: "sp" (spotify 등)
    external_id = models.CharField(max_length=200)         # 공급자 측 식별자
    category = models.CharField(max_length=50)             # BREATHING / MEDITATION / OUTING 등
    title = models.CharField(max_length=200, blank=True, default="")
    url = models.URLField(max_length=500, blank=True, default="")
    is_active = models.BooleanField(default=True)

    # 장소형 컨텐츠용 필드
    type = models.CharField(max_length=10, default="CONTENT")  # CONTENT / PLACE
    lat = models.DecimalField(max_digits=9, decimal_places=6, null=True, blank=True)
    lng = models.DecimalField(max_digits=9, decimal_places=6, null=True, blank=True)
    tags = models.JSONField(null=True, blank=True)             # ["SHADE","STROLLER_OK"]
    safety_flags = models.JSONField(null=True, blank=True)     # ["RESTROOM_NEARBY"]

    class Meta:
        db_table = "content"
        unique_together = (("provider", "external_id"),)
        indexes = [
            models.Index(fields=["type"]),
            models.Index(fields=["lat", "lng"]),
        ]

    def __str__(self) -> str:
        return f"{self.provider}:{self.external_id} ({self.category})"


# ─────────────────────────────────────────────────────────────────────
# 추천 세션(한 번의 추천 판단/응답 단위)
# ─────────────────────────────────────────────────────────────────────
class RecommendationSession(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)  # session_id
    user_ref = models.CharField(max_length=64, db_index=True)                    # 외부 사용자 식별자
    trigger = models.CharField(max_length=50, blank=True, default="")            # 어떤 트리거로 시작했는지(옵션)
    mode = models.CharField(max_length=10, choices=MODE_CHOICES, default="normal")
    context = models.JSONField(default=dict, blank=True)                         # 버킷/룰버전/사유 등
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = "recommendation_session"
        indexes = [
            models.Index(fields=["user_ref", "created_at"]),
            models.Index(fields=["mode", "created_at"]),
        ]

    def __str__(self) -> str:
        return f"session:{self.id} user:{self.user_ref} mode:{self.mode}"


# ─────────────────────────────────────────────────────────────────────
# 노출 후보(전처리 풀 로깅: 금기 제외 후 pre_score/x_item_vec 보관)
# ─────────────────────────────────────────────────────────────────────
class ExposureCandidate(models.Model):
    session = models.ForeignKey(RecommendationSession, on_delete=models.CASCADE, related_name="candidates")
    content = models.ForeignKey(Content, on_delete=models.CASCADE)
    pre_score = models.FloatField(null=True, blank=True)
    chosen_flag = models.BooleanField(default=False)
    x_item_vec = models.JSONField(default=dict, blank=True)            # 임베딩/피처 등
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = "exposure_candidate"
        indexes = [
            models.Index(fields=["session"]),
            models.Index(fields=["content"]),
        ]


# ─────────────────────────────────────────────────────────────────────
# 최종 추천 N개 (랭크/점수/사유)
# ─────────────────────────────────────────────────────────────────────
class ItemRec(models.Model):
    session = models.ForeignKey(RecommendationSession, on_delete=models.CASCADE, related_name="items")
    content = models.ForeignKey(Content, on_delete=models.CASCADE)
    rank = models.IntegerField()                               # 노출 순번(1부터)
    score = models.FloatField(null=True, blank=True)
    reason = models.CharField(max_length=200, blank=True, default="")
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = "item_rec"
        indexes = [
            models.Index(fields=["session", "rank"]),
        ]
        unique_together = (("session", "content"),)


# ─────────────────────────────────────────────────────────────────────
# 피드백(ACCEPT/COMPLETE/EFFECT 등)
# ─────────────────────────────────────────────────────────────────────
class Feedback(models.Model):
    user_ref = models.CharField(max_length=64, db_index=True)
    session = models.ForeignKey(RecommendationSession, on_delete=models.SET_NULL, null=True, blank=True)
    item_rec = models.ForeignKey(ItemRec, on_delete=models.SET_NULL, null=True, blank=True)
    content = models.ForeignKey(Content, on_delete=models.SET_NULL, null=True, blank=True)
    type = models.CharField(max_length=10, choices=FEEDBACK_TYPE_CHOICES)
    value = models.FloatField(null=True, blank=True)           # 필요 시 일반 수치 보상
    dwell_ms = models.IntegerField(null=True, blank=True)
    watched_pct = models.FloatField(null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = "feedback"
        indexes = [
            models.Index(fields=["user_ref", "created_at"]),
            models.Index(fields=["type", "created_at"]),
        ]


# ─────────────────────────────────────────────────────────────────────
# 아웃컴(효과 지표 집계: before/after/delta/effect)
# ─────────────────────────────────────────────────────────────────────
class Outcome(models.Model):
    session = models.ForeignKey(RecommendationSession, on_delete=models.SET_NULL, null=True, blank=True)
    item_rec = models.ForeignKey(ItemRec, on_delete=models.SET_NULL, null=True, blank=True)
    content = models.ForeignKey(Content, on_delete=models.SET_NULL, null=True, blank=True)

    outcome_type = models.CharField(max_length=50)             # 예: "hr_drop"
    before = models.FloatField(null=True, blank=True)
    after = models.FloatField(null=True, blank=True)
    delta = models.FloatField(null=True, blank=True)
    effect = models.FloatField(null=True, blank=True)          # 0~1 스케일 등

    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = "outcome"
        indexes = [
            models.Index(fields=["outcome_type", "created_at"]),
        ]


# ─────────────────────────────────────────────────────────────────────
# 트리거→카테고리 우선순위 정책
# (주의: 필드명 priority / is_active 사용)
# ─────────────────────────────────────────────────────────────────────
class TriggerCategoryPolicy(models.Model):
    trigger = models.CharField(max_length=50)         # "stress_up" / "hr_low" / "hr_high" / "steps_low"
    category = models.CharField(max_length=50)        # "BREATHING" / "MEDITATION" / "WALK" / "OUTING" ...
    priority = models.IntegerField(default=1)         # 1이 최상위 노출
    is_active = models.BooleanField(default=True)

    # 운영 편의 옵션
    min_gw = models.IntegerField(null=True, blank=True)     # 임신 주차 하한
    max_gw = models.IntegerField(null=True, blank=True)     # 임신 주차 상한
    tod_bucket = models.CharField(                          # 시간대 버킷(선택)
        max_length=20, null=True, blank=True
    )  # "morning"/"day"/"evening"/"night"
    requires_location = models.BooleanField(default=False)  # 나들이 등 위치 필요 여부
    title = models.CharField(max_length=100, blank=True, default="")  # 노출명(관리자용/클라표시용)

    class Meta:
        db_table = "trigger_category_policy"
        unique_together = (("trigger", "category"),)
        indexes = [
            models.Index(fields=["trigger", "is_active", "priority"]),
            models.Index(fields=["min_gw", "max_gw"]),
            models.Index(fields=["tod_bucket"]),
        ]


# ─────────────────────────────────────────────────────────────────────
# user_tod_stats_daily (자정 스냅샷: 행=metric×stat, 열=버킷 v_*)
#  - 운영 DB에 이미 존재한다면 스키마를 맞추거나 managed=False로 바꿔 사용
# ─────────────────────────────────────────────────────────────────────
class UserTodStatsDaily(models.Model):
    user_ref = models.CharField(max_length=64, db_index=True)
    as_of = models.DateField()                                  # 스냅샷 기준 날짜(KST)
    metric = models.CharField(max_length=20)                    # 'hr' / 'stress'
    stat = models.CharField(max_length=20)                      # 'mean' / 'stddev'

    # 4시간 버킷(0..5)
    v_0_4 = models.FloatField(null=True, blank=True)
    v_4_8 = models.FloatField(null=True, blank=True)
    v_8_12 = models.FloatField(null=True, blank=True)
    v_12_16 = models.FloatField(null=True, blank=True)
    v_16_20 = models.FloatField(null=True, blank=True)
    v_20_24 = models.FloatField(null=True, blank=True)

    class Meta:
        db_table = "user_tod_stats_daily"
        unique_together = (("user_ref", "as_of", "metric", "stat"),)
        indexes = [
            models.Index(fields=["user_ref", "as_of"]),
            models.Index(fields=["metric", "stat"]),
        ]

    def __str__(self) -> str:
        return f"{self.user_ref} {self.as_of} {self.metric}/{self.stat}"


# ─────────────────────────────────────────────────────────────────────
# 장소 원장 테이블(원천 보관용) — 추천용 Content(type=PLACE)로 동기화 예정
# ─────────────────────────────────────────────────────────────────────
class PlaceBase(models.Model):
    name = models.CharField(max_length=200, db_index=True)
    category = models.CharField(max_length=100, blank=True, default="")
    address_road = models.CharField(max_length=300, blank=True, default="")
    address = models.CharField(max_length=300, blank=True, default="")
    lon = models.FloatField(null=True, blank=True)
    lat = models.FloatField(null=True, blank=True)
    is_active = models.BooleanField(default=True)
    source = models.CharField(max_length=50, blank=True, default="")
    created_at = models.DateTimeField(auto_now_add=True)
    raw = models.JSONField(default=dict, blank=True)

    class Meta:
        abstract = True


class PlaceInside(PlaceBase):
    postal_code = models.CharField(max_length=20, blank=True, default="")
    epsg5174_x = models.FloatField(null=True, blank=True)
    epsg5174_y = models.FloatField(null=True, blank=True)

    class Meta:
        db_table = "place_inside"
        indexes = [models.Index(fields=["category"])]


class PlaceOutside(PlaceBase):
    sido = models.CharField(max_length=40, blank=True, default="")
    sigungu = models.CharField(max_length=60, blank=True, default="")
    eupmyeondong = models.CharField(max_length=80, blank=True, default="")

    class Meta:
        db_table = "place_outside"
        indexes = [models.Index(fields=["sido", "sigungu"])]


# ─────────────────────────────────────────────────────────────────────
# 4시간 버킷 누적 걸음수 기준선(steps-check용)
# ─────────────────────────────────────────────────────────────────────
class UserStepsTodStatsDaily(models.Model):
    user_ref = models.CharField(max_length=64, db_index=True)
    d = models.DateField()                       # 기준 날짜(KST)
    bucket = models.IntegerField()               # 0..5
    cum_mu = models.FloatField()
    cum_sigma = models.FloatField()
    p20 = models.FloatField()

    class Meta:
        db_table = "user_steps_tod_stats_daily"
        unique_together = (("user_ref", "d", "bucket"),)
        indexes = [
            models.Index(fields=["user_ref", "d"]),
            models.Index(fields=["bucket"]),
        ]

class PlaceExposure(models.Model):
    user_ref = models.CharField(max_length=64, db_index=True)
    place_type = models.CharField(max_length=10)  # "inside" | "outside"
    place_id = models.IntegerField()
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = "place_exposure"
        indexes = [
            models.Index(fields=["user_ref", "place_type", "place_id"]),
            models.Index(fields=["created_at"]),
        ]

    def __str__(self) -> str:
        return f"{self.user_ref}:{self.place_type}:{self.place_id}"
    