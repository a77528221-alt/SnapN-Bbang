package com.snapnbbang.controller;

import com.snapnbbang.dto.ReceiptParseResponse;
import com.snapnbbang.service.ReceiptParseService;
import com.snapnbbang.service.ReceiptRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/receipts")
public class ReceiptController {

    private final ReceiptParseService receiptParseService;
    private final ReceiptRateLimiter receiptRateLimiter;

    public ReceiptController(
            ReceiptParseService receiptParseService,
            ReceiptRateLimiter receiptRateLimiter) {
        this.receiptParseService = receiptParseService;
        this.receiptRateLimiter = receiptRateLimiter;
    }

    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReceiptParseResponse> parseReceipt(
            @RequestParam(value = "image", required = false) MultipartFile image,
            HttpServletRequest request) {
        receiptRateLimiter.checkAndRecord(extractClientIp(request));
        ReceiptParseResponse response = receiptParseService.parseReceipt(image);
        return ResponseEntity.ok(response);
    }

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int commaIdx = forwardedFor.indexOf(',');
            return (commaIdx > 0 ? forwardedFor.substring(0, commaIdx) : forwardedFor).trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr == null || remoteAddr.isBlank()) ? "unknown" : remoteAddr;
    }
}
