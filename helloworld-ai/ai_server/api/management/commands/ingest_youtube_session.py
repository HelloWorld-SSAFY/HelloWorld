# api/management/commands/ingest_youtube_session.py
from django.core.management.base import BaseCommand, CommandError
from services.youtube_ingest import ingest_youtube_to_session

class Command(BaseCommand):
    help = "YouTube에서 영상 후보를 모아 지정 세션의 ExposureCandidate로 적재합니다."

    def add_arguments(self, parser):
        parser.add_argument("--session", required=True, help="RecommendationSession.id")
        parser.add_argument("--category", required=True, choices=["MEDITATION","YOGA"])
        parser.add_argument("--max", type=int, default=30)
        parser.add_argument("--query", action="append")
        parser.add_argument("--region", default="KR")
        parser.add_argument("--lang", default="ko")

    def handle(self, *args, **opts):
        try:
            created, skipped = ingest_youtube_to_session(
                session_id=opts["session"],
                category=opts["category"],
                max_total=opts["max"],
                queries=opts.get("query"),
                region=opts["region"],
                lang=opts["lang"],
            )
            self.stdout.write(self.style.SUCCESS(
                f"Done. created={created}, skipped={skipped}"
            ))
        except Exception as e:
            raise CommandError(str(e))
