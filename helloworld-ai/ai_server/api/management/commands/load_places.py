# api/management/commands/load_places.py
from django.core.management.base import BaseCommand
from django.db import transaction
from api.models import Place
import pandas as pd

class Command(BaseCommand):
    def add_arguments(self, p):
        p.add_argument("--inside", default="./data/place_inside.xlsx")
        p.add_argument("--outside", default="./data/place_outside.xlsx")
        p.add_argument("--mode", choices=["upsert","insert"], default="upsert")

    def handle(self, *a, **o):
        total = 0

        def f2f(x):  # float to float or None
            s = str(x).strip()
            return float(s) if s and s.lower() not in ["nan","none"] else None

        with transaction.atomic():
            # inside
            try:
                df = pd.read_excel(o["inside"]).fillna("")
                for r in df.to_dict("records"):
                    defaults = dict(
                        category=r.get("업태구분명",""),
                        address_road=r.get("도로명전체주소",""),
                        address=r.get("소재지전체주소",""),
                        postal_code=str(r.get("도로명우편번호","") or ""),
                        lon=f2f(r.get("lon")), lat=f2f(r.get("lat")),
                        sido="", sigungu="", eupmyeondong="",
                        source="inside_xlsx", is_active=True, raw=r,
                    )
                    if o["mode"]=="upsert":
                        Place.objects.update_or_create(
                            kind="inside", name=r.get("사업장명",""), address=defaults["address"],
                            defaults=defaults
                        )
                    else:
                        Place.objects.get_or_create(
                            kind="inside", name=r.get("사업장명",""), address=defaults["address"],
                            defaults=defaults
                        )
                    total += 1
            except Exception:
                pass

            # outside
            try:
                df = pd.read_excel(o["outside"]).fillna("")
                for r in df.to_dict("records"):
                    defaults = dict(
                        category=r.get("MLSFC_NM",""),
                        address_road=r.get("FCLTY_ROAD_NM_ADDR",""),
                        address=r.get("LNM_ADDR","") or r.get("adress",""),
                        postal_code="",
                        lon=f2f(r.get("FCLTY_LO")), lat=f2f(r.get("FCLTY_LA")),
                        sido=r.get("CTPRVN_NM",""), sigungu=r.get("SIGNGU_NM",""),
                        eupmyeondong=r.get("LEGALDONG_NM",""),
                        source=r.get("ORIGIN_NM","outside_xlsx"),
                        is_active=True, raw=r,
                    )
                    name = r.get("FCLTY_NM","") or r.get("name","")
                    if o["mode"]=="upsert":
                        Place.objects.update_or_create(
                            kind="outside", name=name, address=defaults["address"],
                            defaults=defaults
                        )
                    else:
                        Place.objects.get_or_create(
                            kind="outside", name=name, address=defaults["address"],
                            defaults=defaults
                        )
                    total += 1
            except Exception:
                pass

        self.stdout.write(self.style.SUCCESS(f"Processed: {total}"))
