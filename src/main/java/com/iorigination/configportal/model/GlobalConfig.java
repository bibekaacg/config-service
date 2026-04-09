package com.iorigination.configportal.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * GlobalConfig — equivalent of DynamoDB GlobalConfig table.
 * PK in DynamoDB = marketCode#productId  →  stored as _id in MongoDB.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "global_config")
public class GlobalConfig {

    /** Composite key: e.g. "DE#PERSONAL_LOAN_V1" */
    @Id
    private String id;

    @Indexed
    private String marketCode;

    @Indexed
    private String productId;

    private String productName;
    private String status;   // ACTIVE | DRAFT | ARCHIVED
    private int    version;
    private String effectiveDate;
    private MarketInfo    market;
    private ProductLimits product;
    private WorkflowFlags workflow;
    private FccConfig     fcc;
    private CreditConfig  credit;
    private DocumentConfig documents;
    private NotificationConfig notifications;
    private MetaInfo      meta;

    // ── Nested types ───────────────────────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MarketInfo {
        private String currency;
        private String language;
        private String timezone;
        private String regulatoryBody;
        private String dataResidencyRegion;
        private String gdprDataController;
        private String legalEntityId;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ProductLimits {
        private double minAmount;
        private double maxAmount;
        private int    minTermMonths;
        private int    maxTermMonths;
        private String interestRateModel;
        private double interestRateMin;
        private double interestRateMax;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class WorkflowFlags {
        private boolean idvEnabled;
        private String  idvProvider;
        private String  signicatProfile;
        private boolean amlEnabled;
        private boolean fraudCheckEnabled;
        private boolean pepScreeningEnabled;
        private boolean eSigningEnabled;
        private String  signingProvider;
        private String  documentSet;
        private boolean brokerCallbackEnabled;
        private boolean outboundSalesEnabled;
        private boolean referralPortalEnabled;
        private List<String> workflowSteps;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class FccConfig {
        private int          amlRiskScoreThreshold;
        private int          fraudScoreThreshold;
        private int          manualReviewThreshold;
        private List<String> sanctionsListIds;
        private String       amlProvider;
        private String       fraudProvider;
        private String       pepDatabaseVersion;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CreditConfig {
        private int    minCreditScore;
        private double dtiMaxRatio;
        private String bureauProvider;
        private String scorecardId;
        private String decisionEngineVersion;
        private String bureauConsentWording;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DocumentConfig {
        private String templateSet;
        private String loanAgreementTemplate;
        private String secciTemplate;
        private String consentFormTemplate;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class NotificationConfig {
        private String       emailTemplateSet;
        private String       smsProvider;
        private List<String> supportedLanguages;
        private String       customerSupportNumber;
        private String       defaultSenderEmail;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MetaInfo {
        private String  lastModifiedBy;
        private Instant lastModifiedAt;
        private String  approvedBy;
        private Instant approvedAt;
        private String  changeComment;
    }
}
