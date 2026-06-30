package com.fitcore.gym.controller;

import com.fitcore.gym.dto.ImageUploadResponse;
import com.fitcore.gym.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class ImageController {

    private final S3Service s3Service;

    @PostMapping("/upload")
    public ResponseEntity<ImageUploadResponse> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("folder") String folder
    ) {
        String url = s3Service.uploadFile(file, folder);
        return ResponseEntity.ok(new ImageUploadResponse(url, folder));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Void> deleteImage(@RequestParam("url") String url) {
        s3Service.deleteFile(url);
        return ResponseEntity.ok().build();
    }
}
