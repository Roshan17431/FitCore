package com.fitcore.gym.controller;

import com.fitcore.gym.dto.trainer.TrainerProfileDto;
import com.fitcore.gym.dto.trainer.TrainerSessionDto;
import com.fitcore.gym.service.TrainerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/trainers")
@RequiredArgsConstructor
public class TrainerController {

    private final TrainerService trainerService;

    @GetMapping
    public ResponseEntity<List<TrainerProfileDto>> getAllTrainers() {
        return ResponseEntity.ok(trainerService.getAllTrainers());
    }

    @PostMapping("/book/{memberId}/{trainerId}")
    public ResponseEntity<TrainerSessionDto> bookSession(
            @PathVariable UUID memberId,
            @PathVariable Long trainerId,
            @RequestBody TrainerSessionDto request
    ) {
        return ResponseEntity.ok(trainerService.bookSession(memberId, trainerId, request));
    }

    @GetMapping("/sessions/{memberId}")
    public ResponseEntity<List<TrainerSessionDto>> getMemberSessions(@PathVariable UUID memberId) {
        return ResponseEntity.ok(trainerService.getMemberSessions(memberId));
    }
}
