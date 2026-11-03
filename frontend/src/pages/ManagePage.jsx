import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import AppShell from '../components/AppShell';
import { MOCK_SETTLEMENT_DETAIL } from '../mock/mockData';
import styles from './ManagePage.module.css';

const PAY_STATUS_META = {
  UNPAID: { label: '미입금', tone: 'unpaid' },
  REPORTED: { label: '송금 신고', tone: 'reported' },
  CONFIRMED: { label: '입금 확인', tone: 'confirmed' },
};

export default function ManagePage() {
  const { sid } = useParams();
  const navigate = useNavigate();

  const [participants, setParticipants] = useState(MOCK_SETTLEMENT_DETAIL.participants);
  const [feedback, setFeedback] = useState('');

  const shareUrl = `${window.location.origin}/r/${sid || MOCK_SETTLEMENT_DETAIL.id}`;

  const handleShareLink = async () => {
    const shareText = `[스냅 N빵] "${MOCK_SETTLEMENT_DETAIL.title}" 정산 내역이 도착했어요! 아래 링크에서 본인 이름을 골라 금액을 확인해주세요.`;
    if (navigator.share) {
      try {
        await navigator.share({
          title: MOCK_SETTLEMENT_DETAIL.title,
          text: shareText,
          url: shareUrl,
        });
        setFeedback('공유 시트를 열었어요.');
        return;
      } catch {
        // 사용자가 공유 취소 시 복사로 폴백하지 않고 조용히 종료
        return;
      }
    }

    try {
      await navigator.clipboard.writeText(shareUrl);
      setFeedback('정산 링크를 클립보드에 복사했어요!');
    } catch {
      setFeedback(`링크: ${shareUrl}`);
    }
  };

  const handleCopyLinkOnly = async () => {
    try {
      await navigator.clipboard.writeText(shareUrl);
      setFeedback('정산 링크를 복사했어요!');
    } catch {
      setFeedback(`링크: ${shareUrl}`);
    }
  };

  const handleConfirmPayment = (participantId) => {
    setParticipants((prev) =>
      prev.map((p) =>
        p.id === participantId ? { ...p, payStatus: 'CONFIRMED' } : p
      )
    );
  };

  const handleDeleteSettlement = () => {
    const ok = window.confirm('이 정산을 삭제할까요? 삭제하면 복구할 수 없어요.');
    if (!ok) return;
    navigate('/');
  };

  const confirmedCount = participants.filter((p) => p.payStatus === 'CONFIRMED').length;
  const progressPercent =
    participants.length > 0 ? Math.round((confirmedCount / participants.length) * 100) : 0;

  return (
    <AppShell
      title="정산 링크 · 입금 현황"
      subtitle={`정산 ID: ${sid}`}
      backTo="/settle"
      bottomBar={
        <button
          type="button"
          className={styles.shareCta}
          onClick={handleShareLink}
        >
          🔗 정산 링크 공유하기
        </button>
      }
    >
      {/* 정산 요약 및 공유 박스 */}
      <section className={styles.card}>
        <div className={styles.topRow}>
          <div>
            <span className={styles.dateBadge}>{MOCK_SETTLEMENT_DETAIL.settledOn}</span>
            <h2 className={styles.settlementTitle}>{MOCK_SETTLEMENT_DETAIL.title}</h2>
          </div>
          <strong className={styles.totalAmount}>
            {MOCK_SETTLEMENT_DETAIL.totalAmount.toLocaleString()}원
          </strong>
        </div>

        <div className={styles.linkBox}>
          <span className={styles.linkText}>{shareUrl}</span>
          <button
            type="button"
            className={styles.copyBtn}
            onClick={handleCopyLinkOnly}
          >
            링크 복사
          </button>
        </div>

        <div className={styles.previewRow}>
          <Link to={`/r/${sid}`} className={styles.previewLink}>
            참여자 화면(`/r/{sid}`) 열기 →
          </Link>
        </div>

        {feedback && <p className={styles.feedbackText}>{feedback}</p>}
      </section>

      {/* 입금 현황 목록 */}
      <section className={styles.section}>
        <div className={styles.sectionHeader}>
          <h3>
            입금 현황 ({confirmedCount}/{participants.length}명 완료)
          </h3>
          <span>{progressPercent}%</span>
        </div>

        <div className={styles.progressBar}>
          <div
            className={styles.progressFill}
            style={{ width: `${progressPercent}%` }}
          />
        </div>

        <ul className={styles.participantList}>
          {participants.map((p) => {
            const statusMeta = PAY_STATUS_META[p.payStatus] || PAY_STATUS_META.UNPAID;
            return (
              <li key={p.id} className={styles.participantRow}>
                <div className={styles.personCol}>
                  <div className={styles.nameLine}>
                    <strong>{p.name}</strong>
                    {p.isHost && <span className={styles.hostTag}>총무</span>}
                    <span className={`${styles.statusBadge} ${styles[statusMeta.tone]}`}>
                      {statusMeta.label}
                    </span>
                  </div>
                  <span className={styles.amountText}>
                    {p.amount.toLocaleString()}원
                  </span>
                </div>

                {p.payStatus === 'REPORTED' && (
                  <button
                    type="button"
                    className={styles.confirmBtn}
                    onClick={() => handleConfirmPayment(p.id)}
                  >
                    입금 확인
                  </button>
                )}
              </li>
            );
          })}
        </ul>
      </section>

      {/* 정산 삭제 영역 */}
      <section className={styles.dangerZone}>
        <p className={styles.noticeText}>
          총무 토큰은 이 기기에만 저장돼요. 정산을 삭제하면 모든 차수와 메뉴 기록이 즉시 삭제됩니다.
        </p>
        <button
          type="button"
          className={styles.deleteBtn}
          onClick={handleDeleteSettlement}
        >
          정산 삭제
        </button>
      </section>
    </AppShell>
  );
}

