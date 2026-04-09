package com.iorigination.configportal.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Document(collection = "supported_integrations")
@CompoundIndex(def = "{'integrationType': 1, 'providerCode': 1}", unique = true)
public class SupportedIntegration {
    @Id
    private String id;
    private String integrationType;  // BUREAU | IDV | AML | FRAUD | SIGNING | SMS
    private String providerCode;     // SCHUFA | UC_SE | SIGNICAT_EID …
    private String displayName;
    private List<String> supportedMarkets;
    private String status;           // LIVE | BETA | PLANNED | DEPRECATED
    private Map<String, Object> endpointConfig;
    private String lambdaArn;        // in prod; endpoint in local
    private String lastTestedAt;
    private String notes;
    private boolean isMandatory;
}
