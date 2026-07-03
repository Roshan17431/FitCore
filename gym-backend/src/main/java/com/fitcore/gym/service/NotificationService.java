package com.fitcore.gym.service;

import com.fitcore.gym.dto.notification.NotificationDto;
import com.fitcore.gym.entity.Notification;
import com.fitcore.gym.entity.User;
import com.fitcore.gym.repository.NotificationRepository;
import com.fitcore.gym.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public List<NotificationDto> getUserNotifications(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return notificationRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private NotificationDto mapToDto(Notification notification) {
        return NotificationDto.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .type(notification.getType())
                .readFlag(notification.getReadFlag())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
