import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import AppShell from '../components/AppShell';
import ParticipantChips from '../components/ParticipantChips';
import MenuItemCard from '../components/MenuItemCard';
import { calculateSettlementPreview } from '../lib/calc';
import { storage } from '../lib/storage';
import {
  MOCK_INITIAL_ROUNDS,
  MOCK_PARTICIPANTS,
  MOCK_SETTLEMENT_DETAIL,
} from '../mock/mockData';
import styles from './SettlePage.module.css';

export default function SettlePage() {
  const navigate = useNavigate();

  const [participants, setParticipants] = useState(MOCK_PARTICIPANTS);
  const [rounds, setRounds] = useState(MOCK_INITIAL_ROUNDS);
  const [activeRoundId, setActiveRoundId] = useState(MOCK_INITIAL_ROUNDS[0]?.id || 'r1');

  const activeRound = rounds.find((r) => r.id === activeRoundId) || rounds[0];

  const handleAddParticipant = (name) => {
    if (participants.some((p) => p.name === name)) return;
    const next = [...participants, { id: `p-${Date.now()}`, name, isHost: false }];
    setParticipants(next);
    storage.setRecentNames(next.map((p) => p.name));
  };

  const handleRemoveParticipant = (participantId) => {
    setParticipants((prev) => prev.filter((p) => p.id !== participantId));
  };

  const handleToggleRoundAbsence = (roundId, participantId) => {
    setRounds((prev) =>
      prev.map((round) => {
        if (round.id !== roundId) return round;
        const set = new Set(round.absentParticipantIds || []);
        if (set.has(participantId)) {
          set.delete(participantId);
        } else {
          set.add(participantId);
        }
        return { ...round, absentParticipantIds: Array.from(set) };
      })
    );
  };

  const handleToggleItemExclusion = (itemId, participantId) => {
    setRounds((prev) =>
      prev.map((round) => {
        if (round.id !== activeRound.id) return round;
        return {
          ...round,
          items: round.items.map((item) => {
            if (item.id !== itemId) return item;
            const set = new Set(item.excludedParticipantIds || []);
            if (set.has(participantId)) {
              set.delete(participantId);
            } else {
              set.add(participantId);
            }
            return { ...item, excludedParticipantIds: Array.from(set) };
          }),
        };
      })
    );
  };

  const preview = calculateSettlementPreview({ participants, rounds });
  const hasZeroEaterError = preview.zeroEaterItemIds.length > 0;

  const handleCreateSettlementLink = () => {
    // Mock 정산 ID로 총무 관리 화면 이동
    navigate(`/s/${MOCK_SETTLEMENT_DETAIL.id}/manage`);
  };

  return (
    <AppShell
      title="정산하기"
      subtitle="먹지 않은 사람만 탭해서 빼주세요"
      backTo="/"
      bottomBar={
        <button
          type="button"
          className={styles.primaryCta}
          disabled={hasZeroEaterError}
          onClick={handleCreateSettlementLink}
        >
          정산 링크 만들기 (총 {preview.grandTotal.toLocaleString()}원)
        </button>
      }
    >
      {/* 1. 참석자 칩 (총무 포함) */}
      <section className={styles.section}>
        <div className={styles.sectionHeader}>
          <h2>1. 전체 참석자 ({participants.length}명)</h2>
          <span>총무 포함</span>
        </div>
        <ParticipantChips
          participants={participants}
          onAdd={handleAddParticipant}
          onRemove={handleRemoveParticipant}
        />
      </section>

      {/* 2. 차수 탭 & 차수 불참 설정 */}
      <section className={styles.section}>
        <div className={styles.sectionHeader}>
          <h2>2. 차수별 불참·메뉴 제외</h2>
          <span>전원 참여가 기본값</span>
        </div>

        <div className={styles.roundTabs}>
          {rounds.map((round) => (
            <button
              key={round.id}
              type="button"
              className={`${styles.roundTab} ${
                round.id === activeRound?.id ? styles.roundTabActive : ''
              }`}
              onClick={() => setActiveRoundId(round.id)}
            >
              {round.roundNumber}차 · {round.placeName}
            </button>
          ))}
        </div>

        {activeRound && (
          <div className={styles.absenceBox}>
            <p className={styles.boxTitle}>
              {activeRound.roundNumber}차에 아예 안 온 사람 (차수 불참)
            </p>
            <div className={styles.absenceChipRow}>
              {participants.map((p) => {
                const isAbsent = (activeRound.absentParticipantIds || []).includes(p.id);
                return (
                  <button
                    key={p.id}
                    type="button"
                    className={`${styles.absenceChip} ${
                      isAbsent ? styles.absenceActive : ''
                    }`}
                    onClick={() => handleToggleRoundAbsence(activeRound.id, p.id)}
                  >
                    <span>{p.name}</span>
                    <span>{isAbsent ? '불참' : '참석'}</span>
                  </button>
                );
              })}
            </div>
          </div>
        )}

        {/* 메뉴 카드 목록 */}
        {activeRound && (
          <div className={styles.menuList}>
            {activeRound.items.map((item) => (
              <MenuItemCard
                key={item.id}
                item={item}
                participants={participants}
                absentParticipantIds={activeRound.absentParticipantIds}
                onToggleExclude={handleToggleItemExclusion}
              />
            ))}
          </div>
        )}
      </section>

      {/* 3. 인당 금액 실시간 요약 (Mock 계산) */}
      <section className={styles.summarySection}>
        <div className={styles.sectionHeader}>
          <h2>3. 인당 금액 확인 (Mock 계산)</h2>
          <span>1원 단위 내림 · 자투리는 총무 부담</span>
        </div>

        <ul className={styles.summaryList}>
          {participants.map((p) => {
            const amount = preview.amountsByParticipantId[p.id] ?? 0;
            return (
              <li key={p.id} className={styles.summaryRow}>
                <div className={styles.personInfo}>
                  <strong>{p.name}</strong>
                  {p.isHost && <span className={styles.hostTag}>총무 (송금 제외)</span>}
                </div>
                <strong className={styles.personAmount}>
                  {amount.toLocaleString()}원
                </strong>
              </li>
            );
          })}
        </ul>

        <div className={styles.totalCheckRow}>
          <span>합계 검증</span>
          <strong>총 {preview.grandTotal.toLocaleString()}원 일치</strong>
        </div>
      </section>
    </AppShell>
  );
}

