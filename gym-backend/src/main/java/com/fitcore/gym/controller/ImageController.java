package com.fitcore.gym.controller;

import com.fitcore.gym.dto.ImageUploadResponse;
import com.fitcore.gym.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aws.s3.enabled", havingValue = "true")
@Tag(name = "Image Upload", description = "Endpoints for uploading and deleting images via AWS S3")
@SecurityRequirement(name = "Bearer Authentication")
public class ImageController {

    private final S3Service s3Service;

    @Operation(
            summary = "Upload an image",
            description = "Uploads an image file to a specified folder in AWS S3 and returns the URL.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Image successfully uploaded"),
                    @ApiResponse(responseCode = "400", description = "Invalid file or folder"),
                    @ApiResponse(responseCode = "500", description = "Server error uploading file")
            }
    )
    @PostMapping("/upload")
    public ResponseEntity<ImageUploadResponse> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("folder") String folder
    ) {
        String url = s3Service.uploadFile(file, folder);
        return ResponseEntity.ok(new ImageUploadResponse(url, folder));
    }

    @Operation(
            summary = "Delete an image",
            description = "Deletes an image from AWS S3 using its key.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Image successfully deleted"),
                    @ApiResponse(responseCode = "500", description = "Server error deleting file")
            }
    )
    @DeleteMapping("/delete")
    public ResponseEntity<Void> deleteImage(@RequestParam("url") String url) {
        s3Service.deleteFile(url);
        return ResponseEntity.ok().build();
    }
}
