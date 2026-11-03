package com.snapnbbang.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/receipts")
public class ReceiptController {

    @PostMapping("/parse")
    public ResponseEntity<Void> parseReceipt(
            @RequestParam(value = "file", required = false) MultipartFile file) {
        // TODO: 영수증 이미지 검증 및 Gemini 멀티모달 API 파싱 구현
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}

