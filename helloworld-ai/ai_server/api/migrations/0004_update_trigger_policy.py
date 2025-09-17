from django.db import migrations

TARGET = {
    "stress_up": ["BREATHING", "MUSIC", "MEDITATION"],
    "hr_low":    ["BREATHING", "YOGA", "WALKING"],
    "hr_high":   ["BREATHING", "MEDITATION", "YOGA"],
}

def apply(apps, schema_editor):
    P = apps.get_model("api", "TriggerCategoryPolicy")
    for trig, cats in TARGET.items():
        # 우선순위/활성화 업서트
        for i, cat in enumerate(cats, start=1):
            obj, created = P.objects.get_or_create(
                trigger=trig, category=cat,
                defaults={"priority": i, "is_active": True}
            )
            if not created:
                obj.priority = i
                obj.is_active = True
                obj.save()
        # 지정 목록 이외는 비활성화
        P.objects.filter(trigger=trig).exclude(category__in=cats).update(is_active=False)

def rollback(apps, schema_editor):
    # 필요시 되돌림 로직; 지금은 noop
    pass

class Migration(migrations.Migration):
    dependencies = [
        ("api", "0003_seed_trigger_policy"),  # 예: "0003_seed_trigger_policy"
    ]
    operations = [
        migrations.RunPython(apply, reverse_code=rollback),
    ]
