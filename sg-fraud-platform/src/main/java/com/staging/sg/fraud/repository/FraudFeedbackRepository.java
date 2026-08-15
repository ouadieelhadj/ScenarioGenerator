package com.staging.sg.fraud.repository;
import com.staging.sg.fraud.domain.FraudFeedback; import org.springframework.data.jpa.repository.JpaRepository; import java.util.UUID;
public interface FraudFeedbackRepository extends JpaRepository<FraudFeedback,UUID>{}
