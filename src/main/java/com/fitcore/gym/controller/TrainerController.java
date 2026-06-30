package com.fitcore.gym.controller;

import com.fitcore.gym.dto.trainer.TrainerProfileDto;
import com.fitcore.gym.dto.trainer.TrainerSessionDto;
import com.fitcore.gym.service.TrainerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/trainers")
@RequiredArgsConstructor
@Tag(name = "Trainer", description = "Endpoints for managing trainers and session bookings")
@SecurityRequirement(name = "Bearer Authentication")
public class TrainerController {

    private final TrainerService trainerService;

    @Operation(
            summary = "Get all trainers",
            description = "Retrieves a list of all active trainers in the gym.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successfully retrieved trainers")
            }
    )
    @GetMapping
    public ResponseEntity<List<TrainerProfileDto>> getAllTrainers() {
        return ResponseEntity.ok(trainerService.getAllTrainers());
    }

    @Operation(
            summary = "Book a trainer session",
            description = "Books a personal training session between a member and a trainer.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successfully booked session"),
                    @ApiResponse(responseCode = "400", description = "Validation error"),
                    @ApiResponse(responseCode = "404", description = "Member or trainer not found")
            }
    )
    @PostMapping("/book/{memberId}/{trainerId}")
    public ResponseEntity<TrainerSessionDto> bookSession(
            @PathVariable UUID memberId,
            @PathVariable Long trainerId,
            @Valid @RequestBody TrainerSessionDto request
    ) {
        return ResponseEntity.ok(trainerService.bookSession(memberId, trainerId, request));
    }

    @Operation(
            summary = "Get member sessions",
            description = "Retrieves all booked sessions for a specific member.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successfully retrieved sessions")
            }
    )
    @GetMapping("/sessions/{memberId}")
    public ResponseEntity<List<TrainerSessionDto>> getMemberSessions(@PathVariable UUID memberId) {
        return ResponseEntity.ok(trainerService.getMemberSessions(memberId));
    }
}
