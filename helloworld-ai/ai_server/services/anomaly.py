# services/anomaly.py
from __future__ import annotations
from dataclasses import dataclass
from datetime import datetime, timezone, timedelta, date
from typing import Dict, Tuple, Protocol, Optional, runtime_checkable
import uuid
import math
import os, logging

# ──────────────────────────────────────────────────────────────────────────────
# KST / 버킷
# ──────────────────────────────────────────────────────────────────────────────
KST = timezone(timedelta(hours=9))

def to_kst(dt_utc: datetime) -> datetime:
    if dt_utc.tzinfo is None:
        dt_utc = dt_utc.replace(tzinfo=timezone.utc)
    return dt_utc.astimezone(KST)

def bucket_index_4h(kst_dt: datetime) -> int:
    return kst_dt.hour // 4  # 0..5: [0-4), [4-8), ...

# ──────────────────────────────────────────────────────────────────────────────
# 로깅/디버그 스위치
# ──────────────────────────────────────────────────────────────────────────────
log = logging.getLogger(__name__)
ANOMALY_DEBUG = os.getenv("ANOMALY_DEBUG", "false").lower() in ("1", "true", "yes")

# ──────────────────────────────────────────────────────────────────────────────
# 기준선 공급자
# ──────────────────────────────────────────────────────────────────────────────
@runtime_checkable
class StatsProvider(Protocol):
    def get_bucket_stats(
        self, user_ref: str, as_of: date, metric: str, bucket_idx: int
    ) -> Optional[Tuple[float, float]]:
        ...

# ──────────────────────────────────────────────────────────────────────────────
# 설정
# ──────────────────────────────────────────────────────────────────────────────
@dataclass(frozen=True)
class AnomalyConfig:
    # Z 임계
    z_restrict: float = 2.5
    z_emergency: float = 5.0

    # HR 절대값 임계(Restrict 전용, 연속 3틱)
    hr_inst_restrict_high: int = 150
    hr_inst_restrict_low:  int = 45

    # 연속 틱 요구 개수 / 최대 간격(초)
    consecutive_required: int = 3
    max_gap_sec: int = 30  # 10초 간격 가정, 여유 30초

    supported_metrics: Tuple[str, ...] = ("hr", "stress")

    # 쿨다운(초)
    restrict_cooldown_sec: int = 180
    emergency_cooldown_sec: int = 3600

# ──────────────────────────────────────────────────────────────────────────────
# 상태 / 결과
# ──────────────────────────────────────────────────────────────────────────────
@dataclass
class UserState:
    # 응급 카운터: |Z|>=5 (HR/Stress)
    emg_hr_z_c: int = 0
    emg_stress_z_c: int = 0

    # Restrict 카운터(심박 ↑/↓ / 스트레스 |Z|)
    res_hr_high_c: int = 0      # HR_Z>=2.5  또는 HR_inst>=150
    res_hr_low_c:  int = 0      # HR_Z<=-2.5 또는 HR_inst<=45
    res_stress_c:  int = 0      # |STRESS_Z|>=2.5

    # 쿨다운
    restrict_until: Optional[datetime] = None
    emergency_until: Optional[datetime] = None

    # 연속성 판단
    last_ts: Optional[datetime] = None

@dataclass
class AnomalyResult:
    ok: bool
    anomaly: bool
    risk_level: str         # "low" | "high" | "critical"
    mode: str               # "normal" | "restrict" | "emergency" | "cooldown"
    reasons: Tuple[str, ...]
    trigger: Optional[str] = None  # "hr_high" | "hr_low" | "stress_up"
    z: Optional[float] = None

    # cooldown payload
    cooldown_min: Optional[int] = None
    cooldown_source: Optional[str] = None  # "restrict" | "emergency"
    cooldown_until: Optional[datetime] = None

# ──────────────────────────────────────────────────────────────────────────────
# 탐지기
# ──────────────────────────────────────────────────────────────────────────────
class AnomalyDetector:
    """
    __init__ 호환성:
      - 신규: AnomalyDetector(config=<AnomalyConfig>, provider=<StatsProvider>)
      - 레거시: AnomalyDetector(<StatsProvider>, config=<AnomalyConfig>)
      - 또는 AnomalyDetector(config=..., stats=...) 도 허용
    """
    def __init__(
        self,
        config: Optional[AnomalyConfig] = None,
        provider: Optional[StatsProvider] = None,
        *,
        stats: Optional[StatsProvider] = None
    ):
        if isinstance(config, StatsProvider) and provider is None and stats is None:
            provider = config
            config = None

        self.stats: Optional[StatsProvider] = provider or stats
        self.cfg = config or AnomalyConfig()
        if self.stats is None:
            raise ValueError("Stats provider is required")

        self._users: Dict[str, UserState] = {}

    # 내부: Z-score 계산
    def _z(self, x: float, mu: Optional[float], sigma: Optional[float]) -> Optional[float]:
        if mu is None or sigma is None or sigma <= 1e-6:
            return None
        try:
            return (float(x) - float(mu)) / float(sigma)
        except Exception:
            return None

    def _reset_counters_if_gap(self, S: UserState, ts_utc: datetime):
        if S.last_ts is None:
            return
        if (ts_utc - S.last_ts).total_seconds() > self.cfg.max_gap_sec:
            S.emg_hr_z_c = S.emg_stress_z_c = 0
            S.res_hr_high_c = S.res_hr_low_c = 0
            S.res_stress_c = 0

    # ──────────────────────────────────────────────────────────────────
    # 외부: 평가 (UTC 기준)
    # ──────────────────────────────────────────────────────────────────
    def evaluate(self, *, user_ref: str, ts_utc: datetime, metrics: Dict[str, float]) -> AnomalyResult:
        S = self._users.setdefault(user_ref, UserState())

        # 0) emergency 쿨다운
        if S.emergency_until and ts_utc <= S.emergency_until:
            remain = max(0, int((S.emergency_until - ts_utc).total_seconds()))
            cd_min = max(1, math.ceil(remain / 60)) if remain > 0 else 1
            return AnomalyResult(
                ok=True, anomaly=True, risk_level="critical", mode="cooldown",
                reasons=(f"emergency_cooldown_until={S.emergency_until.isoformat()}",),
                cooldown_min=cd_min, cooldown_source="emergency", cooldown_until=S.emergency_until
            )

        present = [m for m in self.cfg.supported_metrics if m in metrics]
        if not present:
            # metric이 없어도 restrict 쿨다운이면 cooldown
            if S.restrict_until and ts_utc <= S.restrict_until:
                remain = max(0, int((S.restrict_until - ts_utc).total_seconds()))
                cd_min = max(1, math.ceil(remain / 60)) if remain > 0 else 1
                return AnomalyResult(
                    True, True, "high", "cooldown",
                    (f"restrict_cooldown_until={S.restrict_until.isoformat()}",),
                    cooldown_min=cd_min, cooldown_source="restrict", cooldown_until=S.restrict_until
                )
            return AnomalyResult(True, False, "low", "normal", ("no_supported_metrics",))

        # 1) 버킷/날짜
        kst = to_kst(ts_utc)
        bucket = bucket_index_4h(kst)
        as_of = kst.date()

        # 2) 연속성/시간 역행
        self._reset_counters_if_gap(S, ts_utc)
        forward = (S.last_ts is None) or (ts_utc > S.last_ts)  # 과거 ts는 카운터 미반영
        if forward:
            S.last_ts = ts_utc

        # 3) HR
        hr = metrics.get("hr")
        hr_z = None
        if hr is not None:
            stats_hr = self.stats.get_bucket_stats(user_ref, as_of, "hr", bucket)
            mu_h, sd_h = (stats_hr or (None, None))
            hr_z = self._z(hr, mu_h, sd_h)
            hrf = None
            try:
                hrf = float(hr)
            except Exception:
                pass

            emg_hr_z = (hr_z is not None and abs(hr_z) >= self.cfg.z_emergency)
            res_hr_z_hi = (hr_z is not None and hr_z >= self.cfg.z_restrict)
            res_hr_z_lo = (hr_z is not None and hr_z <= -self.cfg.z_restrict)
            res_hr_inst_hi = (hrf is not None and hrf >= self.cfg.hr_inst_restrict_high)
            res_hr_inst_lo = (hrf is not None and hrf <= self.cfg.hr_inst_restrict_low)

            if forward:
                S.emg_hr_z_c    = (S.emg_hr_z_c    + 1) if emg_hr_z else 0
                S.res_hr_high_c = (S.res_hr_high_c + 1) if (res_hr_z_hi or res_hr_inst_hi) else 0
                S.res_hr_low_c  = (S.res_hr_low_c  + 1) if (res_hr_z_lo or res_hr_inst_lo) else 0

        # 4) STRESS: |Z| 기반 (restrict & emergency)
        stress = metrics.get("stress")
        stress_z = None
        if stress is not None:
            s = None
            try:
                s = float(stress)
            except Exception:
                s = None

            stats_s = self.stats.get_bucket_stats(user_ref, as_of, "stress", bucket)
            mu_s, sd_s = (stats_s or (None, None))

            # 🔧 스케일 자동 정합(양방향)
            # μ/σ가 0~1대인데 s가 1.5 이상이면 → 0~100 입력으로 판단 → /100
            # μ/σ가 5 이상(대략 10~100 스케일)인데 s가 1.5 이하이면 → 0~1 입력으로 판단 → ×100
            if (mu_s is not None and sd_s is not None and s is not None):
                if (mu_s <= 1.5 and sd_s <= 1.5 and s > 1.5):
                    s = s / 100.0
                elif (s <= 1.5 and (mu_s >= 5.0 or sd_s >= 5.0)):
                    s = s * 100.0

            if s is not None:
                stress_z = self._z(s, mu_s, sd_s)
                emg_stress_z = (stress_z is not None and abs(stress_z) >= self.cfg.z_emergency)
                res_stress_z = (stress_z is not None and abs(stress_z) >= self.cfg.z_restrict)
                if forward:
                    S.emg_stress_z_c = (S.emg_stress_z_c + 1) if emg_stress_z else 0
                    S.res_stress_c   = (S.res_stress_c   + 1) if res_stress_z   else 0

        if ANOMALY_DEBUG:
            log.info(
                "[ANOM] user=%s ts=%s bkt=%d hr=%s hr_z=%s st=%s st_z=%s "
                "cnt(emg_hr=%d,emg_st=%d,res_hi=%d,res_lo=%d,res_st=%d) "
                "cd(res=%s,emg=%s)",
                user_ref, kst.isoformat(), bucket, hr, hr_z, stress, stress_z,
                S.emg_hr_z_c, S.emg_stress_z_c, S.res_hr_high_c, S.res_hr_low_c, S.res_stress_c,
                S.restrict_until.isoformat() if S.restrict_until else None,
                S.emergency_until.isoformat() if S.emergency_until else None,
            )

        # 5) EMERGENCY: |Z|>=5 3틱 (HR 또는 STRESS)
        if S.emg_hr_z_c >= self.cfg.consecutive_required:
            S.emg_hr_z_c = S.emg_stress_z_c = 0
            S.res_hr_high_c = S.res_hr_low_c = 0
            S.res_stress_c = 0
            S.restrict_until = None
            S.emergency_until = ts_utc + timedelta(seconds=self.cfg.emergency_cooldown_sec)
            trig = "hr_high" if (hr_z is None or hr_z >= 0) else "hr_low"
            res = AnomalyResult(
                True, True, "critical", "emergency",
                (f"|HR_Z|>={self.cfg.z_emergency:g} x{self.cfg.consecutive_required}",),
                trigger=trig, z=hr_z
            )
            res.cooldown_min = max(1, math.ceil(self.cfg.emergency_cooldown_sec / 60))
            res.cooldown_source = "emergency"
            res.cooldown_until = S.emergency_until
            return res

        if S.emg_stress_z_c >= self.cfg.consecutive_required:
            S.emg_hr_z_c = S.emg_stress_z_c = 0
            S.res_hr_high_c = S.res_hr_low_c = 0
            S.res_stress_c = 0
            S.restrict_until = None
            S.emergency_until = ts_utc + timedelta(seconds=self.cfg.emergency_cooldown_sec)
            res = AnomalyResult(
                True, True, "critical", "emergency",
                (f"|STRESS_Z|>={self.cfg.z_emergency:g} x{self.cfg.consecutive_required}",),
                trigger="stress_up", z=stress_z
            )
            res.cooldown_min = max(1, math.ceil(self.cfg.emergency_cooldown_sec / 60))
            res.cooldown_source = "emergency"
            res.cooldown_until = S.emergency_until
            return res

        # 6) RESTRICT 쿨다운
        if S.restrict_until and ts_utc <= S.restrict_until:
            remain = max(0, int((S.restrict_until - ts_utc).total_seconds()))
            cd_min = max(1, math.ceil(remain / 60)) if remain > 0 else 1
            return AnomalyResult(
                True, True, "high", "cooldown",
                (f"restrict_cooldown_until={S.restrict_until.isoformat()}",),
                cooldown_min=cd_min, cooldown_source="restrict", cooldown_until=S.restrict_until
            )

        # 7) RESTRICT: HR Z/절대값 or STRESS Z — 3틱
        if S.res_hr_high_c >= self.cfg.consecutive_required:
            S.res_hr_high_c = S.res_hr_low_c = 0
            S.res_stress_c = 0
            S.restrict_until = ts_utc + timedelta(seconds=self.cfg.restrict_cooldown_sec)
            reason = (
                "HR_Z>={:.1f} x{}".format(self.cfg.z_restrict, self.cfg.consecutive_required)
                if (hr_z is not None and hr_z >= self.cfg.z_restrict)
                else "HR_inst>={} x{}".format(self.cfg.hr_inst_restrict_high, self.cfg.consecutive_required)
            )
            res = AnomalyResult(True, True, "high", "restrict", (reason,), trigger="hr_high", z=hr_z)
            res.cooldown_min = max(1, math.ceil(self.cfg.restrict_cooldown_sec / 60))
            res.cooldown_source = "restrict"
            res.cooldown_until = S.restrict_until
            return res

        if S.res_hr_low_c >= self.cfg.consecutive_required:
            S.res_hr_high_c = S.res_hr_low_c = 0
            S.res_stress_c = 0
            S.restrict_until = ts_utc + timedelta(seconds=self.cfg.restrict_cooldown_sec)
            reason = (
                "HR_Z<={:.1f} x{}".format(-self.cfg.z_restrict, self.cfg.consecutive_required)
                if (hr_z is not None and hr_z <= -self.cfg.z_restrict)
                else "HR_inst<={} x{}".format(self.cfg.hr_inst_restrict_low, self.cfg.consecutive_required)
            )
            res = AnomalyResult(True, True, "high", "restrict", (reason,), trigger="hr_low", z=hr_z)
            res.cooldown_min = max(1, math.ceil(self.cfg.restrict_cooldown_sec / 60))
            res.cooldown_source = "restrict"
            res.cooldown_until = S.restrict_until
            return res

        if S.res_stress_c >= self.cfg.consecutive_required:
            S.res_hr_high_c = S.res_hr_low_c = 0
            S.res_stress_c = 0
            S.restrict_until = ts_utc + timedelta(seconds=self.cfg.restrict_cooldown_sec)
            reason = "|STRESS_Z|>={:.1f} x{}".format(self.cfg.z_restrict, self.cfg.consecutive_required)
            res = AnomalyResult(True, True, "high", "restrict", (reason,), trigger="stress_up", z=stress_z)
            res.cooldown_min = max(1, math.ceil(self.cfg.restrict_cooldown_sec / 60))
            res.cooldown_source = "restrict"
            res.cooldown_until = S.restrict_until
            return res

        # 8) 이상 없음
        return AnomalyResult(True, False, "low", "normal", ())

    # ──────────────────────────────────────────────────────────────────────────
    # views.py 호환용: handle_telemetry
    # ──────────────────────────────────────────────────────────────────────────
    def handle_telemetry(self, *, user_ref: str, ts: datetime, metrics: Dict[str, float]) -> Dict[str, Optional[str]]:
        # ts → UTC
        ts_utc = ts
        if ts_utc.tzinfo is None:
            ts_utc = ts_utc.replace(tzinfo=timezone.utc)
        else:
            ts_utc = ts_utc.astimezone(timezone.utc)

        res = self.evaluate(user_ref=user_ref, ts_utc=ts_utc, metrics=metrics)

        out: Dict[str, Optional[str]] = {
            "level": res.mode,          # "normal" | "restrict" | "emergency" | "cooldown"
            "trigger": res.trigger,     # None 가능
        }

        # 세션 ID는 restrict/emergency에서만 발급
        if res.mode in ("restrict", "emergency"):
            out["session_id"] = str(uuid.uuid4())

        # reasons 전달(views.py에서 사용 가능)
        if res.reasons:
            out["reasons"] = list(res.reasons)

        # cooldown payload
        if res.cooldown_min is not None:
            out["cooldown_min"] = res.cooldown_min
        if res.cooldown_source:
            out["cooldown_source"] = res.cooldown_source
        if res.cooldown_until is not None:
            out["cooldown_until"] = res.cooldown_until.isoformat()

        return out
