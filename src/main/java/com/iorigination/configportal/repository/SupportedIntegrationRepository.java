package com.iorigination.configportal.repository;

import com.iorigination.configportal.model.SupportedIntegration;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SupportedIntegrationRepository extends MongoRepository<SupportedIntegration, String> {
    List<SupportedIntegration> findByIntegrationType(String integrationType);

    @Query("{ 'integrationType': ?0, 'supportedMarkets': ?1, 'status': { $in: ['LIVE','BETA'] } }")
    List<SupportedIntegration> findByTypeAndMarket(String integrationType, String marketCode);

    @Query("{ 'supportedMarkets': ?0 }")
    List<SupportedIntegration> findByMarket(String marketCode);
}
