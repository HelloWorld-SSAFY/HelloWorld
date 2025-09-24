# api/management/commands/ingest_daily_buckets.py
from django.core.management.base import BaseCommand, CommandError
from services.main_gateway import fetch_daily_buckets
from services.stats_ingest import upsert_daily_buckets_payload

class Command(BaseCommand):
    help = "메인서버 /health/api/wearable/daily-buckets 결과를 user_tod_stats_daily 에 저장"

    def add_arguments(self, parser):
        parser.add_argument("--date", required=True, help="KST 날짜 (YYYY-MM-DD)")

    def handle(self, *args, **opts):
        date_str = opts["date"]
        payload = fetch_daily_buckets(date_str)
        n = upsert_daily_buckets_payload(payload)
        self.stdout.write(self.style.SUCCESS(f"saved={n} date={date_str}"))
