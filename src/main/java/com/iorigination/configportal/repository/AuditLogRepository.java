package com.iorigination.configportal.repository;

import com.iorigination.configportal.model.AuditLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AuditLogRepository extends MongoRepository<AuditLog, String> {
    List<AuditLog> findByConfigIdOrderByChangedAtDesc(String configId);
    List<AuditLog> findByMarketCodeAndProductIdOrderByVersionDesc(String marketCode, String productId);
    List<AuditLog> findByStatusOrderByChangedAtDesc(String status);
}
