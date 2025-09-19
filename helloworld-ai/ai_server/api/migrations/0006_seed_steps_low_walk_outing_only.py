# api/migrations/00xx_seed_steps_low_walk_outing_only.py
from django.db import migrations

ALLOWED = [
    ("steps_low", "WALK",   1, False),
    ("steps_low", "OUTING", 2, True),
]

def seed(apps, schema_editor):
    Policy = apps.get_model("api", "TriggerCategoryPolicy")

    # 1) steps_low 전체 중 ALLOWED 2개를 제외한 것들은 비활성화 (rename 하지 않음)
    Policy.objects.filter(trigger="steps_low")\
        .exclude(category__in=[c for _, c, _, _ in ALLOWED])\
        .update(is_active=False)

    # 2) 필요한 두 개만 upsert + 활성화
    for trig, cat, prio, req_loc in ALLOWED:
        Policy.objects.update_or_create(
            trigger=trig, category=cat,
            defaults={
                "priority": prio,
                "is_active": True,
                "requires_location": req_loc,
                "title": f"{trig} → {cat}",
            },
        )

    # (선택) 혹시 남아있는 오타 'WALKING'은 그냥 비활성화만 하고 삭제/rename는 안 함
    Policy.objects.filter(trigger="steps_low", category="WALKING").update(is_active=False)

def unseed(apps, schema_editor):
    # 되돌릴 때도 ALLOWED 두 개만 비활성화 (다른 데이터는 건드리지 않음)
    Policy = apps.get_model("api", "TriggerCategoryPolicy")
    Policy.objects.filter(trigger="steps_low", category__in=["WALK", "OUTING"]).update(is_active=False)

class Migration(migrations.Migration):
    # 반드시 TriggerCategoryPolicy가 만들어진 '직전' 마이그레이션에 의존하게 변경하세요.
    dependencies = [
        ("api", "0005_placeinside_placeoutside_userstepstodstatsdaily_and_more"),
    ]

    # 트랜잭션 전역 abort를 피하려고 atomic=False 설정
    atomic = False

    operations = [
        migrations.RunPython(seed, reverse_code=unseed),
    ]
