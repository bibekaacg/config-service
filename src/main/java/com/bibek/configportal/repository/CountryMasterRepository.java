package com.bibek.configportal.repository;

import com.bibek.configportal.model.CountryMaster;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CountryMasterRepository extends MongoRepository<CountryMaster, String> {
    List<CountryMaster> findByStatus(String status);
}
