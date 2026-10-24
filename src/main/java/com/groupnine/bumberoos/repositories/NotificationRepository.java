package com.groupnine.bumberoos.repositories;

import com.groupnine.bumberoos.domain.entities.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop50ByOrderByCreatedAtDescIdDesc();

    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.read = true where n.read = false")
    int markAllRead();
}
