package com.ausaf.sudoku.service;

import com.ausaf.sudoku.entity.Notification;
import com.ausaf.sudoku.repository.notification.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ImageUploadService imageUploadService;

    public boolean createNotification(Notification notification, MultipartFile image) throws IOException {
        if (image != null && !image.isEmpty()) {
            String imageUrl = imageUploadService.uploadFile(image);
            notification.setImageUrl(imageUrl);
        }
        notificationRepository.save(notification);
        return true;
    }

    public List<Notification> getAllNotifications() {
        return notificationRepository.findAll();
    }
}
