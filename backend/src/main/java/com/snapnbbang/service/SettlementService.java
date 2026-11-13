package com.snapnbbang.service;

import com.snapnbbang.common.ConflictException;
import com.snapnbbang.common.IdGenerator;
import com.snapnbbang.common.NotFoundException;
import com.snapnbbang.common.TokenUtil;
import com.snapnbbang.domain.Item;
import com.snapnbbang.domain.ItemExclusion;
import com.snapnbbang.domain.RoundAbsence;
import com.snapnbbang.domain.SettleRound;
import com.snapnbbang.domain.Settlement;
import com.snapnbbang.domain.SettlementParticipant;
import com.snapnbbang.dto.ParticipantClaimRequest;
import com.snapnbbang.dto.ParticipantClaimResponse;
import com.snapnbbang.dto.ParticipantShareResponse;
import com.snapnbbang.dto.ParticipantShareResponse.AccountInfo;
import com.snapnbbang.dto.ParticipantShareResponse.ShareItem;
import com.snapnbbang.dto.PaymentStatusResponse;
import com.snapnbbang.dto.SettlementCreateRequest;
import com.snapnbbang.dto.SettlementCreateRequest.ItemRequest;
import com.snapnbbang.dto.SettlementCreateRequest.ParticipantRequest;
import com.snapnbbang.dto.SettlementCreateRequest.RoundRequest;
import com.snapnbbang.dto.SettlementCreateResponse;
import com.snapnbbang.dto.SettlementPublicResponse;
import com.snapnbbang.dto.SettlementPublicResponse.ParticipantSummary;
import com.snapnbbang.mapper.ItemExclusionMapper;
import com.snapnbbang.mapper.ItemMapper;
import com.snapnbbang.mapper.RoundAbsenceMapper;
import com.snapnbbang.mapper.SettleRoundMapper;
import com.snapnbbang.mapper.SettlementMapper;
import com.snapnbbang.mapper.SettlementParticipantMapper;
import com.snapnbbang.service.SettlementCalculator.CalculationResult;
import com.snapnbbang.service.SettlementCalculator.ItemInput;
import com.snapnbbang.service.SettlementCalculator.ParticipantInput;
import com.snapnbbang.service.SettlementCalculator.RoundInput;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettlementService {

    private static final int MAX_TITLE_LENGTH = 60;
    private static final int MAX_BANK_LENGTH = 30;
    private static final int MAX_ACCOUNT_NUMBER_LENGTH = 40;
    private static final int MAX_ACCOUNT_HOLDER_LENGTH = 30;
    private static final int MAX_PAY_LINK_LENGTH = 255;
    private static final String ALLOWED_PAY_LINK_HOST = "qr.kakaopay.com";

    private static final int MAX_PARTICIPANTS = 30;
    private static final int MAX_PARTICIPANT_NAME_LENGTH = 30;
    private static final int MAX_ROUNDS = 10;
    private static final int MAX_PLACE_NAME_LENGTH = 60;
    private static final int MAX_ITEMS_PER_ROUND = 50;
    private static final int MAX_ITEM_NAME_LENGTH = 60;
    private static final int MAX_ITEM_PRICE = 10_000_000;
    private static final int MAX_ITEM_QTY = 10_000;
    private static final int MAX_ROUND_AMOUNT = 100_000_000;

    private static final String BEARER_PREFIX = "Bearer ";

    private final SettlementMapper settlementMapper;
    private final SettlementParticipantMapper settlementParticipantMapper;
    private final SettleRoundMapper settleRoundMapper;
    private final RoundAbsenceMapper roundAbsenceMapper;
    private final ItemMapper itemMapper;
    private final ItemExclusionMapper itemExclusionMapper;
    private final SettlementCalculator settlementCalculator;

    @Transactional
    public SettlementCreateResponse createSettlement(SettlementCreateRequest request) {
        ValidatedCreateInput validated = validateCreateRequest(request);

        // 계산 가능 여부 사전 검증 (먹은 사람이 한 명도 없는 메뉴 등이 있으면 IllegalArgumentException -> 400)
        settlementCalculator.calculate(validated.calcParticipants(), validated.calcRounds());

        String settlementId = IdGenerator.generate();
        String rawAdminToken = TokenUtil.generateToken();
        String adminTokenHash = TokenUtil.sha256Hex(rawAdminToken);
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        Settlement settlement = Settlement.builder()
                .id(settlementId)
                .title(validated.title())
                .settledOn(request.getSettledOn())
                .status("SHARED")
                .adminTokenHash(adminTokenHash)
                .bank(validated.bank())
                .accountNumber(validated.accountNumber())
                .accountHolder(validated.accountHolder())
                .payLink(validated.payLink())
                .sharedAt(now)
                .accountPurgeAt(now.plusDays(90))
                .expiresAt(now.plusMonths(12))
                .build();

        settlementMapper.insert(settlement);

        Map<String, Long> participantIdByName = new LinkedHashMap<>();
        for (ParticipantInput participantInput : validated.calcParticipants()) {
            boolean isHost = participantInput.isHost();
            SettlementParticipant participant = SettlementParticipant.builder()
                    .settlementId(settlementId)
                    .name(participantInput.name())
                    .isHost(isHost)
                    .tokenHash(null)
                    .claimedAt(null)
                    .payStatus(isHost ? "CONFIRMED" : "UNPAID")
                    .reportedAt(null)
                    .confirmedAt(isHost ? now : null)
                    .build();
            settlementParticipantMapper.insert(participant);
            participantIdByName.put(participantInput.name(), participant.getId());
        }

        for (ValidatedRound round : validated.rounds()) {
            SettleRound settleRound = SettleRound.builder()
                    .settlementId(settlementId)
                    .roundNumber(round.roundNumber())
                    .placeName(round.placeName())
                    .receiptTotal(round.receiptTotal())
                    .adjustmentAmount(round.adjustmentAmount())
                    .build();
            settleRoundMapper.insert(settleRound);
            Long roundId = settleRound.getId();

            for (String absentName : round.absentNames()) {
                Long participantId = participantIdByName.get(absentName);
                roundAbsenceMapper.insert(RoundAbsence.builder()
                        .roundId(roundId)
                        .participantId(participantId)
                        .settlementId(settlementId)
                        .build());
            }

            for (ValidatedItem validatedItem : round.items()) {
                Item item = Item.builder()
                        .roundId(roundId)
                        .settlementId(settlementId)
                        .name(validatedItem.name())
                        .qty(validatedItem.qty())
                        .price(validatedItem.price())
                        .build();
                itemMapper.insert(item);
                Long itemId = item.getId();

                for (String excludedName : validatedItem.excludedNames()) {
                    Long participantId = participantIdByName.get(excludedName);
                    itemExclusionMapper.insert(ItemExclusion.builder()
                            .itemId(itemId)
                            .participantId(participantId)
                            .settlementId(settlementId)
                            .build());
                }
            }
        }

        return SettlementCreateResponse.builder()
                .settlementId(settlementId)
                .adminToken(rawAdminToken)
                .build();
    }

    @Transactional(readOnly = true)
    public SettlementPublicResponse getSettlementPublic(String sid) {
        Settlement settlement = findActiveSettlementOrThrow(sid);
        List<SettlementParticipant> participants = settlementParticipantMapper.selectBySettlementId(sid);

        List<ParticipantSummary> summaries = participants.stream()
                .map(p -> ParticipantSummary.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .isHost(p.isHost())
                        .claimed(p.getTokenHash() != null)
                        .build())
                .toList();

        return SettlementPublicResponse.builder()
                .title(settlement.getTitle())
                .settledOn(settlement.getSettledOn())
                .participants(summaries)
                .build();
    }

    @Transactional
    public ParticipantClaimResponse claimParticipant(String sid, ParticipantClaimRequest request) {
        findActiveSettlementOrThrow(sid);

        if (request == null || request.getParticipantId() == null) {
            throw new IllegalArgumentException("참여자 ID가 필요합니다.");
        }

        Long participantId = request.getParticipantId();
        String rawParticipantToken = TokenUtil.generateToken();
        String tokenHash = TokenUtil.sha256Hex(rawParticipantToken);

        int updated = settlementParticipantMapper.claimParticipant(participantId, sid, tokenHash);
        if (updated == 1) {
            return ParticipantClaimResponse.builder()
                    .participantId(participantId)
                    .participantToken(rawParticipantToken)
                    .build();
        }

        SettlementParticipant existing = settlementParticipantMapper.selectByIdAndSettlementId(participantId, sid);
        if (existing == null || existing.isHost()) {
            throw new IllegalArgumentException("점유할 수 없는 참여자입니다.");
        }
        if (existing.getTokenHash() != null) {
            throw new ConflictException();
        }

        throw new ConflictException("점유할 수 없는 참여자입니다.");
    }

    @Transactional(readOnly = true)
    public ParticipantShareResponse getMyShare(String sid, String authorizationHeader) {
        SettlementParticipant me = verifyParticipantToken(sid, authorizationHeader);
        Settlement settlement = findActiveSettlementOrThrow(sid);

        List<SettlementParticipant> participants = settlementParticipantMapper.selectBySettlementId(sid);
        List<SettleRound> rounds = settleRoundMapper.selectBySettlementId(sid);
        List<RoundAbsence> absences = roundAbsenceMapper.selectBySettlementId(sid);
        List<Item> items = itemMapper.selectBySettlementId(sid);
        List<ItemExclusion> exclusions = itemExclusionMapper.selectBySettlementId(sid);

        Map<Long, String> participantNameById = new LinkedHashMap<>();
        List<ParticipantInput> calcParticipants = new ArrayList<>(participants.size());
        for (SettlementParticipant p : participants) {
            participantNameById.put(p.getId(), p.getName());
            calcParticipants.add(new ParticipantInput(p.getName(), p.isHost()));
        }

        Map<Long, Set<Long>> absentParticipantIdsByRoundId = new LinkedHashMap<>();
        for (RoundAbsence absence : absences) {
            absentParticipantIdsByRoundId
                    .computeIfAbsent(absence.getRoundId(), k -> new LinkedHashSet<>())
                    .add(absence.getParticipantId());
        }

        Map<Long, Set<Long>> excludedParticipantIdsByItemId = new LinkedHashMap<>();
        for (ItemExclusion exclusion : exclusions) {
            excludedParticipantIdsByItemId
                    .computeIfAbsent(exclusion.getItemId(), k -> new LinkedHashSet<>())
                    .add(exclusion.getParticipantId());
        }

        Map<Long, List<Item>> itemsByRoundId = new LinkedHashMap<>();
        for (Item item : items) {
            itemsByRoundId
                    .computeIfAbsent(item.getRoundId(), k -> new ArrayList<>())
                    .add(item);
        }

        List<RoundInput> calcRounds = new ArrayList<>(rounds.size());
        List<ShareItem> myShareItems = new ArrayList<>();
        List<String> myExcludedItems = new ArrayList<>();
        List<Integer> myAbsentRounds = new ArrayList<>();

        for (SettleRound round : rounds) {
            Set<Long> absentIds = absentParticipantIdsByRoundId.getOrDefault(
                    round.getId(), Collections.emptySet());
            List<String> absentNames = absentIds.stream()
                    .map(participantNameById::get)
                    .filter(name -> name != null)
                    .toList();

            boolean isMeAbsent = absentIds.contains(me.getId());
            if (isMeAbsent) {
                myAbsentRounds.add(round.getRoundNumber());
            }

            List<Item> roundItems = itemsByRoundId.getOrDefault(round.getId(), Collections.emptyList());
            List<ItemInput> calcItems = new ArrayList<>(roundItems.size());

            for (Item item : roundItems) {
                Set<Long> excludedIds = excludedParticipantIdsByItemId.getOrDefault(
                        item.getId(), Collections.emptySet());
                List<String> excludedNames = excludedIds.stream()
                        .map(participantNameById::get)
                        .filter(name -> name != null)
                        .toList();

                calcItems.add(new ItemInput(item.getName(), item.getPrice(), excludedNames));

                if (!isMeAbsent) {
                    if (excludedIds.contains(me.getId())) {
                        myExcludedItems.add(item.getName());
                    } else {
                        long eaterCount = participants.stream()
                                .filter(p -> !absentIds.contains(p.getId()) && !excludedIds.contains(p.getId()))
                                .count();
                        // 항목별 share는 화면 표시용 값으로 원 미만을 내림(정수 나눗셈)한다.
                        // 최종 amount는 SettlementCalculator가 차수 가감액 배분 및 전 차수 합산 후 1회만 내림하여 계산하므로,
                        // 항목별 share 합계가 최종 amount와 몇 원 다를 수 있다.
                        long displayShare = eaterCount > 0 ? (item.getPrice() / eaterCount) : 0L;
                        myShareItems.add(ShareItem.builder()
                                .roundNumber(round.getRoundNumber())
                                .placeName(round.getPlaceName())
                                .name(item.getName())
                                .share(displayShare)
                                .build());
                    }
                }
            }

            int adjustment = round.getAdjustmentAmount() == null ? 0 : round.getAdjustmentAmount();
            calcRounds.add(new RoundInput(
                    round.getRoundNumber(),
                    adjustment,
                    absentNames,
                    calcItems));
        }

        CalculationResult calcResult = settlementCalculator.calculate(calcParticipants, calcRounds);
        long myAmount = calcResult.amounts().getOrDefault(me.getName(), 0L);

        AccountInfo account = null;
        if (settlement.getBank() != null
                || settlement.getAccountNumber() != null
                || settlement.getAccountHolder() != null) {
            account = AccountInfo.builder()
                    .bank(settlement.getBank())
                    .accountNumber(settlement.getAccountNumber())
                    .accountHolder(settlement.getAccountHolder())
                    .build();
        }

        return ParticipantShareResponse.builder()
                .name(me.getName())
                .amount(myAmount)
                .items(myShareItems)
                .excludedItems(myExcludedItems)
                .absentRounds(myAbsentRounds)
                .account(account)
                .payLink(settlement.getPayLink())
                .payStatus(me.getPayStatus())
                .build();
    }

    @Transactional
    public PaymentStatusResponse reportPayment(String sid, String authorizationHeader) {
        SettlementParticipant me = verifyParticipantToken(sid, authorizationHeader);
        if (me.isHost()) {
            throw new IllegalArgumentException("총무는 송금 신고를 할 수 없습니다.");
        }

        int updated = settlementParticipantMapper.reportPayment(me.getId(), sid, me.getTokenHash());
        if (updated == 1) {
            return new PaymentStatusResponse("REPORTED");
        }

        SettlementParticipant current = settlementParticipantMapper.selectByIdAndSettlementId(me.getId(), sid);
        if (current == null) {
            throw new NotFoundException();
        }
        if (current.isHost()) {
            throw new IllegalArgumentException("총무는 송금 신고를 할 수 없습니다.");
        }
        throw new ConflictException("이미 송금 신고되었거나 입금 확인된 상태예요");
    }

    @Transactional
    public PaymentStatusResponse cancelReportPayment(String sid, String authorizationHeader) {
        SettlementParticipant me = verifyParticipantToken(sid, authorizationHeader);
        if (me.isHost()) {
            throw new IllegalArgumentException("총무는 송금 신고 취소를 할 수 없습니다.");
        }

        int updated = settlementParticipantMapper.cancelReportPayment(me.getId(), sid, me.getTokenHash());
        if (updated == 1) {
            return new PaymentStatusResponse("UNPAID");
        }

        SettlementParticipant current = settlementParticipantMapper.selectByIdAndSettlementId(me.getId(), sid);
        if (current == null) {
            throw new NotFoundException();
        }
        if (current.isHost()) {
            throw new IllegalArgumentException("총무는 송금 신고 취소를 할 수 없습니다.");
        }
        if ("CONFIRMED".equals(current.getPayStatus())) {
            throw new ConflictException("이미 입금 확인된 내역은 취소할 수 없어요");
        }
        throw new ConflictException("송금 신고 상태가 아니에요");
    }

    /**
     * 총무 토큰 검증 헬퍼.
     * 정산이 없거나 만료되었거나 토큰이 없거나 해시가 일치하지 않으면 모두 동일한 404(NotFoundException)로 응답한다.
     */
    @Transactional(readOnly = true)
    public Settlement verifyAdminToken(String sid, String authorizationHeader) {
        String rawToken = extractBearerTokenOrThrow(authorizationHeader);
        Settlement settlement = findActiveSettlementOrThrow(sid);
        if (!TokenUtil.verifyToken(rawToken, settlement.getAdminTokenHash())) {
            throw new NotFoundException();
        }
        return settlement;
    }

    /**
     * 참여자 토큰 검증 헬퍼.
     * 정산이 없거나 만료되었거나 토큰이 없거나 일치하는 참여자가 없으면 모두 동일한 404(NotFoundException)로 응답한다.
     */
    @Transactional(readOnly = true)
    public SettlementParticipant verifyParticipantToken(String sid, String authorizationHeader) {
        String rawToken = extractBearerTokenOrThrow(authorizationHeader);
        findActiveSettlementOrThrow(sid);

        List<SettlementParticipant> participants = settlementParticipantMapper.selectBySettlementId(sid);
        SettlementParticipant matched = null;
        for (SettlementParticipant participant : participants) {
            if (participant.getTokenHash() != null
                    && TokenUtil.verifyToken(rawToken, participant.getTokenHash())) {
                matched = participant;
            }
        }
        if (matched == null) {
            throw new NotFoundException();
        }
        return matched;
    }

    private Settlement findActiveSettlementOrThrow(String sid) {
        if (sid == null || sid.isBlank() || sid.length() > 21) {
            throw new NotFoundException();
        }
        Settlement settlement = settlementMapper.selectById(sid);
        if (settlement == null) {
            throw new NotFoundException();
        }
        if (settlement.getExpiresAt() != null && settlement.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new NotFoundException();
        }
        return settlement;
    }

    private String extractBearerTokenOrThrow(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new NotFoundException();
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new NotFoundException();
        }
        return token;
    }

    private ValidatedCreateInput validateCreateRequest(SettlementCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("요청 본문이 비어 있습니다.");
        }

        String title = trimToNull(request.getTitle());
        if (title == null || title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("정산 제목은 1~60자여야 합니다.");
        }
        if (request.getSettledOn() == null) {
            throw new IllegalArgumentException("정산 날짜가 필요합니다.");
        }

        String bank = validateOptionalLength(request.getBank(), MAX_BANK_LENGTH, "은행명");
        String accountNumber = validateOptionalLength(
                request.getAccountNumber(), MAX_ACCOUNT_NUMBER_LENGTH, "계좌번호");
        String accountHolder = validateOptionalLength(
                request.getAccountHolder(), MAX_ACCOUNT_HOLDER_LENGTH, "예금주");
        String payLink = validatePayLink(request.getPayLink());

        List<ParticipantRequest> reqParticipants = request.getParticipants();
        if (reqParticipants == null || reqParticipants.isEmpty() || reqParticipants.size() > MAX_PARTICIPANTS) {
            throw new IllegalArgumentException("참여자는 1명 이상 30명 이하여야 합니다.");
        }

        List<ParticipantInput> calcParticipants = new ArrayList<>(reqParticipants.size());
        Set<String> participantNames = new LinkedHashSet<>();
        int hostCount = 0;

        for (ParticipantRequest p : reqParticipants) {
            if (p == null) {
                throw new IllegalArgumentException("참여자 정보가 올바르지 않습니다.");
            }
            String name = trimToNull(p.getName());
            if (name == null || name.length() > MAX_PARTICIPANT_NAME_LENGTH) {
                throw new IllegalArgumentException("참여자 이름은 1~30자여야 합니다.");
            }
            if (!participantNames.add(name)) {
                throw new IllegalArgumentException("참여자 이름이 중복되었습니다.");
            }
            boolean isHost = Boolean.TRUE.equals(p.getIsHost());
            if (isHost) {
                hostCount++;
            }
            calcParticipants.add(new ParticipantInput(name, isHost));
        }

        if (hostCount != 1) {
            throw new IllegalArgumentException("총무는 정확히 1명이어야 합니다.");
        }

        List<RoundRequest> reqRounds = request.getRounds();
        if (reqRounds == null || reqRounds.isEmpty() || reqRounds.size() > MAX_ROUNDS) {
            throw new IllegalArgumentException("차수는 1개 이상 10개 이하여야 합니다.");
        }

        Set<Integer> seenRoundNumbers = new HashSet<>();
        List<ValidatedRound> validatedRounds = new ArrayList<>(reqRounds.size());
        List<RoundInput> calcRounds = new ArrayList<>(reqRounds.size());

        for (RoundRequest r : reqRounds) {
            if (r == null || r.getRoundNumber() == null || r.getRoundNumber() < 1) {
                throw new IllegalArgumentException("차수 번호는 1 이상이어야 합니다.");
            }
            if (!seenRoundNumbers.add(r.getRoundNumber())) {
                throw new IllegalArgumentException("중복된 차수 번호가 있습니다.");
            }

            String placeName = validateOptionalLength(r.getPlaceName(), MAX_PLACE_NAME_LENGTH, "장소명");

            Integer receiptTotal = r.getReceiptTotal();
            if (receiptTotal != null && (receiptTotal < 0 || receiptTotal > MAX_ROUND_AMOUNT)) {
                throw new IllegalArgumentException("영수증 총액 범위를 확인해 주세요.");
            }

            int adjustmentAmount = r.getAdjustmentAmount() == null ? 0 : r.getAdjustmentAmount();
            if (adjustmentAmount < -MAX_ROUND_AMOUNT || adjustmentAmount > MAX_ROUND_AMOUNT) {
                throw new IllegalArgumentException("가감액 범위를 확인해 주세요.");
            }

            List<String> absentNames = normalizeAndValidateNames(
                    r.getAbsentNames(), participantNames, "차수 불참자");

            List<ItemRequest> reqItems = r.getItems();
            if (reqItems == null || reqItems.isEmpty() || reqItems.size() > MAX_ITEMS_PER_ROUND) {
                throw new IllegalArgumentException("차수당 메뉴는 1개 이상 50개 이하여야 합니다.");
            }

            List<ValidatedItem> validatedItems = new ArrayList<>(reqItems.size());
            List<ItemInput> calcItems = new ArrayList<>(reqItems.size());

            for (ItemRequest item : reqItems) {
                if (item == null) {
                    throw new IllegalArgumentException("메뉴 정보가 올바르지 않습니다.");
                }
                String itemName = trimToNull(item.getName());
                if (itemName == null || itemName.length() > MAX_ITEM_NAME_LENGTH) {
                    throw new IllegalArgumentException("메뉴 이름은 1~60자여야 합니다.");
                }
                int qty = item.getQty() == null ? 1 : item.getQty();
                if (qty < 1 || qty > MAX_ITEM_QTY) {
                    throw new IllegalArgumentException("메뉴 수량은 1 이상이어야 합니다.");
                }
                if (item.getPrice() == null || item.getPrice() < 0 || item.getPrice() > MAX_ITEM_PRICE) {
                    throw new IllegalArgumentException("메뉴 가격은 0 이상 10,000,000 이하여야 합니다.");
                }

                List<String> excludedNames = normalizeAndValidateNames(
                        item.getExcludedNames(), participantNames, "메뉴 제외자");

                validatedItems.add(new ValidatedItem(itemName, qty, item.getPrice(), excludedNames));
                calcItems.add(new ItemInput(itemName, item.getPrice(), excludedNames));
            }

            validatedRounds.add(new ValidatedRound(
                    r.getRoundNumber(),
                    placeName,
                    receiptTotal,
                    adjustmentAmount,
                    absentNames,
                    validatedItems));
            calcRounds.add(new RoundInput(
                    r.getRoundNumber(),
                    adjustmentAmount,
                    absentNames,
                    calcItems));
        }

        return new ValidatedCreateInput(
                title,
                bank,
                accountNumber,
                accountHolder,
                payLink,
                calcParticipants,
                validatedRounds,
                calcRounds);
    }

    private List<String> normalizeAndValidateNames(
            List<String> rawNames,
            Set<String> validParticipantNames,
            String fieldLabel) {
        if (rawNames == null || rawNames.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> deduplicated = new LinkedHashSet<>();
        for (String raw : rawNames) {
            String trimmed = trimToNull(raw);
            if (trimmed == null || !validParticipantNames.contains(trimmed)) {
                throw new IllegalArgumentException(fieldLabel + "에 존재하지 않는 참여자 이름이 포함되어 있습니다.");
            }
            deduplicated.add(trimmed);
        }
        return List.copyOf(deduplicated);
    }

    private String validateOptionalLength(String raw, int maxLength, String fieldLabel) {
        String trimmed = trimToNull(raw);
        if (trimmed != null && trimmed.length() > maxLength) {
            throw new IllegalArgumentException(fieldLabel + " 길이가 제한을 초과했습니다.");
        }
        return trimmed;
    }

    private String validatePayLink(String rawPayLink) {
        String trimmed = trimToNull(rawPayLink);
        if (trimmed == null) {
            return null;
        }
        if (trimmed.length() > MAX_PAY_LINK_LENGTH) {
            throw new IllegalArgumentException("송금 링크 길이가 제한을 초과했습니다.");
        }
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("올바르지 않은 송금 링크 형식입니다.");
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null
                || !scheme.equalsIgnoreCase("https")
                || host == null
                || !host.toLowerCase(Locale.ROOT).equals(ALLOWED_PAY_LINK_HOST)
                || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("허용되지 않은 송금 링크 도메인입니다.");
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record ValidatedCreateInput(
            String title,
            String bank,
            String accountNumber,
            String accountHolder,
            String payLink,
            List<ParticipantInput> calcParticipants,
            List<ValidatedRound> rounds,
            List<RoundInput> calcRounds) {
    }

    private record ValidatedRound(
            int roundNumber,
            String placeName,
            Integer receiptTotal,
            int adjustmentAmount,
            List<String> absentNames,
            List<ValidatedItem> items) {
    }

    private record ValidatedItem(
            String name,
            int qty,
            int price,
            List<String> excludedNames) {
    }
}
