export const BANK_OPTIONS = [
  '토스뱅크',
  '카카오뱅크',
  'KB국민은행',
  '신한은행',
  '우리은행',
  '하나은행',
  'NH농협은행',
  'IBK기업은행',
  '케이뱅크',
  'SC제일은행',
];

export const MOCK_DEFAULT_PROFILE = {
  name: '민수',
  bank: '카카오뱅크',
  accountNumber: '3333-01-2345678',
  accountHolder: '김민수',
  payLink: 'https://qr.kakaopay.com/mock-example',
};

export const MOCK_PARTICIPANTS = [
  { id: 'p1', name: '민수', isHost: true },
  { id: 'p2', name: '철수', isHost: false },
  { id: 'p3', name: '영희', isHost: false },
  { id: 'p4', name: '지민', isHost: false },
];

export const MOCK_INITIAL_ROUNDS = [
  {
    id: 'r1',
    roundNumber: 1,
    placeName: '일미집 삼겹살',
    parseStatus: 'MATCHED', // ANALYZING | MATCHED | NEEDS_CHECK | FAILED
    receiptTotal: 50000,
    adjustmentAmount: 0,
    absentParticipantIds: [],
    items: [
      { id: 'i1', name: '삼겹살 4인분', qty: 4, price: 40000, excludedParticipantIds: [] },
      { id: 'i2', name: '소주 2병', qty: 2, price: 10000, excludedParticipantIds: ['p3'] },
    ],
  },
  {
    id: 'r2',
    roundNumber: 2,
    placeName: '역전할머니맥주',
    parseStatus: 'NEEDS_CHECK',
    receiptTotal: 27000,
    adjustmentAmount: 0,
    absentParticipantIds: ['p4'],
    items: [
      { id: 'i3', name: '먹태', qty: 1, price: 15000, excludedParticipantIds: [] },
      { id: 'i4', name: '생맥주 3잔', qty: 3, price: 12000, excludedParticipantIds: [] },
    ],
  },
];

export const MOCK_RECENT_SETTLEMENTS = [
  {
    id: 'AbCdEfGhIjKlMnOpQrSt1',
    title: '11/2 금요일 회식 (1·2차)',
    settledOn: '2026-11-02',
    totalAmount: 77000,
    participantCount: 4,
    confirmedCount: 2,
    reportedCount: 1,
  },
  {
    id: 'ZxYwVuTsRqPoNmLkJiHg2',
    title: '풋살 동호회 뒷풀이',
    settledOn: '2026-10-28',
    totalAmount: 112000,
    participantCount: 5,
    confirmedCount: 5,
    reportedCount: 0,
  },
];

export const MOCK_SETTLEMENT_DETAIL = {
  id: 'AbCdEfGhIjKlMnOpQrSt1',
  title: '11/2 금요일 회식 (1·2차)',
  settledOn: '2026-11-02',
  totalAmount: 77000,
  bank: '카카오뱅크',
  accountNumber: '3333-01-2345678',
  accountHolder: '김민수',
  payLink: 'https://qr.kakaopay.com/mock-example',
  participants: [
    {
      id: 'p1',
      name: '민수',
      isHost: true,
      claimed: true,
      payStatus: 'CONFIRMED',
      amount: 22334,
      summaryReason: '1차·2차 참석 · 총무(자투리 +1원 포함)',
      breakdown: [
        { roundNumber: 1, placeName: '일미집 삼겹살', itemName: '삼겹살 4인분 (1/4)', share: 10000 },
        { roundNumber: 1, placeName: '일미집 삼겹살', itemName: '소주 2병 (1/3)', share: 3333 },
        { roundNumber: 2, placeName: '역전할머니맥주', itemName: '먹태 (1/3)', share: 5000 },
        { roundNumber: 2, placeName: '역전할머니맥주', itemName: '생맥주 3잔 (1/3)', share: 4000 },
      ],
    },
    {
      id: 'p2',
      name: '철수',
      isHost: false,
      claimed: true,
      payStatus: 'REPORTED',
      amount: 22333,
      summaryReason: '1차·2차 전원 메뉴 참여',
      breakdown: [
        { roundNumber: 1, placeName: '일미집 삼겹살', itemName: '삼겹살 4인분 (1/4)', share: 10000 },
        { roundNumber: 1, placeName: '일미집 삼겹살', itemName: '소주 2병 (1/3)', share: 3333 },
        { roundNumber: 2, placeName: '역전할머니맥주', itemName: '먹태 (1/3)', share: 5000 },
        { roundNumber: 2, placeName: '역전할머니맥주', itemName: '생맥주 3잔 (1/3)', share: 4000 },
      ],
    },
    {
      id: 'p3',
      name: '영희',
      isHost: false,
      claimed: false,
      payStatus: 'UNPAID',
      amount: 19000,
      summaryReason: '1차·2차 참석 · 1차 소주 제외',
      breakdown: [
        { roundNumber: 1, placeName: '일미집 삼겹살', itemName: '삼겹살 4인분 (1/4)', share: 10000 },
        { roundNumber: 2, placeName: '역전할머니맥주', itemName: '먹태 (1/3)', share: 5000 },
        { roundNumber: 2, placeName: '역전할머니맥주', itemName: '생맥주 3잔 (1/3)', share: 4000 },
      ],
    },
    {
      id: 'p4',
      name: '지민',
      isHost: false,
      claimed: false,
      payStatus: 'UNPAID',
      amount: 13333,
      summaryReason: '1차만 참석 (2차 불참)',
      breakdown: [
        { roundNumber: 1, placeName: '일미집 삼겹살', itemName: '삼겹살 4인분 (1/4)', share: 10000 },
        { roundNumber: 1, placeName: '일미집 삼겹살', itemName: '소주 2병 (1/3)', share: 3333 },
      ],
    },
  ],
};

