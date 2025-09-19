# api/serializers.py
from rest_framework import serializers

class RecommendRequestSer(serializers.Serializer):
    user_ref = serializers.CharField()
    category = serializers.CharField()
    context = serializers.DictField(required=False)

class RecommendResponseSer(serializers.Serializer):
    ok = serializers.BooleanField()
    session_id = serializers.CharField()
    category = serializers.CharField()
    item = serializers.DictField()
    reason = serializers.CharField()
    candidates = serializers.ListField(child=serializers.DictField(), required=False)
