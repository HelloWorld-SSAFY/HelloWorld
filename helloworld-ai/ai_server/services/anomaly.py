### 이상탐지 (쿨다운 포함)

# services/anomaly.py
from __future__ import annotations
from dataclasses import dataclass, field
from datetime import datetime, timezone, timedelta, date
from typing import Dict, Tuple, Protocol, Optional

# KST(UTC+9) 타임존
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


# ---- 기준선 공급자 인터페이스 ----------------------------------------------

class StatsProvider(Protocol):
    """
    (user_ref, as_of(날짜), metric, bucket_idx) -> (mean, stddev)
    실제 구현은 user_tod_stats_daily 스냅샷을 조회.
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
        if not arr:
            return None
        if not (0 <= bucket_idx < len(arr)):
            return None
        return arr[bucket_idx]


# ---- 탐지 파라미터(정책) ----------------------------------------------------

@dataclass(frozen=True)
class AnomalyConfig:
    # 일반 이상: |Z| >= 이 임계면 연속 카운트 대상
    z_anomaly_threshold: float = 2.5
    # 연속 샘플 수(기본 3회)
    consecutive_z_required: int = 3

    # 즉시 응급: |Z| >= 이 임계면 즉시 emergency
    z_emergency_threshold: float = 5.0

    # HR 실측 응급(지속) 기준
    hr_emergency_high: int = 150
    hr_emergency_low: int = 45
    emergency_duration_sec: int = 120  # 120초 지속

    # 샘플 간격(초) — 지속 판정 누적에 사용
    sample_interval_sec: int = 10

    # 지원 메트릭
    supported_metrics: Tuple[str, ...] = ("hr", "stress")

    # ★ NEW: Restrict 쿨다운(재추천 금지) — 초 단위
    #   - 한 번 restrict가 발화되면, 해당 시간 동안은 새로운 "발화" 없이도 restrict 상태를 유지.
    #   - 운영에서 "몇 분 쉬게" 정책을 여기에 매핑.
    restrict_cooldown_sec: int = 180  # 기본 3분


# ---- 사용자/메트릭 상태 -----------------------------------------------------

@dataclass
class MetricState:
    # 연속 Z 카운트(이상)
    consecutive_z: int = 0
    # HR 응급 지속 누적(초)
    hr_high_streak_sec: int = 0
    hr_low_streak_sec: int = 0
    # 마지막 처리 시각(역행 방지)
    last_ts: Optional[datetime] = None

@dataclass
class UserState:
    # 메트릭명 → MetricState
    metrics: Dict[str, MetricState] = field(default_factory=dict)
    # ★ NEW: 사용자 단위 restrict 쿨다운 종료 시각(UTC)
    restrict_until: Optional[datetime] = None
    # (참고) 필요 시 응급 쿨다운도 추가 가능: emergency_until: Optional[datetime] = None


# ---- 결과 모델 --------------------------------------------------------------

@dataclass
class AnomalyResult:
    ok: bool
    anomaly: bool
    risk_level: str         # "low" | "high" | "critical"
    mode: str               # "normal" | "restrict" | "emergency"
    reasons: Tuple[str, ...]


# ---- 탐지기 -----------------------------------------------------------------

class AnomalyDetector:
    """
    실시간 이상/응급 탐지 + 'restrict 쿨다운' 유지 로직.

    흐름:
      1) as_of(날짜), 버킷 계산(KST 기준)
      2) Z-score 계산
      3) 응급 우선(즉시 응급/Z>=5 또는 HR 지속 120s) → emergency 반환
      4) 연속 Z로 이상 트리거 시:
           - user_state.restrict_until = now + restrict_cooldown_sec
           - 이후 해당 쿨다운 시간 동안은 새로운 발화가 없어도 restrict 유지
      5) 최종 모드:
           - emergency → (critical, emergency)
           - restrict(쿨다운 포함) → (high, restrict)
           - else → (low, normal)
    """
    def __init__(self, stats: StatsProvider, config: Optional[AnomalyConfig] = None):
        self.stats = stats
        self.cfg = config or AnomalyConfig()
        self._users: Dict[str, UserState] = {}

    def evaluate(self, *, user_ref: str, ts_utc: datetime, metrics: Dict[str, float]) -> AnomalyResult:
        # 1) 버킷/날짜
        kst = to_kst(ts_utc)
        bucket = bucket_index_4h(kst)
        as_of = kst.date()

        state = self._users.setdefault(user_ref, UserState())
        reasons = []

        # 지원 메트릭 확인
        present_metrics = [m for m in self.cfg.supported_metrics if m in metrics]
        if not present_metrics:
            return AnomalyResult(ok=False, anomaly=False, risk_level="low", mode="normal",
                                 reasons=("no_supported_metrics",))

        # 2) Z-score
        z_scores: Dict[str, Optional[float]] = {}
        for m in present_metrics:
            baseline = self.stats.get_bucket_stats(user_ref, as_of, m, bucket)
            if not baseline:
                z_scores[m] = None
                continue
            mean, std = baseline
            z_scores[m] = None if std is None or std <= 0 else (metrics[m] - mean) / std

        # 3) 응급(Z 기준 즉시)
        z_emergencies = [
            f"{m}_Z>={self.cfg.z_emergency_threshold:g}"
            for m, z in z_scores.items()
            if z is not None and abs(z) >= self.cfg.z_emergency_threshold
        ]
        if z_emergencies:
            reasons.extend(z_emergencies)
            return AnomalyResult(ok=True, anomaly=True, risk_level="critical", mode="emergency",
                                 reasons=tuple(reasons))

        # 3) 응급(HR 지속)
        hr_val = metrics.get("hr")
        if hr_val is not None:
            hr_reason = self._update_hr_emergency_state(user_ref, ts_utc, hr_val)
            if hr_reason:
                reasons.append(hr_reason)
                return AnomalyResult(ok=True, anomaly=True, risk_level="critical", mode="emergency",
                                     reasons=tuple(reasons))

        # 4) 연속 Z 기반 이상 플래그
        anomaly_flags = self._update_consecutive_z_state(user_ref, ts_utc, z_scores)
        if anomaly_flags:
            reasons.extend(anomaly_flags)
            # ★ 쿨다운 시작(또는 갱신): 이제부터 restrict 유지 기간
            state.restrict_until = ts_utc + timedelta(seconds=self.cfg.restrict_cooldown_sec)

        # 5) 쿨다운 활성 여부 확인
        if state.restrict_until and ts_utc <= state.restrict_until:
            # 새 발화가 없어도 쿨다운 동안은 restrict 유지
            if not anomaly_flags:
                reasons.append(f"restrict_cooldown_until={state.restrict_until.isoformat()}")
            return AnomalyResult(ok=True, anomaly=True, risk_level="high", mode="restrict",
                                 reasons=tuple(reasons))

        # 6) 이상 없음
        return AnomalyResult(ok=True, anomaly=False, risk_level="low", mode="normal",
                             reasons=tuple(reasons))

    # -- 내부 로직들 -----------------------------------------------------------

    def _metric_state(self, user_ref: str, metric: str) -> MetricState:
        user = self._users.setdefault(user_ref, UserState())
        return user.metrics.setdefault(metric, MetricState())

    def _update_hr_emergency_state(self, user_ref: str, ts_utc: datetime, hr: float) -> Optional[str]:
        """
        HR 상/하한 지속 응급 판정. 샘플 간격(cfg.sample_interval_sec)을 누적.
        시간 역행(ts_utc <= last_ts)은 streak 갱신 생략.
        """
        st = self._metric_state(user_ref, "hr")

        if st.last_ts and ts_utc <= st.last_ts:
            return None

        if hr >= self.cfg.hr_emergency_high:
            st.hr_high_streak_sec += self.cfg.sample_interval_sec
        else:
            st.hr_high_streak_sec = 0

        if hr <= self.cfg.hr_emergency_low:
            st.hr_low_streak_sec += self.cfg.sample_interval_sec
        else:
            st.hr_low_streak_sec = 0

        st.last_ts = ts_utc

        if st.hr_high_streak_sec >= self.cfg.emergency_duration_sec:
            return f"HR_inst>={self.cfg.hr_emergency_high} for {self.cfg.emergency_duration_sec}s"
        if st.hr_low_streak_sec >= self.cfg.emergency_duration_sec:
            return f"HR_inst<={self.cfg.hr_emergency_low} for {self.cfg.emergency_duration_sec}s"
        return None

    def _update_consecutive_z_state(
        self,
        user_ref: str,
        ts_utc: datetime,
        z_scores: Dict[str, Optional[float]],
    ) -> Tuple[str, ...]:
        """
        |Z| >= z_anomaly_threshold이면 연속 카운트 +1, 아니면 0으로 리셋.
        연속 카운트가 consecutive_z_required(기본 3)에 도달하면 플래그 추가.
        트리거 직후 카운터는 0으로 리셋(동일 패턴 재발 시 재발화 가능).
        쿨다운은 evaluate()에서 user_state.restrict_until로 관리.
        """
        flags = []
        for m, z in z_scores.items():
            st = self._metric_state(user_ref, m)
            if z is None:
                st.consecutive_z = 0
            else:
                st.consecutive_z = st.consecutive_z + 1 if abs(z) >= self.cfg.z_anomaly_threshold else 0
                if st.consecutive_z >= self.cfg.consecutive_z_required:
                    flags.append(
                        f"{m}_Z>={self.cfg.z_anomaly_threshold:g} for {self.cfg.consecutive_z_required} samples"
                    )
                    st.consecutive_z = 0  # 트리거 후 카운터 초기화
            st.last_ts = ts_utc
        return tuple(flags)
