package com.ausaf.sudoku.controllers;

import com.ausaf.sudoku.entity.Notification;
import com.ausaf.sudoku.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @PostMapping(consumes = "multipart/form-data")
    public Boolean createNotificationWithImage(
            @ModelAttribute Notification notification,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) throws IOException {
        return notificationService.createNotification(notification, image);
    }


    @GetMapping
    public List<Notification> getAllNotifications() {
        return notificationService.getAllNotifications();
    }
}
