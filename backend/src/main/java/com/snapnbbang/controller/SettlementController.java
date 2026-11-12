package com.snapnbbang.controller;

import com.snapnbbang.dto.ParticipantClaimRequest;
import com.snapnbbang.dto.ParticipantClaimResponse;
import com.snapnbbang.dto.SettlementCreateRequest;
import com.snapnbbang.dto.SettlementCreateResponse;
import com.snapnbbang.dto.SettlementPublicResponse;
import com.snapnbbang.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    @PostMapping
    public ResponseEntity<SettlementCreateResponse> createSettlement(
            @RequestBody(required = false) SettlementCreateRequest request) {
        SettlementCreateResponse response = settlementService.createSettlement(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{sid}")
    public ResponseEntity<Void> deleteSettlement(
            @PathVariable("sid") String sid,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        // TODO: 총무 토큰 검증 후 정산 삭제 구현
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @GetMapping("/{sid}")
    public ResponseEntity<SettlementPublicResponse> getSettlement(
            @PathVariable("sid") String sid) {
        SettlementPublicResponse response = settlementService.getSettlementPublic(sid);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{sid}/claim")
    public ResponseEntity<ParticipantClaimResponse> claimParticipant(
            @PathVariable("sid") String sid,
            @RequestBody(required = false) ParticipantClaimRequest request) {
        ParticipantClaimResponse response = settlementService.claimParticipant(sid, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{sid}/me")
    public ResponseEntity<Void> getMyShare(
            @PathVariable("sid") String sid,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        // TODO: 참여자 토큰 검증 후 내 금액, 메뉴 내역, 입금 계좌 조회 구현
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @PostMapping("/{sid}/report")
    public ResponseEntity<Void> reportPayment(
            @PathVariable("sid") String sid,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        // TODO: 참여자 토큰 검증 후 송금 신고(UNPAID -> REPORTED) 구현
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @DeleteMapping("/{sid}/report")
    public ResponseEntity<Void> cancelReportPayment(
            @PathVariable("sid") String sid,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        // TODO: 참여자 토큰 검증 후 송금 신고 취소(REPORTED -> UNPAID) 구현
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @GetMapping("/{sid}/status")
    public ResponseEntity<Void> getSettlementStatus(
            @PathVariable("sid") String sid,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        // TODO: 총무 토큰 검증 후 입금 현황 조회 구현
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @PostMapping("/{sid}/participants/{pid}/confirm")
    public ResponseEntity<Void> confirmPayment(
            @PathVariable("sid") String sid,
            @PathVariable("pid") Long pid,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        // TODO: 총무 토큰 검증 후 입금 확인(REPORTED -> CONFIRMED) 구현
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @DeleteMapping("/{sid}/participants/{pid}/claim")
    public ResponseEntity<Void> releaseClaim(
            @PathVariable("sid") String sid,
            @PathVariable("pid") Long pid,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        // TODO: 총무 토큰 검증 후 참여자 이름 점유 해제 구현
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
