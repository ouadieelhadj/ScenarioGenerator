package com.staging.sg.repository;

import com.staging.sg.entity.MessageType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageTypeRepository extends JpaRepository<MessageType, Long> {
    List<MessageType> findByActiveTrue();
    List<MessageType> findByCategory(String category);
}
