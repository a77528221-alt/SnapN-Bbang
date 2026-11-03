const KEYS = {
  PROFILE: 'snap.profile',
  RECENT_NAMES: 'snap.recentNames',
  MY_SETTLEMENTS: 'snap.mySettlements',
  participantKey: (sid) => `snap.participant.${sid}`,
};

function readJson(key, fallback) {
  try {
    const raw = window.localStorage.getItem(key);
    if (!raw) return fallback;
    return JSON.parse(raw);
  } catch {
    return fallback;
  }
}

function writeJson(key, value) {
  try {
    window.localStorage.setItem(key, JSON.stringify(value));
    return true;
  } catch {
    return false;
  }
}

function removeKey(key) {
  try {
    window.localStorage.removeItem(key);
    return true;
  } catch {
    return false;
  }
}

export const storage = {
  KEYS,

  getProfile(fallback = null) {
    return readJson(KEYS.PROFILE, fallback);
  },

  setProfile(profile) {
    return writeJson(KEYS.PROFILE, profile);
  },

  clearProfile() {
    return removeKey(KEYS.PROFILE);
  },

  getRecentNames(fallback = []) {
    return readJson(KEYS.RECENT_NAMES, fallback);
  },

  setRecentNames(names) {
    return writeJson(KEYS.RECENT_NAMES, names);
  },

  getMySettlements(fallback = []) {
    return readJson(KEYS.MY_SETTLEMENTS, fallback);
  },

  setMySettlements(settlements) {
    return writeJson(KEYS.MY_SETTLEMENTS, settlements);
  },

  getParticipantClaim(sid, fallback = null) {
    if (!sid) return fallback;
    return readJson(KEYS.participantKey(sid), fallback);
  },

  setParticipantClaim(sid, claimData) {
    if (!sid) return false;
    return writeJson(KEYS.participantKey(sid), claimData);
  },

  clearParticipantClaim(sid) {
    if (!sid) return false;
    return removeKey(KEYS.participantKey(sid));
  },
};

