package com.staging.sg.common.repository;

import com.staging.sg.common.entity.MessageType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageTypeRepository extends JpaRepository<MessageType, Long> {
    List<MessageType> findByActiveTrue();
    List<MessageType> findByCategory(String category);
    List<MessageType> findByNetworkAndCategory(String network, String category);
    List<MessageType> findByNetwork(String network);
}
