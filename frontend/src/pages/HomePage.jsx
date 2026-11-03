import { useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import AppShell from '../components/AppShell';
import ReceiptCard from '../components/ReceiptCard';
import { storage } from '../lib/storage';
import {
  MOCK_DEFAULT_PROFILE,
  MOCK_INITIAL_ROUNDS,
  MOCK_RECENT_SETTLEMENTS,
} from '../mock/mockData';
import styles from './HomePage.module.css';

export default function HomePage() {
  const navigate = useNavigate();
  const cameraInputRef = useRef(null);
  const albumInputRef = useRef(null);

  const profile = storage.getProfile(MOCK_DEFAULT_PROFILE);
  const [rounds, setRounds] = useState(MOCK_INITIAL_ROUNDS);
  const recentSettlements = storage.getMySettlements(MOCK_RECENT_SETTLEMENTS);

  const handleFilesSelected = (event) => {
    const fileList = Array.from(event.target.files || []);
    if (fileList.length === 0) return;

    const baseRoundNumber = rounds.length;
    const createdRounds = fileList.map((file, idx) => {
      const nextNum = baseRoundNumber + idx + 1;
      const newId = `r-${Date.now()}-${idx}`;
      return {
        id: newId,
        roundNumber: nextNum,
        placeName: file.name.replace(/\.[^/.]+$/, '') || `${nextNum}차 영수증`,
        parseStatus: 'ANALYZING',
        receiptTotal: 36000,
        adjustmentAmount: 0,
        absentParticipantIds: [],
        items: [
          { id: `${newId}-1`, name: '모둠안주 (Mock)', qty: 1, price: 24000, excludedParticipantIds: [] },
          { id: `${newId}-2`, name: '음료/주류 (Mock)', qty: 3, price: 12000, excludedParticipantIds: [] },
        ],
      };
    });

    setRounds((prev) => [...prev, ...createdRounds]);
    event.target.value = '';

    // Mock으로 잠시 분석 중 상태를 보여준 뒤 분석 완료(합계 일치) 처리
    setTimeout(() => {
      const createdIds = new Set(createdRounds.map((r) => r.id));
      setRounds((prev) =>
        prev.map((r) =>
          createdIds.has(r.id) ? { ...r, parseStatus: 'MATCHED' } : r
        )
      );
    }, 600);
  };

  const handleStatusChange = (roundId, nextStatus) => {
    setRounds((prev) =>
      prev.map((r) => (r.id === roundId ? { ...r, parseStatus: nextStatus } : r))
    );
  };

  const handleDeleteRound = (roundId) => {
    setRounds((prev) =>
      prev
        .filter((r) => r.id !== roundId)
        .map((r, idx) => ({ ...r, roundNumber: idx + 1 }))
    );
  };

  const totalReceiptSum = rounds.reduce((sum, r) => {
    const itemsTotal = (r.items || []).reduce((acc, i) => acc + (Number(i.price) || 0), 0);
    return sum + itemsTotal;
  }, 0);

  return (
    <AppShell
      title="스냅 N빵"
      subtitle="영수증 찰칵, 먹은 것만 쪼개는 모임 정산"
      showProfileLink
      bottomBar={
        <button
          type="button"
          className={styles.primaryCta}
          onClick={() => navigate('/settle')}
          disabled={rounds.length === 0}
        >
          정산하기 ({rounds.length}개 차수 · {totalReceiptSum.toLocaleString()}원)
        </button>
      }
    >
      {/* 총무 프로필 요약 배너 */}
      <section className={styles.profileSummary}>
        <div>
          <span className={styles.summaryLabel}>총무 입금 계좌</span>
          <p className={styles.summaryValue}>
            <strong>{profile.name}</strong> · {profile.bank} {profile.accountNumber}
          </p>
        </div>
        <Link to="/profile" className={styles.editLink}>
          수정
        </Link>
      </section>

      {/* 영수증 촬영 및 앨범 선택 영역 (실시간 미리보기 없음) */}
      <section className={styles.captureSection}>
        <div className={styles.sectionHeader}>
          <h2>영수증 추가</h2>
          <span>선택한 순서대로 1차, 2차가 돼요</span>
        </div>

        {/* 숨겨진 file input 2개: 카메라 촬영(단일) / 앨범 선택(다중) */}
        <input
          ref={cameraInputRef}
          type="file"
          accept="image/*"
          capture="environment"
          className={styles.hiddenInput}
          onChange={handleFilesSelected}
        />
        <input
          ref={albumInputRef}
          type="file"
          accept="image/*"
          multiple
          className={styles.hiddenInput}
          onChange={handleFilesSelected}
        />

        <button
          type="button"
          className={styles.cameraButton}
          onClick={() => cameraInputRef.current?.click()}
        >
          <span className={styles.cameraIcon} aria-hidden="true">📷</span>
          <span>영수증 촬영하기</span>
        </button>

        <button
          type="button"
          className={styles.albumButton}
          onClick={() => albumInputRef.current?.click()}
        >
          🖼️ 앨범에서 여러 장 고르기
        </button>
      </section>

      {/* 가로 스크롤 차수별 영수증 카드 */}
      <section className={styles.roundsSection}>
        <div className={styles.sectionHeader}>
          <h2>차수별 영수증 ({rounds.length}장)</h2>
          <span>좌우로 넘겨 확인하세요</span>
        </div>

        {rounds.length === 0 ? (
          <div className={styles.emptyBox}>
            아직 추가된 영수증이 없어요. 위 버튼으로 촬영하거나 앨범에서 골라주세요.
          </div>
        ) : (
          <div className={styles.horizontalScroll}>
            {rounds.map((round) => (
              <ReceiptCard
                key={round.id}
                round={round}
                onStatusChange={handleStatusChange}
                onDelete={handleDeleteRound}
              />
            ))}
          </div>
        )}
      </section>

      {/* 최근 정산 목록 자리 */}
      <section className={styles.recentSection}>
        <div className={styles.sectionHeader}>
          <h2>최근 정산 목록</h2>
          <span>이 기기에만 저장돼요</span>
        </div>

        <ul className={styles.recentList}>
          {recentSettlements.map((item) => (
            <li key={item.id} className={styles.recentCard}>
              <div className={styles.recentTop}>
                <strong>{item.title}</strong>
                <span className={styles.recentDate}>{item.settledOn}</span>
              </div>
              <div className={styles.recentMeta}>
                <span>총 {Number(item.totalAmount).toLocaleString()}원</span>
                <span>
                  입금 완료 {item.confirmedCount}/{item.participantCount}명
                </span>
              </div>
              <div className={styles.recentLinks}>
                <Link to={`/s/${item.id}/manage`} className={styles.manageLink}>
                  총무 관리 화면 →
                </Link>
                <Link to={`/r/${item.id}`} className={styles.participantLink}>
                  참여자 화면 보기 →
                </Link>
              </div>
            </li>
          ))}
        </ul>
      </section>
    </AppShell>
  );
}

