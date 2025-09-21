# api/serializers.py
from rest_framework import serializers

# ─────────────────────────────────────────────────────────────
# Recommend (요청/응답) — 최신 스펙 반영
#  - 요청: session_id 필수, category(MEDITATION|YOGA|MUSIC), top_k 옵션
#  - 응답: items 리스트(Top-K), 각 아이템에 score/thumbnail/reason 포함
#  - 기존 단일 'item' 필드는 폐지(호환 불가)
# ─────────────────────────────────────────────────────────────

class RecommendPreferencesSer(serializers.Serializer):
    lang = serializers.CharField(required=False)
    duration_min = serializers.IntegerField(required=False, min_value=1, max_value=60)
    duration_max = serializers.IntegerField(required=False, min_value=1, max_value=180)
    music_provider = serializers.ChoiceField(required=False, choices=["spotify", "youtube"])
    allow_voice_guidance = serializers.BooleanField(required=False)

class RecommendContextSer(serializers.Serializer):
    pregnancy_week = serializers.IntegerField(required=False, min_value=0, max_value=45)
    trimester = serializers.IntegerField(required=False, min_value=1, max_value=3)
    risk_flags = serializers.ListField(child=serializers.CharField(), required=False)
    symptoms_today = serializers.ListField(child=serializers.CharField(), required=False)
    preferences = RecommendPreferencesSer(required=False)
    taboo_tags = serializers.ListField(child=serializers.CharField(), required=False)
    locale = serializers.CharField(required=False)
    tz = serializers.CharField(required=False)

class RecommendRequestSer(serializers.Serializer):
    user_ref = serializers.CharField()
    session_id = serializers.CharField()
    category = serializers.CharField(help_text="MEDITATION | YOGA | MUSIC")
    top_k = serializers.IntegerField(required=False, min_value=1, max_value=5, help_text="기본 3")
    ts = serializers.DateTimeField(required=False)
    q = serializers.CharField(required=False, help_text="MUSIC일 때 검색 키워드(옵션)")
    context = RecommendContextSer(required=False)

    # ↓ 하위호환 입력(점진 폐지 예정)
    gw = serializers.IntegerField(required=False, min_value=0, max_value=45, help_text="임신 주차(legacy)")
    ctx = serializers.JSONField(required=False, help_text="임의 컨텍스트(legacy)")

class RecommendItemSer(serializers.Serializer):
    content_id = serializers.IntegerField()
    title = serializers.CharField()
    url = serializers.URLField()
    thumbnail = serializers.URLField(required=False, allow_blank=True)
    rank = serializers.IntegerField(min_value=1)
    score = serializers.FloatField(required=False, allow_null=True)
    reason = serializers.CharField(required=False)

class RecommendResponseSer(serializers.Serializer):
    ok = serializers.BooleanField()
    session_id = serializers.CharField()
    category = serializers.CharField()
    items = RecommendItemSer(many=True)
