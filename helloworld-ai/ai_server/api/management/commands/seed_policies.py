# api/management/commands/seed_policies.py
from django.core.management.base import BaseCommand
from django.db import transaction
from api.models import TriggerCategoryPolicy as TCP

POLICIES = [
    # stress_up
    {"trigger":"stress_up","category":"BREATHING","priority":3,"is_active":True,"title":"호흡"},
    {"trigger":"stress_up","category":"MUSIC","priority":2,"is_active":True,"title":"음악"},
    {"trigger":"stress_up","category":"MEDITATION","priority":1,"is_active":True,"title":"명상"},
    # hr_low
    {"trigger":"hr_low","category":"BREATHING","priority":1,"is_active":True,"title":"호흡"},
    {"trigger":"hr_low","category":"YOGA","priority":2,"is_active":True,"title":"요가"},
    {"trigger":"hr_low","category":"WALK","priority":3,"is_active":True,"title":"걷기"},
    # hr_high
    {"trigger":"hr_high","category":"BREATHING","priority":1,"is_active":True,"title":"호흡"},
    {"trigger":"hr_high","category":"MEDITATION","priority":2,"is_active":True,"title":"명상"},
    {"trigger":"hr_high","category":"YOGA","priority":3,"is_active":True,"title":"요가"},
    # steps_low
    {"trigger":"steps_low","category":"OUTING","priority":1,"is_active":True,"requires_location":True,"title":"나들이"},
    {"trigger":"steps_low","category":"WALK","priority":2,"is_active":True,"title":"걷기"},
    # {"trigger":"steps_low","category":"STRETCHING","priority":3,"is_active":True,"title":"스트레칭"},
]
# ... (import/ POLICIES 동일)
class Command(BaseCommand):
    help = "Seed TriggerCategoryPolicy (idempotent)."

    def add_arguments(self, p):
        p.add_argument("--deactivate-missing", action="store_true",
                       help="POLICIES에 없는 기존 정책 비활성화")
        p.add_argument("--reset", action="store_true",
                       help="전체 삭제 후 POLICIES로 재삽입")

    @transaction.atomic
    def handle(self, *args, **opts):
        reset = opts.get("reset", False)

        if reset:
            # 하드 리셋(전부 삭제)
            n = TCP.objects.all().delete()
            self.stdout.write(self.style.WARNING(f"Reset: deleted {n[0]} rows"))

        seen = set()
        for row in POLICIES:
            obj, _ = TCP.objects.update_or_create(
                trigger=row["trigger"], category=row["category"],
                defaults={k: v for k, v in row.items() if k not in ("trigger", "category")}
            )
            seen.add((obj.trigger, obj.category))

        if not reset and opts.get("deactivate_missing", False):
            for existed in TCP.objects.all():
                key = (existed.trigger, existed.category)
                if key not in seen and existed.is_active:
                    existed.is_active = False
                    existed.save(update_fields=["is_active"])

        self.stdout.write(self.style.SUCCESS(f"Seeded {len(seen)} policies"))
