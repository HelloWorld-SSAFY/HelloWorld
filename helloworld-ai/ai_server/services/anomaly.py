# services/anomaly.py
from __future__ import annotations
from dataclasses import dataclass, field
from datetime import datetime, timezone, timedelta, date
from typing import Dict, Tuple, Protocol, Optional

# ──────────────────────────────────────────────────────────────────────────────
# KST(UTC+9) / 버킷 유틸
# ──────────────────────────────────────────────────────────────────────────────

KST = timezone(timedelta(hours=9))

def to_kst(dt_utc: datetime) -> datetime:
    """
    UTC datetime(naive/aware)을 KST로 변환.
    naive면 UTC로 간주하여 tzinfo를 붙인 뒤 변환.
    """
    if dt_utc.tzinfo is None:
        dt_utc = dt_utc.replace(tzinfo=timezone.utc)
    return dt_utc.astimezone(KST)

def bucket_index_4h(kst_dt: datetime) -> int:
    """
    4시간 버킷 인덱스(0..5): [0-4), [4-8), [8-12), [12-16), [16-20), [20-24)
    user_tod_stats_daily의 v_0_4 .. v_20_24와 매핑.
    """
    return kst_dt.hour // 4  # 0..5


# ──────────────────────────────────────────────────────────────────────────────
# 기준선 공급자 인터페이스
# ──────────────────────────────────────────────────────────────────────────────

class StatsProvider(Protocol):
    """
    (user_ref, as_of(날짜), metric, bucket_idx) -> (mean, stddev) | None
    실제 구현은 ORM Provider(예: UserTodStatsDaily)에서 조회.
    """
    def get_bucket_stats(
        self, user_ref: str, as_of: date, metric: str, bucket_idx: int
    ) -> Optional[Tuple[float, float]]:
        ...

class DictStatsProvider:
    """
    테스트/스텁용 구현.
    store 키: (user_ref, as_of_date, metric)
    값: 길이 6의 튜플(버킷 0..5 각 (mean, std))
    """
    def __init__(self, store: Dict[Tuple[str, date, str], Tuple[Tuple[float, float], ...]]):
        self.store = store

    def get_bucket_stats(
        self, user_ref: str, as_of: date, metric: str, bucket_idx: int
    ) -> Optional[Tuple[float, float]]:
        arr = self.store.get((user_ref, as_of, metric))
        if not arr or not (0 <= bucket_idx < len(arr)):
            return None
        return arr[bucket_idx]


# ──────────────────────────────────────────────────────────────────────────────
# 탐지 파라미터(정책)
# ──────────────────────────────────────────────────────────────────────────────

@dataclass(frozen=True)
class AnomalyConfig:
    # Z 기반 기준
    z_anomaly_threshold: float = 2.5      # RESTRICT 후보 (|Z| ≥ 2.5)
    z_emergency_threshold: float = 5.0    # EMERGENCY 후보 (|Z| ≥ 5.0)

    # HR 기반 기준
    hr_restrict_high: int = 140           # RESTRICT 후보: HR ≥ 140
    hr_restrict_low: int = 50             # RESTRICT 후보: HR ≤ 50
    hr_emergency_high: int = 150          # EMERGENCY 후보: HR ≥ 150
    hr_emergency_low: int = 45            # EMERGENCY 후보: HR ≤ 45

    # 모든 후보는 "연속 N회"여야 트리거
    consecutive_required: int = 3         # 10초 주기 3회

    # 지원 메트릭
    supported_metrics: Tuple[str, ...] = ("hr", "stress")

    # 쿨다운 (초)
    restrict_cooldown_sec: int = 180      # RESTRICT 유지 시간
    emergency_cooldown_sec: int = 3600    # ★ EMERGENCY 유지 시간(기본 60분)


# ──────────────────────────────────────────────────────────────────────────────
# 상태/결과 모델
# ──────────────────────────────────────────────────────────────────────────────

@dataclass
class MetricState:
    consecutive_z: int = 0
    last_ts: Optional[datetime] = None

@dataclass
class UserState:
    metrics: Dict[str, MetricState] = field(default_factory=dict)
    # 쿨다운
    restrict_until: Optional[datetime] = None
    emergency_until: Optional[datetime] = None  # ★ 응급 쿨다운
    # 연속 카운트(사용자 단위)
    emg_streak: int = 0
    res_streak: int = 0
    last_ts: Optional[datetime] = None  # 역행 방지

@dataclass
class AnomalyResult:
    ok: bool
    anomaly: bool
    risk_level: str         # "low" | "high" | "critical"
    mode: str               # "normal" | "restrict" | "emergency"
    reasons: Tuple[str, ...]


# ──────────────────────────────────────────────────────────────────────────────
# 탐지기
# ──────────────────────────────────────────────────────────────────────────────

class AnomalyDetector:
    """
    실시간 이상/응급 탐지 + 'restrict/emergency 쿨다운' 유지.

    규칙(확정):
      - 모든 트리거는 10초 주기 '연속 3회'일 때만 발화.
      - EMERGENCY 후보: |Z| ≥ 5  또는 HR ≥ 150 / HR ≤ 45
      - RESTRICT  후보: |Z| ≥ 2.5 또는 HR ≥ 140 / HR ≤ 50 (단, EMG 후보가 아닐 때)
      - 쿨다운 우선순위: emergency > restrict
        · emergency 발화 시: emergency_cooldown 시작 + restrict_cooldown 무효화
        · emergency 쿨다운 중엔 무조건 'emergency' 유지
        · restrict 쿨다운은 emergency 쿨다운이 없을 때만 적용
    """
    def __init__(self, stats: StatsProvider, config: Optional[AnomalyConfig] = None):
        self.stats = stats
        self.cfg = config or AnomalyConfig()
        self._users: Dict[str, UserState] = {}

    def evaluate(self, *, user_ref: str, ts_utc: datetime, metrics: Dict[str, float]) -> AnomalyResult:
        # 사용자 상태(쿨다운 우선 확인 위해 먼저 로드)
        state = self._users.setdefault(user_ref, UserState())

        # ★ 0) emergency 쿨다운 우선 적용(메트릭 유무와 상관없이 응급 유지)
        if state.emergency_until and ts_utc <= state.emergency_until:
            return AnomalyResult(
                ok=True, anomaly=True, risk_level="critical", mode="emergency",
                reasons=(f"emergency_cooldown_until={state.emergency_until.isoformat()}",)
            )

        # 0.5) 메트릭 없을 때도 restrict 쿨다운은 유지되어야 함
        present_metrics = [m for m in self.cfg.supported_metrics if m in metrics]
        if not present_metrics:
            if state.restrict_until and ts_utc <= state.restrict_until:
                return AnomalyResult(
                    ok=True, anomaly=True, risk_level="high", mode="restrict",
                    reasons=(f"restrict_cooldown_until={state.restrict_until.isoformat()}",)
                )
            return AnomalyResult(ok=True, anomaly=False, risk_level="low", mode="normal", reasons=("no_supported_metrics",))

        # 1) 버킷/날짜
        kst = to_kst(ts_utc)
        bucket = bucket_index_4h(kst)
        as_of = kst.date()

        # 2) Z-score 계산(있으면)
        z_scores: Dict[str, Optional[float]] = {}
        for m in present_metrics:
            baseline = self.stats.get_bucket_stats(user_ref, as_of, m, bucket)
            if not baseline:
                z_scores[m] = None
                continue
            mean, std = baseline
            if std is None or std <= 0:
                z_scores[m] = None
            else:
                z_scores[m] = (metrics[m] - mean) / std

        # 3) 후보 플래그
        z_emg = any(z is not None and abs(z) >= self.cfg.z_emergency_threshold for z in z_scores.values())
        z_res = any(z is not None and abs(z) >= self.cfg.z_anomaly_threshold  for z in z_scores.values())

        hr_val = metrics.get("hr")
        hr_emg = (hr_val is not None) and (hr_val >= self.cfg.hr_emergency_high or hr_val <= self.cfg.hr_emergency_low)
        hr_res = (hr_val is not None) and (hr_val >= self.cfg.hr_restrict_high  or hr_val <= self.cfg.hr_restrict_low)

        emg_candidate = z_emg or hr_emg
        res_candidate = (z_res or hr_res) and not emg_candidate  # 상위단계 우선

        # 4) 연속 카운팅(역행 방지)
        if state.last_ts and ts_utc <= state.last_ts:
            # 시간 역행이면 카운팅 스킵
            pass
        else:
            if emg_candidate:
                state.emg_streak += 1
                state.res_streak = 0
            elif res_candidate:
                state.res_streak += 1
                state.emg_streak = 0
            else:
                state.emg_streak = 0
                state.res_streak = 0
            state.last_ts = ts_utc

        # 5) 트리거 판단
        if state.emg_streak >= self.cfg.consecutive_required:
            reasons = []
            if z_emg:
                reasons.append(f"Z>={self.cfg.z_emergency_threshold:g} x{self.cfg.consecutive_required}")
            if hr_emg:
                reasons.append(f"HR>={self.cfg.hr_emergency_high} or <={self.cfg.hr_emergency_low} x{self.cfg.consecutive_required}")
            # 리셋 + emergency 쿨다운 시작 + restrict 쿨다운 무효화
            state.emg_streak = 0
            state.res_streak = 0
            state.emergency_until = ts_utc + timedelta(seconds=self.cfg.emergency_cooldown_sec)
            state.restrict_until = None
            return AnomalyResult(ok=True, anomaly=True, risk_level="critical", mode="emergency",
                                 reasons=tuple(reasons) if reasons else ("emergency_candidate_x3",))

        if state.res_streak >= self.cfg.consecutive_required:
            reasons = []
            if z_res:
                reasons.append(f"Z>={self.cfg.z_anomaly_threshold:g} x{self.cfg.consecutive_required}")
            if hr_res:
                reasons.append(f"HR>={self.cfg.hr_restrict_high} or <={self.cfg.hr_restrict_low} x{self.cfg.consecutive_required}")
            # 리셋 + restrict 쿨다운 시작
            state.res_streak = 0
            state.emg_streak = 0
            state.restrict_until = ts_utc + timedelta(seconds=self.cfg.restrict_cooldown_sec)
            return AnomalyResult(ok=True, anomaly=True, risk_level="high", mode="restrict",
                                 reasons=tuple(reasons) if reasons else ("restrict_candidate_x3",))

        # 6) 쿨다운 유지 (emergency 없음이 확정된 경우에만)
        if state.restrict_until and ts_utc <= state.restrict_until:
            return AnomalyResult(
                ok=True, anomaly=True, risk_level="high", mode="restrict",
                reasons=(f"restrict_cooldown_until={state.restrict_until.isoformat()}",)
            )

        # 7) 이상 없음
        return AnomalyResult(ok=True, anomaly=False, risk_level="low", mode="normal", reasons=())
