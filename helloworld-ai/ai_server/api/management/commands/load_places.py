# api/management/commands/load_places.py
from django.core.management.base import BaseCommand
from django.db import transaction
from api.models import PlaceInside, PlaceOutside
import pandas as pd
import os, traceback

class Command(BaseCommand):
    def add_arguments(self, p):
        p.add_argument("--inside", default="./data/place_inside.xlsx")
        p.add_argument("--outside", default="./data/place_outside.xlsx")
        p.add_argument("--mode", choices=["upsert","insert"], default="upsert")

    # helpers
    def _f2f(self, x):
        s = str(x).strip()
        return float(s) if s and s.lower() not in ("nan","none") else None

    def _abspath_log(self, label, path):
        ap = os.path.abspath(path)
        self.stdout.write(f"[path:{label}] {path} -> {ap} | exists={os.path.exists(ap)}")
        return ap

    def handle(self, *a, **o):
        inside_path = self._abspath_log("inside", o["inside"])
        outside_path = self._abspath_log("outside", o["outside"])
        total = 0

        with transaction.atomic():
            # ── 실내 inside ─────────────────────────────────────────
            try:
                df = pd.read_excel(inside_path, engine="openpyxl").fillna("")
                self.stdout.write(f"[inside] rows={len(df)}")
                for r in df.to_dict("records"):
                    name = r.get("사업장명","")
                    address = r.get("소재지전체주소","")
                    defaults = dict(
                        name=name,
                        category=r.get("업태구분명",""),
                        address_road=r.get("도로명전체주소",""),
                        address=address,
                        postal_code=str(r.get("도로명우편번호","") or ""),
                        lat=self._f2f(r.get("lat")),
                        lon=self._f2f(r.get("lon")),
                        epsg5174_x=self._f2f(r.get("epsg5174_x")),
                        epsg5174_y=self._f2f(r.get("epsg5174_y")),
                        is_active=True,
                        source="inside_xlsx",
                        raw=r,
                    )
                    if o["mode"] == "upsert":
                        PlaceInside.objects.update_or_create(
                            name=name, address=address, defaults=defaults
                        )
                    else:
                        PlaceInside.objects.get_or_create(
                            name=name, address=address, defaults=defaults
                        )
                    total += 1
            except Exception:
                self.stderr.write("[inside] failed:\n" + traceback.format_exc())

            # ── 실외 outside ────────────────────────────────────────
            try:
                df = pd.read_excel(outside_path, engine="openpyxl").fillna("")
                self.stdout.write(f"[outside] rows={len(df)}")
                for r in df.to_dict("records"):
                    name = r.get("FCLTY_NM","") or r.get("name","")
                    address = r.get("LNM_ADDR","") or r.get("adress","")
                    defaults = dict(
                        name=name,
                        category=r.get("MLSFC_NM",""),
                        address_road=r.get("FCLTY_ROAD_NM_ADDR",""),
                        address=address,
                        lat=self._f2f(r.get("FCLTY_LA")),
                        lon=self._f2f(r.get("FCLTY_LO")),
                        sido=r.get("CTPRVN_NM",""),
                        sigungu=r.get("SIGNGU_NM",""),
                        eupmyeondong=r.get("LEGALDONG_NM",""),
                        is_active=True,
                        source=r.get("ORIGIN_NM","outside_xlsx"),
                        raw=r,
                    )
                    if o["mode"] == "upsert":
                        PlaceOutside.objects.update_or_create(
                            name=name, address=address, defaults=defaults
                        )
                    else:
                        PlaceOutside.objects.get_or_create(
                            name=name, address=address, defaults=defaults
                        )
                    total += 1
            except Exception:
                self.stderr.write("[outside] failed:\n" + traceback.format_exc())

        self.stdout.write(self.style.SUCCESS(f"Processed: {total}"))
