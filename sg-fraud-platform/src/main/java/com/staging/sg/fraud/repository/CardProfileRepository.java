package com.staging.sg.fraud.repository;
import com.staging.sg.fraud.domain.CardProfile; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface CardProfileRepository extends JpaRepository<CardProfile,UUID>{Optional<CardProfile> findByMemberIdAndTokenHash(String memberId,String tokenHash);}
