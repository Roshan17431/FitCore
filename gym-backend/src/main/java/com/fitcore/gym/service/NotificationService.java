package com.fitcore.gym.service;

import com.fitcore.gym.dto.notification.NotificationDto;
import com.fitcore.gym.entity.Notification;
import com.fitcore.gym.entity.User;
import com.fitcore.gym.repository.NotificationRepository;
import com.fitcore.gym.repository.UserRepository;
import com.fitcore.gym.security.TenantAccess;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@org.springframework.transaction.annotation.Transactional
@RequiredArgsConstructor
public class NotificationService {
    private final TenantAccess tenantAccess;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @PreAuthorize("@recordAccess.user(authentication, #userId)")
    public List<NotificationDto> getUserNotifications(UUID userId) {
        User user = tenantAccess.requireUser(userId);

        return notificationRepository
                .findByGymIdAndUserIdOrderByCreatedAtDesc(tenantAccess.gym(), user.getId())
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private NotificationDto mapToDto(Notification notification) {
        tenantAccess.requireOwned(notification);
        return NotificationDto.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .type(notification.getType())
                .readFlag(notification.getReadFlag())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
