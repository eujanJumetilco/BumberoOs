package com.groupnine.bumberoos.repositories;

import com.groupnine.bumberoos.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findAllByOrderByEventDateAsc();
}