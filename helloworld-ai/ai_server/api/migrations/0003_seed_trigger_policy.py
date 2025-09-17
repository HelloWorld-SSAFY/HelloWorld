from django.db import migrations

def seed(apps, schema_editor):
    Policy = apps.get_model("api", "TriggerCategoryPolicy")
    rows = [
        # 스트레스 지수 상승 > 호흡, 음악, 명상
        ("stress_up", "BREATHING", 1, True),
        ("stress_up", "MUSIC", 2, True),
        ("stress_up", "MEDITATION", 3, True),
        # 심박수 저하 > 호흡, 명상, 요가
        ("hr_low", "BREATHING", 1, True),
        ("hr_low", "MEDITATION", 2, True),
        ("hr_low", "YOGA", 3, True),
        # 심박수 상승 > 호흡, 명상 영상
        ("hr_high", "BREATHING", 1, True),
        ("hr_high", "MEDITATION_VIDEO", 2, True),
        # 걸음수 저하 > 나들이, 걷기, 스트레칭
        ("steps_low", "OUTING", 1, True),
        ("steps_low", "WALKING", 2, True),
        ("steps_low", "STRETCHING", 3, True),
    ]
    for trigger, category, priority, is_active in rows:
        Policy.objects.update_or_create(
            trigger=trigger, category=category,
            defaults={"priority": priority, "is_active": is_active},
        )

def unseed(apps, schema_editor):
    Policy = apps.get_model("api", "TriggerCategoryPolicy")
    Policy.objects.filter(trigger__in=["stress_up","hr_low","hr_high","steps_low"]).delete()

class Migration(migrations.Migration):
    dependencies = [("api", "0002_triggercategorypolicy_max_gw_and_more")]
    operations = [migrations.RunPython(seed, unseed)]
