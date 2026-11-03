import { useState } from 'react';
import styles from './ParticipantChips.module.css';

export default function ParticipantChips({ participants, onAdd, onRemove }) {
  const [newName, setNewName] = useState('');

  const handleSubmit = (e) => {
    e.preventDefault();
    const trimmed = newName.trim();
    if (!trimmed) return;
    onAdd(trimmed);
    setNewName('');
  };

  return (
    <div className={styles.wrap}>
      <div className={styles.chipList}>
        {participants.map((p) => (
          <div
            key={p.id}
            className={`${styles.chip} ${p.isHost ? styles.hostChip : ''}`}
          >
            <span className={styles.name}>{p.name}</span>
            {p.isHost ? (
              <span className={styles.hostBadge}>총무</span>
            ) : (
              <button
                type="button"
                className={styles.removeBtn}
                onClick={() => onRemove(p.id)}
                aria-label={`${p.name} 삭제`}
              >
                ×
              </button>
            )}
          </div>
        ))}
      </div>

      <form className={styles.addForm} onSubmit={handleSubmit}>
        <input
          type="text"
          className={styles.input}
          value={newName}
          onChange={(e) => setNewName(e.target.value)}
          placeholder="참석자 이름 추가"
          maxLength={20}
        />
        <button type="submit" className={styles.addBtn}>
          + 추가
        </button>
      </form>
    </div>
  );
}

