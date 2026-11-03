import styles from './ReceiptCard.module.css';

export const RECEIPT_STATUS_META = {
  ANALYZING: {
    label: '분석 중',
    tone: 'reported',
    desc: 'AI가 메뉴와 금액을 읽고 있어요...',
  },
  MATCHED: {
    label: '합계 일치',
    tone: 'confirmed',
    desc: '메뉴 합계와 영수증 총액이 일치해요.',
  },
  NEEDS_CHECK: {
    label: '확인 필요',
    tone: 'reported',
    desc: '메뉴 합계와 영수증 총액을 한 번 확인해주세요.',
  },
  FAILED: {
    label: '읽지 못했어요',
    tone: 'unpaid',
    desc: '글자가 흐려 읽지 못했어요. 직접 입력하거나 다시 찍어주세요.',
  },
};

const STATUS_KEYS = ['ANALYZING', 'MATCHED', 'NEEDS_CHECK', 'FAILED'];

export default function ReceiptCard({ round, onStatusChange, onDelete }) {
  const statusMeta = RECEIPT_STATUS_META[round.parseStatus] || RECEIPT_STATUS_META.MATCHED;
  const itemsSum = (round.items || []).reduce((acc, item) => acc + (Number(item.price) || 0), 0);

  return (
    <article className={styles.card}>
      <div className={styles.cardHeader}>
        <span className={styles.roundBadge}>{round.roundNumber}차</span>
        <span className={`${styles.statusBadge} ${styles[statusMeta.tone]}`}>
          {statusMeta.label}
        </span>
      </div>

      <h3 className={styles.placeName}>{round.placeName || `${round.roundNumber}차 영수증`}</h3>
      <p className={styles.statusDesc}>{statusMeta.desc}</p>

      {round.parseStatus !== 'ANALYZING' && round.parseStatus !== 'FAILED' && (
        <ul className={styles.itemList}>
          {(round.items || []).map((item) => (
            <li key={item.id} className={styles.itemRow}>
              <span className={styles.itemName}>{item.name}</span>
              <span className={styles.itemPrice}>{Number(item.price).toLocaleString()}원</span>
            </li>
          ))}
        </ul>
      )}

      <div className={styles.totalRow}>
        <span>영수증 합계</span>
        <strong>{itemsSum.toLocaleString()}원</strong>
      </div>

      <div className={styles.cardFooter}>
        <label className={styles.statusSwitchLabel}>
          <span>상태(Mock):</span>
          <select
            className={styles.statusSelect}
            value={round.parseStatus}
            onChange={(e) => onStatusChange(round.id, e.target.value)}
          >
            {STATUS_KEYS.map((key) => (
              <option key={key} value={key}>
                {RECEIPT_STATUS_META[key].label}
              </option>
            ))}
          </select>
        </label>
        <button
          type="button"
          className={styles.deleteBtn}
          onClick={() => onDelete(round.id)}
        >
          삭제
        </button>
      </div>
    </article>
  );
}

