from __future__ import annotations
import uuid
from django.db import models
from django.core.validators import MinValueValidator, MaxValueValidator


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
# + 추천 고도화를 위한 메타(길이/언어/보이스/채널품질/pre_score_base) 추가
# ─────────────────────────────────────────────────────────────────────
class Content(models.Model):
    provider = models.CharField(max_length=50, blank=True, default="")      # 예: "YOUTUBE" / "SPOTIFY" / "INAPP"
    external_id = models.CharField(max_length=200, blank=True, default="")  # 공급자 측 식별자(yt:..., sp:...)
    category = models.CharField(max_length=50)                               # BREATHING / MEDITATION / OUTING 등
    title = models.CharField(max_length=200, blank=True, default="")
    url = models.URLField(max_length=500, blank=True, default="")
    # 썸네일(YouTube/Spotify 앨범커버 등)
    thumbnail_url = models.URLField(max_length=500, blank=True, default="")
    is_active = models.BooleanField(default=True)

    # 장소형 컨텐츠용 필드
    type = models.CharField(max_length=10, default="CONTENT")  # CONTENT / PLACE
    lat = models.DecimalField(max_digits=9, decimal_places=6, null=True, blank=True)
    lng = models.DecimalField(max_digits=9, decimal_places=6, null=True, blank=True)
    tags = models.JSONField(null=True, blank=True)             # ["SHADE","STROLLER_OK"]
    safety_flags = models.JSONField(null=True, blank=True)     # ["RESTROOM_NEARBY"]

    # ── 추천 메타(컨텍스트 부스트/품질) ───────────────────────────────
    length_sec = models.IntegerField(null=True, blank=True, help_text="콘텐츠 길이(초)")
    lang = models.CharField(max_length=10, blank=True, default="", help_text="ko/en 등")
    voice_guided = models.BooleanField(null=True, blank=True, help_text="음성 가이드 여부")
    channel_quality = models.FloatField(
        null=True, blank=True,
        validators=[MinValueValidator(0.0), MaxValueValidator(1.0)],
        help_text="채널/제공자 품질(0~1)"
    )
    pre_score_base = models.FloatField(
        null=True, blank=True,
        validators=[MinValueValidator(0.0), MaxValueValidator(1.0)],
        help_text="배치 산출 기본 품질 점수(0~1)"
    )

    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = "content"
        unique_together = (("provider", "external_id"),)
        indexes = [
            models.Index(fields=["type"]),
            models.Index(fields=["lat", "lng"]),
            models.Index(fields=["category"]),
            models.Index(fields=["is_active"]),
            # 추천 메타 인덱스
            models.Index(fields=["lang"]),
            models.Index(fields=["length_sec"]),
            models.Index(fields=["voice_guided"]),
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
    context = models.JSONField(default=dict, blank=True)                         # 버킷/룰버전/사유/유저컨텍스트 등
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = "recommendation_session"
        indexes = [
            models.Index(fields=["user_ref", "created_at"]),
            models.Index(fields=["mode", "created_at"]),
            models.Index(fields=["trigger", "created_at"]),
        ]

    def __str__(self) -> str:
        return f"session:{self.id} user:{self.user_ref} mode:{self.mode}"

    # views.py에서 사용하는 헬퍼(프로젝트마다 존재 유무 달라 try/except로 호출)
    def set_context(self, ctx: dict, save: bool = False):
        self.context = ctx or {}
        if save:
            self.save(update_fields=["context"])

    def update_context(self, patch: dict, save: bool = False):
        base = self.context or {}
        base.update(patch or {})
        self.context = base
        if save:
            self.save(update_fields=["context"])


# ─────────────────────────────────────────────────────────────────────
# 노출 후보(전처리 풀 로깅: 금기 제외 후 pre_score/x_item_vec 보관)
# ─────────────────────────────────────────────────────────────────────
class ExposureCandidate(models.Model):
    session = models.ForeignKey(RecommendationSession, on_delete=models.CASCADE, related_name="candidates")
    content = models.ForeignKey(Content, on_delete=models.CASCADE)
    pre_score = models.FloatField(null=True, blank=True)  # 0~1 (없으면 0.5로 처리)
    chosen_flag = models.BooleanField(default=False)
    x_item_vec = models.JSONField(default=dict, blank=True)            # 임베딩/피처 등 (thumb_url 포함 가능)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = "exposure_candidate"
        indexes = [
            models.Index(fields=["session"]),
            models.Index(fields=["content"]),
            models.Index(fields=["created_at"]),
        ]


# ─────────────────────────────────────────────────────────────────────
# 최종 추천 N개 (랭크/점수/사유)
#  - recommender.py 호환을 위해 user_ref / external_id는 선택, rank는 옵션
# ─────────────────────────────────────────────────────────────────────
class ItemRec(models.Model):
    session = models.ForeignKey(RecommendationSession, on_delete=models.CASCADE, related_name="items")
    content = models.ForeignKey(Content, on_delete=models.CASCADE)

    # 호환 필드
    user_ref = models.CharField(max_length=64, blank=True, default="")          # 추천 당시 유저(옵션)
    external_id = models.CharField(max_length=200, blank=True, default="")      # 추적용 (예: "sp:trk:..", 옵션)
    rank = models.IntegerField(null=True, blank=True)                            # 없으면 저장 시 자동 채움

    score = models.FloatField(null=True, blank=True)
    reason = models.CharField(max_length=200, blank=True, default="")
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = "item_rec"
        indexes = [
            models.Index(fields=["session", "rank"]),
            models.Index(fields=["session", "created_at"]),
        ]
        unique_together = (("session", "content"),)

    def save(self, *args, **kwargs):
        # rank 미지정 시 세션 내 다음 순번 자동 부여
        if self.rank is None and self.session_id:
            last = ItemRec.objects.filter(session_id=self.session_id).order_by("-rank").first()
            self.rank = (last.rank or 0) + 1 if last and last.rank else 1
        super().save(*args, **kwargs)


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

    outcome_type = models.CharField(max_length=50)             # 예: "hr_drop" / "self_report"
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
# 개인화 통계(전역/유저) — Thompson Sampling / CTS 가중치에 사용
#  - accepts/completes/effects_sum: 레거시/집계 호환 필드 유지
#  - alpha/beta: Beta 분포 파라미터(보상 r∈[0,1] 에 따라 업데이트)
# ─────────────────────────────────────────────────────────────────────
class ContentStat(models.Model):
    content = models.OneToOneField(Content, on_delete=models.CASCADE, related_name="stat")
    # 레거시 카운트(집계용)
    accepts = models.IntegerField(default=0)
    completes = models.IntegerField(default=0)
    effects_sum = models.FloatField(default=0.0)
    # Thompson Sampling 파라미터(전역 prior)
    alpha = models.FloatField(default=1.0, validators=[MinValueValidator(0.0)])
    beta = models.FloatField(default=1.0, validators=[MinValueValidator(0.0)])

    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = "content_stat"
        indexes = [
            models.Index(fields=["updated_at"]),
        ]

    def __str__(self) -> str:
        return f"ContentStat({self.content_id}) a={self.accepts} c={self.completes} e={self.effects_sum:.2f} | α={self.alpha:.2f}, β={self.beta:.2f}"

    # ---- 헬퍼: 보상 r∈[0,1]으로 Beta 업데이트 ----
    def add_reward(self, r: float):
        r = max(0.0, min(1.0, r if r is not None else 0.0))
        self.alpha += r
        self.beta += (1.0 - r)

    # ---- 헬퍼: 기본 가중으로 r 계산 후 업데이트(옵션) ----
    def apply_feedback(self, accept: bool = False, complete: bool = False, effect_value: float | None = None,
                       w_accept: float = 0.6, w_complete: float = 0.2, w_effect: float = 0.2):
        if accept:
            self.accepts += 1
        if complete:
            self.completes += 1
        if effect_value is not None:
            # effect_value는 0~1 스케일 가정
            v = max(0.0, min(1.0, effect_value))
            self.effects_sum += v
        else:
            v = 0.0

        r = (w_accept * (1.0 if accept else 0.0)) + (w_complete * (1.0 if complete else 0.0)) + (w_effect * v)
        self.add_reward(r)


class UserContentStat(models.Model):
    user_ref = models.CharField(max_length=64, db_index=True)
    content = models.ForeignKey(Content, on_delete=models.CASCADE, related_name="user_stats")

    # 레거시 카운트(집계용)
    accepts = models.IntegerField(default=0)
    completes = models.IntegerField(default=0)
    effects_sum = models.FloatField(default=0.0)

    # Thompson Sampling 파라미터(개인 prior)
    alpha = models.FloatField(default=1.0, validators=[MinValueValidator(0.0)])
    beta = models.FloatField(default=1.0, validators=[MinValueValidator(0.0)])

    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = "user_content_stat"
        unique_together = (("user_ref", "content"),)
        indexes = [
            models.Index(fields=["user_ref", "updated_at"]),
            models.Index(fields=["content"]),
        ]

    def __str__(self) -> str:
        return f"UserContentStat({self.user_ref}, {self.content_id}) | α={self.alpha:.2f}, β={self.beta:.2f}"

    def add_reward(self, r: float):
        r = max(0.0, min(1.0, r if r is not None else 0.0))
        self.alpha += r
        self.beta += (1.0 - r)

    def apply_feedback(self, accept: bool = False, complete: bool = False, effect_value: float | None = None,
                       w_accept: float = 0.6, w_complete: float = 0.2, w_effect: float = 0.2):
        if accept:
            self.accepts += 1
        if complete:
            self.completes += 1
        if effect_value is not None:
            v = max(0.0, min(1.0, effect_value))
            self.effects_sum += v
        else:
            v = 0.0

        r = (w_accept * (1.0 if accept else 0.0)) + (w_complete * (1.0 if complete else 0.0)) + (w_effect * v)
        self.add_reward(r)


# ─────────────────────────────────────────────────────────────────────
# 트리거→카테고리 우선순위 정책
# - 레거시 호환 필드(trigger_key/rank/enabled)까지 포함
# ─────────────────────────────────────────────────────────────────────
class TriggerCategoryPolicy(models.Model):
    # 신(표준) 필드
    trigger = models.CharField(max_length=50)           # "stress_up" / "hr_low" / "hr_high" / "steps_low"
    category = models.CharField(max_length=50)          # "BREATHING" / "MEDITATION" / "WALK" / "OUTING" ...
    priority = models.IntegerField(default=1)           # 1이 최상위 노출
    is_active = models.BooleanField(default=True)

    # 운영 편의 옵션
    min_gw = models.IntegerField(null=True, blank=True)     # 임신 주차 하한
    max_gw = models.IntegerField(null=True, blank=True)     # 임신 주차 상한
    tod_bucket = models.CharField(max_length=20, null=True, blank=True)  # "morning"/"day"/"evening"/"night"
    requires_location = models.BooleanField(default=False)  # 나들이 등 위치 필요 여부
    title = models.CharField(max_length=100, blank=True, default="")     # 노출명(관리자용/클라표시용)

    # 레거시 호환(ORM 필터에서 사용하므로 실제 필드 필요)
    trigger_key = models.CharField(max_length=50, blank=True, default="")  # = trigger
    rank = models.IntegerField(default=1)                                   # = priority
    enabled = models.BooleanField(default=True)                             # = is_active

    class Meta:
        db_table = "trigger_category_policy"
        unique_together = (("trigger", "category"),)
        indexes = [
            models.Index(fields=["trigger", "is_active", "priority"]),
            models.Index(fields=["trigger_key", "enabled", "rank"]),
            models.Index(fields=["min_gw", "max_gw"]),
            models.Index(fields=["tod_bucket"]),
        ]

    def save(self, *args, **kwargs):
        # 필드 동기화(양방향)
        # 표준 → 레거시
        if self.trigger and not self.trigger_key:
            self.trigger_key = self.trigger
        if self.priority and (self.rank is None or self.rank == 0):
            self.rank = self.priority
        self.enabled = bool(self.is_active)

        # 레거시 → 표준 (레거시만 들어온 경우)
        if not self.trigger and self.trigger_key:
            self.trigger = self.trigger_key
        if (self.priority is None or self.priority == 0) and self.rank:
            self.priority = self.rank
        self.is_active = bool(self.enabled)

        super().save(*args, **kwargs)


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


# ─────────────────────────────────────────────────────────────────────
# NEW: 추천/장소 제공 결과 통합 로그 (recommend + places 모두 저장)
# ─────────────────────────────────────────────────────────────────────
class RecommendationDelivery(models.Model):
    id = models.BigAutoField(primary_key=True)
    # 한 요청에서 내려간 아이템들을 묶는 식별자
    request_id = models.UUIDField(default=uuid.uuid4, db_index=True)
    # 누가 요청했는지
    user_ref = models.CharField(max_length=64, db_index=True)

    # 어떤 세션(있으면)과 연관되는지
    session = models.ForeignKey(
        "RecommendationSession",
        null=True, blank=True,
        on_delete=models.SET_NULL,
        related_name="deliveries"
    )

    # 4개 카테고리로 조회 용이하게
    category = models.CharField(max_length=20, db_index=True)  # MEDITATION|MUSIC|YOGA|OUTING
    # 아이템 종류: 콘텐츠 | 장소
    item_kind = models.CharField(max_length=10)  # "content" | "place"

    # 공통 메타
    title = models.CharField(max_length=255)
    rank = models.IntegerField(default=1)
    score = models.FloatField(null=True, blank=True)
    reason = models.CharField(max_length=255, blank=True, default="")
    trigger = models.CharField(max_length=64, blank=True, default="")      # 세션 트리거 기록용
    requested_by = models.CharField(max_length=32, default="mobile")       # mobile|auto|server
    context = models.JSONField(default=dict, blank=True)
    created_at = models.DateTimeField(auto_now_add=True, db_index=True)

    # 콘텐츠 전용 필드
    content = models.ForeignKey("Content", null=True, blank=True, on_delete=models.SET_NULL)
    url = models.URLField(blank=True, default="")
    thumbnail = models.URLField(blank=True, default="")

    # 장소 전용 필드
    place_type = models.CharField(max_length=10, blank=True, default="")   # inside|outside
    place_id = models.IntegerField(null=True, blank=True)
    lat = models.FloatField(null=True, blank=True)
    lng = models.FloatField(null=True, blank=True)
    address = models.CharField(max_length=255, blank=True, default="")
    distance_km = models.FloatField(null=True, blank=True)
    weather_gate = models.CharField(max_length=10, blank=True, default="") # OUTDOOR|INDOOR|...

    class Meta:
        db_table = "recommend_delivery"
        indexes = [
            models.Index(fields=["user_ref", "category", "-created_at"]),
            models.Index(fields=["session", "created_at"]),
            models.Index(fields=["request_id"]),
        ]

    def __str__(self) -> str:
        return f"{self.user_ref}/{self.category}#{self.rank} ({self.item_kind})"
