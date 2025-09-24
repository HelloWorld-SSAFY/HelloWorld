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

    # HR 절대값 임계(Restrict 용, 지속 30초)
    hr_inst_restrict_high: int = 150
    hr_inst_restrict_low:  int = 45

    # 지속 시간(초)
    restrict_window_sec: int = 30  # ← 요구사항: 30초 지속이면 restrict

    # 연속성 판단을 위한 최대 간격(초) — 이보다 큰 갭이 생기면 지속 타이머 리셋
    max_gap_sec: int = 30

    supported_metrics: Tuple[str, ...] = ("hr", "stress")

    # 쿨다운(초)
    restrict_cooldown_sec: int = 180
    emergency_cooldown_sec: int = 3600

# ──────────────────────────────────────────────────────────────────────────────
# 상태 / 결과
# ──────────────────────────────────────────────────────────────────────────────
@dataclass
class UserState:
    # 지속 판정용 시작시각(UTC)
    hr_hi_start: Optional[datetime] = None      # HR ≥ 150 시작 시각
    hr_lo_start: Optional[datetime] = None      # HR ≤ 45 시작 시각
    z_hr_start: Optional[datetime] = None       # |HR_Z| ≥ 2.5 시작 시각
    z_stress_start: Optional[datetime] = None   # |STRESS_Z| ≥ 2.5 시작 시각

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
    trigger: Optional[str] = None  # "hr_high" | "hr_low" | "hr_z" | "stress_z"
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

    def _reset_if_gap(self, S: UserState, ts_utc: datetime):
        if S.last_ts is None:
            return
        if (ts_utc - S.last_ts).total_seconds() > self.cfg.max_gap_sec:
            S.hr_hi_start = None
            S.hr_lo_start = None
            S.z_hr_start = None
            S.z_stress_start = None

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
        self._reset_if_gap(S, ts_utc)
        forward = (S.last_ts is None) or (ts_utc > S.last_ts)  # 과거 ts는 지속 타이머/쿨다운 미반영
        if forward:
            S.last_ts = ts_utc

        # 3) HR 값/기준선
        hr = metrics.get("hr")
        hrf: Optional[float] = None
        hr_z: Optional[float] = None
        if hr is not None:
            try:
                hrf = float(hr)
            except Exception:
                hrf = None

            stats_hr = self.stats.get_bucket_stats(user_ref, as_of, "hr", bucket)
            mu_h, sd_h = (stats_hr or (None, None))
            if hrf is not None:
                hr_z = self._z(hrf, mu_h, sd_h)

        # 4) STRESS 값/기준선
        stress = metrics.get("stress")
        s_val: Optional[float] = None
        stress_z: Optional[float] = None
        if stress is not None:
            try:
                s_val = float(stress)
                if s_val > 1.0:  # 0~100 → 0~1
                    s_val = s_val / 100.0
            except Exception:
                s_val = None

            stats_s = self.stats.get_bucket_stats(user_ref, as_of, "stress", bucket)
            mu_s, sd_s = (stats_s or (None, None))
            if s_val is not None:
                stress_z = self._z(s_val, mu_s, sd_s)

        if ANOMALY_DEBUG:
            log.info(
                "[ANOM] user=%s ts=%s bkt=%d hr=%s hr_z=%s st=%s st_z=%s "
                "state(hi=%s,lo=%s,zhr=%s,zst=%s) "
                "cd(res=%s,emg=%s)",
                user_ref, kst.isoformat(), bucket, hrf, hr_z, s_val, stress_z,
                S.hr_hi_start.isoformat() if S.hr_hi_start else None,
                S.hr_lo_start.isoformat() if S.hr_lo_start else None,
                S.z_hr_start.isoformat() if S.z_hr_start else None,
                S.z_stress_start.isoformat() if S.z_stress_start else None,
                S.restrict_until.isoformat() if S.restrict_until else None,
                S.emergency_until.isoformat() if S.emergency_until else None,
            )

        # 5) EMERGENCY: |Z| ≥ z_emergency (즉시) — HR 또는 STRESS
        if (hr_z is not None and abs(hr_z) >= self.cfg.z_emergency) or \
           (stress_z is not None and abs(stress_z) >= self.cfg.z_emergency):
            S.hr_hi_start = S.hr_lo_start = None
            S.z_hr_start = S.z_stress_start = None
            S.restrict_until = None
            S.emergency_until = ts_utc + timedelta(seconds=self.cfg.emergency_cooldown_sec)
            trig = ("hr_high" if hr_z is not None and hr_z >= 0 else
                    "hr_low"  if hr_z is not None and hr_z <  0 else
                    "stress_z")
            res = AnomalyResult(
                True, True, "critical", "emergency",
                (f"|Z|>={self.cfg.z_emergency:g} immediate",),
                trigger=trig, z=hr_z if hr_z is not None else stress_z
            )
            res.cooldown_min = max(1, math.ceil(self.cfg.emergency_cooldown_sec / 60))
            res.cooldown_source = "emergency"
            res.cooldown_until = S.emergency_until
            return res

        # 6) RESTRICT 쿨다운 (emergency보다 우선 검사했으므로 여기서 검사)
        if S.restrict_until and ts_utc <= S.restrict_until:
            remain = max(0, int((S.restrict_until - ts_utc).total_seconds()))
            cd_min = max(1, math.ceil(remain / 60)) if remain > 0 else 1
            return AnomalyResult(
                True, True, "high", "cooldown",
                (f"restrict_cooldown_until={S.restrict_until.isoformat()}",),
                cooldown_min=cd_min, cooldown_source="restrict", cooldown_until=S.restrict_until
            )

        # 7) RESTRICT(지속 30초) — (a) HR 절대값, (b) |Z| ≥ 2.5
        win = self.cfg.restrict_window_sec

        # (a) HR 절대값 지속
        if forward and hrf is not None:
            if hrf >= self.cfg.hr_inst_restrict_high:
                if S.hr_hi_start is None:
                    S.hr_hi_start = ts_utc
                elif (ts_utc - S.hr_hi_start).total_seconds() >= win:
                    S.hr_hi_start = S.hr_lo_start = None
                    S.z_hr_start = S.z_stress_start = None
                    S.restrict_until = ts_utc + timedelta(seconds=self.cfg.restrict_cooldown_sec)
                    res = AnomalyResult(True, True, "high", "restrict",
                                        (f"HR≥{self.cfg.hr_inst_restrict_high} sustained {win}s (hr={hrf:.1f})",),
                                        trigger="hr_high", z=hr_z)
                    res.cooldown_min = max(1, math.ceil(self.cfg.restrict_cooldown_sec / 60))
                    res.cooldown_source = "restrict"
                    res.cooldown_until = S.restrict_until
                    return res
                # 반대 임계는 리셋
                S.hr_lo_start = None
            elif hrf <= self.cfg.hr_inst_restrict_low:
                if S.hr_lo_start is None:
                    S.hr_lo_start = ts_utc
                elif (ts_utc - S.hr_lo_start).total_seconds() >= win:
                    S.hr_hi_start = S.hr_lo_start = None
                    S.z_hr_start = S.z_stress_start = None
                    S.restrict_until = ts_utc + timedelta(seconds=self.cfg.restrict_cooldown_sec)
                    res = AnomalyResult(True, True, "high", "restrict",
                                        (f"HR≤{self.cfg.hr_inst_restrict_low} sustained {win}s (hr={hrf:.1f})",),
                                        trigger="hr_low", z=hr_z)
                    res.cooldown_min = max(1, math.ceil(self.cfg.restrict_cooldown_sec / 60))
                    res.cooldown_source = "restrict"
                    res.cooldown_until = S.restrict_until
                    return res
                S.hr_hi_start = None
            else:
                S.hr_hi_start = None
                S.hr_lo_start = None

        # (b) |Z| ≥ 2.5 지속 (HR / STRESS 각각 독립)
        if forward:
            # HR Z
            if hr_z is not None and abs(hr_z) >= self.cfg.z_restrict:
                if S.z_hr_start is None:
                    S.z_hr_start = ts_utc
                elif (ts_utc - S.z_hr_start).total_seconds() >= win:
                    S.hr_hi_start = S.hr_lo_start = None
                    S.z_hr_start = S.z_stress_start = None
                    S.restrict_until = ts_utc + timedelta(seconds=self.cfg.restrict_cooldown_sec)
                    trig = "hr_high" if hr_z >= 0 else "hr_low"
                    res = AnomalyResult(True, True, "high", "restrict",
                                        (f"|HR_Z|>={self.cfg.z_restrict:g} sustained {win}s (z={hr_z:.2f})",),
                                        trigger=trig, z=hr_z)
                    res.cooldown_min = max(1, math.ceil(self.cfg.restrict_cooldown_sec / 60))
                    res.cooldown_source = "restrict"
                    res.cooldown_until = S.restrict_until
                    return res
            else:
                S.z_hr_start = None

            # STRESS Z
            if stress_z is not None and abs(stress_z) >= self.cfg.z_restrict:
                if S.z_stress_start is None:
                    S.z_stress_start = ts_utc
                elif (ts_utc - S.z_stress_start).total_seconds() >= win:
                    S.hr_hi_start = S.hr_lo_start = None
                    S.z_hr_start = S.z_stress_start = None
                    S.restrict_until = ts_utc + timedelta(seconds=self.cfg.restrict_cooldown_sec)
                    res = AnomalyResult(True, True, "high", "restrict",
                                        (f"|STRESS_Z|>={self.cfg.z_restrict:g} sustained {win}s (z={stress_z:.2f})",),
                                        trigger="stress_z", z=stress_z)
                    res.cooldown_min = max(1, math.ceil(self.cfg.restrict_cooldown_sec / 60))
                    res.cooldown_source = "restrict"
                    res.cooldown_until = S.restrict_until
                    return res
            else:
                S.z_stress_start = None

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
