package com.iorigination.configportal.repository;

import com.iorigination.configportal.model.*;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface GlobalConfigRepository extends MongoRepository<GlobalConfig, String> {
    List<GlobalConfig> findByMarketCode(String marketCode);
    List<GlobalConfig> findByStatus(String status);
    Optional<GlobalConfig> findByMarketCodeAndProductId(String marketCode, String productId);

    @Query("{ 'marketCode': ?0, 'status': 'ACTIVE' }")
    List<GlobalConfig> findActiveByMarket(String marketCode);
}
