package com.cepsandik.bulletinboard.controller;

import com.cepsandik.bulletinboard.dto.AppendRecordRequest;
import com.cepsandik.bulletinboard.entity.BulletinRecord;
import com.cepsandik.bulletinboard.service.BulletinBoardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/bulletin")
public class BulletinBoardController {

    private final BulletinBoardService service;

    @PostMapping("/internal/records")
    public ResponseEntity<BulletinRecord> append(@Valid @RequestBody AppendRecordRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.append(request));
    }

    @GetMapping("/elections/{electionId}/record")
    public List<BulletinRecord> electionRecord(@PathVariable String electionId) {
        return service.electionRecord(electionId);
    }

    @GetMapping("/elections/{electionId}/ballots/{trackingCode}")
    public BulletinRecord ballotByTrackingCode(@PathVariable String electionId, @PathVariable String trackingCode) {
        return service.ballotByTrackingCode(electionId, trackingCode);
    }
}
