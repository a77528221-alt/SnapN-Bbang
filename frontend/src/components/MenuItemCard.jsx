import styles from './MenuItemCard.module.css';

export default function MenuItemCard({
  item,
  participants,
  absentParticipantIds = [],
  onToggleExclude,
}) {
  const absentSet = new Set(absentParticipantIds);
  const excludedSet = new Set(item.excludedParticipantIds || []);

  const roundParticipants = participants.filter((p) => !absentSet.has(p.id));
  const eatersCount = roundParticipants.filter((p) => !excludedSet.has(p.id)).length;

  return (
    <div className={styles.card}>
      <div className={styles.header}>
        <div>
          <h4 className={styles.itemName}>{item.name}</h4>
          <p className={styles.subInfo}>
            수량 {item.qty}개 · 먹은 사람 {eatersCount}명
          </p>
        </div>
        <strong className={styles.price}>
          {Number(item.price).toLocaleString()}원
        </strong>
      </div>

      <p className={styles.helperText}>
        전원 참여가 기본이에요. <strong>먹지 않은 사람만 탭해서 제외</strong>하세요.
      </p>

      <div className={styles.chipRow}>
        {roundParticipants.map((p) => {
          const isExcluded = excludedSet.has(p.id);
          return (
            <button
              key={p.id}
              type="button"
              className={`${styles.memberChip} ${
                isExcluded ? styles.excludedChip : styles.includedChip
              }`}
              onClick={() => onToggleExclude(item.id, p.id)}
            >
              <span>{p.name}</span>
              <span className={styles.chipState}>
                {isExcluded ? '제외됨' : '먹음'}
              </span>
            </button>
          );
        })}
      </div>

      {eatersCount === 0 && (
        <p className={styles.warningText}>
          ⚠️ 먹은 사람이 한 명도 없는 메뉴는 저장할 수 없어요. 최소 1명은 포함해주세요.
        </p>
      )}
    </div>
  );
}

