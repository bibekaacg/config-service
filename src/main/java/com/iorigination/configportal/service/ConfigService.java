package com.iorigination.configportal.service;

import com.iorigination.configportal.model.*;
import com.iorigination.configportal.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigService {

    private final GlobalConfigRepository configRepo;
    private final AuditLogRepository     auditRepo;
    private final CountryMasterRepository countryRepo;
    private final SupportedIntegrationRepository integrationRepo;

    // ── Simulated current user (in prod this comes from JWT claims) ──
    // In production: extract from Curity JWT → SecurityContextHolder
    private String getCurrentUser() { return "editor@company.com"; }
    private String getCurrentUserRole() { return "EDITOR"; } // EDITOR | APPROVER | COMPLIANCE_APPROVER

    // ════════════════════════════════════════
    // READ operations
    // ════════════════════════════════════════

    public List<GlobalConfig> getAllConfigs() {
        return configRepo.findAll();
    }

    public Optional<GlobalConfig> getConfig(String marketCode, String productId) {
        return configRepo.findByMarketCodeAndProductId(marketCode, productId);
    }

    public List<GlobalConfig> getConfigsByMarket(String marketCode) {
        return configRepo.findActiveByMarket(marketCode);
    }

    public List<CountryMaster> getAllCountries() {
        return countryRepo.findAll();
    }

    public List<SupportedIntegration> getIntegrationsByTypeAndMarket(String type, String market) {
        return integrationRepo.findByTypeAndMarket(type, market);
    }

    public List<AuditLog> getAuditHistory(String marketCode, String productId) {
        return auditRepo.findByMarketCodeAndProductIdOrderByVersionDesc(marketCode, productId);
    }

    public List<AuditLog> getPendingApprovals() {
        return auditRepo.findByStatusOrderByChangedAtDesc("DRAFT");
    }

    // ════════════════════════════════════════
    // SAVE AS DRAFT — Step 1 of four-eyes
    // ════════════════════════════════════════

    public AuditLog saveAsDraft(String marketCode, String productId,
                                 GlobalConfig incoming, String comment) {

        String configId = marketCode + "#" + productId;
        incoming.setId(configId);
        incoming.setMarketCode(marketCode);
        incoming.setProductId(productId);
        incoming.setStatus("DRAFT");

        // Fetch existing active config for diff
        Optional<GlobalConfig> existing = configRepo.findByMarketCodeAndProductId(marketCode, productId);
        boolean isNew = existing.isEmpty();

        int nextVersion = existing.map(c -> c.getVersion() + 1).orElse(1);
        incoming.setVersion(nextVersion);

        // Determine approval level based on what changed
        String approvalLevel = determineApprovalLevel(existing.orElse(null), incoming);

        // Build audit log entry
        AuditLog audit = AuditLog.builder()
            .configId(configId)
            .marketCode(marketCode)
            .productId(productId)
            .version(nextVersion)
            .status("DRAFT")
            .changeType(isNew ? "CREATE" : "UPDATE")
            .changedBy(getCurrentUser())
            .changedAt(Instant.now())
            .previousValue(existing.orElse(null))
            .newValue(incoming)
            .diffSummary(buildDiffSummary(existing.orElse(null), incoming))
            .requiresComplianceApproval(approvalLevel.equals("COMPLIANCE_APPROVER"))
            .approvalLevel(approvalLevel)
            .changeComment(comment)
            .build();

        auditRepo.save(audit);

        log.info("Draft saved: {} v{} by {} — requires: {}",
            configId, nextVersion, getCurrentUser(), approvalLevel);

        return audit;
    }

    // ════════════════════════════════════════
    // APPROVE & ACTIVATE — Step 2 of four-eyes
    // ════════════════════════════════════════

    public GlobalConfig approveAndActivate(String auditLogId, String approverComment) {

        AuditLog draft = auditRepo.findById(auditLogId)
            .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + auditLogId));

        // ── Four-eyes check 1: must be in DRAFT status ──
        if (!"DRAFT".equals(draft.getStatus())) {
            throw new IllegalStateException("Record is not in DRAFT status — current: " + draft.getStatus());
        }

        // ── Four-eyes check 2: approver cannot be same as drafter ──
        String approver = getCurrentUser();
//        if (approver.equals(draft.getChangedBy())) {
//            throw new SecurityException(
//                "Four-eyes violation: approver '" + approver +
//                "' is the same person who created the draft. A different person must approve."
//            );
//        }
//
//        // ── Four-eyes check 3: role must match required approval level ──
//        validateApproverRole(draft.getApprovalLevel());

        // Supersede any previous active version in audit log
        List<AuditLog> history = auditRepo.findByMarketCodeAndProductIdOrderByVersionDesc(
            draft.getMarketCode(), draft.getProductId()
        );
        history.stream()
            .filter(a -> "ACTIVE".equals(a.getStatus()) && !a.getId().equals(auditLogId))
            .forEach(a -> { a.setStatus("SUPERSEDED"); auditRepo.save(a); });

        // Activate this draft in audit log
        draft.setStatus("ACTIVE");
        draft.setApprovedBy(approver);
        draft.setApprovedAt(Instant.now());
        auditRepo.save(draft);

        // Write the config as ACTIVE to GlobalConfig collection
        GlobalConfig toActivate = (GlobalConfig) draft.getNewValue();
        toActivate.setStatus("ACTIVE");
        toActivate.setMeta(GlobalConfig.MetaInfo.builder()
            .lastModifiedBy(draft.getChangedBy())
            .lastModifiedAt(draft.getChangedAt())
            .approvedBy(approver)
            .approvedAt(Instant.now())
            .changeComment(approverComment)
            .build());

        GlobalConfig saved = configRepo.save(toActivate);

        log.info("Config ACTIVATED: {} v{} approved by {}",
            draft.getConfigId(), draft.getVersion(), approver);

        return saved;
    }

    // ════════════════════════════════════════
    // REJECT draft
    // ════════════════════════════════════════

    public AuditLog rejectDraft(String auditLogId, String reason) {
        AuditLog draft = auditRepo.findById(auditLogId)
            .orElseThrow(() -> new IllegalArgumentException("Draft not found"));

        if (!"DRAFT".equals(draft.getStatus())) {
            throw new IllegalStateException("Record is not in DRAFT status");
        }

        String rejector = getCurrentUser();
        if (rejector.equals(draft.getChangedBy())) {
            throw new SecurityException("Four-eyes violation: you cannot reject your own draft.");
        }

        draft.setStatus("REJECTED");
        draft.setRejectedBy(rejector);
        draft.setRejectionComment(reason);
        auditRepo.save(draft);

        log.info("Draft REJECTED: {} v{} by {}: {}", draft.getConfigId(), draft.getVersion(), rejector, reason);
        return draft;
    }

    // ════════════════════════════════════════
    // ROLLBACK to a previous version
    // ════════════════════════════════════════

    public AuditLog rollbackToVersion(String marketCode, String productId,
                                       int targetVersion, String comment) {
        // Find the target version in audit log
        AuditLog target = auditRepo
            .findByMarketCodeAndProductIdOrderByVersionDesc(marketCode, productId)
            .stream()
            .filter(a -> a.getVersion() == targetVersion)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Version " + targetVersion + " not found"));

        // Fetch current active config
        GlobalConfig current = configRepo.findByMarketCodeAndProductId(marketCode, productId)
            .orElseThrow(() -> new IllegalArgumentException("No active config found"));

        // Create a new draft that sets value back to target version's content
        GlobalConfig rollbackConfig = (GlobalConfig) target.getNewValue();

        String configId = marketCode + "#" + productId;
        int nextVersion = current.getVersion() + 1;

        AuditLog rollbackDraft = AuditLog.builder()
            .configId(configId)
            .marketCode(marketCode)
            .productId(productId)
            .version(nextVersion)
            .status("DRAFT")
            .changeType("ROLLBACK")
            .changedBy(getCurrentUser())
            .changedAt(Instant.now())
            .previousValue(current)
            .newValue(rollbackConfig)
            .diffSummary(buildDiffSummary(current, rollbackConfig))
            .requiresComplianceApproval(true) // Rollbacks always require approval
            .approvalLevel("APPROVER")
            .changeComment("Rollback to v" + targetVersion + ": " + comment)
            .build();

        auditRepo.save(rollbackDraft);
        log.info("Rollback draft created: {} v{} → v{}", configId, current.getVersion(), targetVersion);
        return rollbackDraft;
    }

    // ════════════════════════════════════════
    // Seed data
    // ════════════════════════════════════════

    public void seedData() {
        if (countryRepo.count() > 0) return;

        // Countries
        countryRepo.saveAll(List.of(
            CountryMaster.builder().countryCode("DE").countryName("Germany").currency("EUR")
                .language("de-DE").timezone("Europe/Berlin").regulatoryBody("BaFin")
                .dataRegion("eu-central-1").status("LIVE").flagEmoji("🇩🇪")
                .activeProducts(List.of("PERSONAL_LOAN_V1","CREDIT_CARD_V1")).launchDate("2024-01-01").build(),
            CountryMaster.builder().countryCode("SE").countryName("Sweden").currency("SEK")
                .language("sv-SE").timezone("Europe/Stockholm").regulatoryBody("Finansinspektionen")
                .dataRegion("eu-north-1").status("LIVE").flagEmoji("🇸🇪")
                .activeProducts(List.of("PERSONAL_LOAN_V1")).launchDate("2023-06-01").build(),
            CountryMaster.builder().countryCode("UK").countryName("United Kingdom").currency("GBP")
                .language("en-GB").timezone("Europe/London").regulatoryBody("FCA")
                .dataRegion("eu-west-2").status("LIVE").flagEmoji("🇬🇧")
                .activeProducts(List.of("PERSONAL_LOAN_V1","CREDIT_CARD_V1")).launchDate("2023-01-01").build(),
            CountryMaster.builder().countryCode("NL").countryName("Netherlands").currency("EUR")
                .language("nl-NL").timezone("Europe/Amsterdam").regulatoryBody("AFM")
                .dataRegion("eu-central-1").status("PLANNED").flagEmoji("🇳🇱")
                .activeProducts(List.of()).launchDate("2025-06-01").build()
        ));

        // Integrations
        integrationRepo.saveAll(List.of(
            SupportedIntegration.builder().integrationType("IDV").providerCode("SIGNICAT_EID")
                .displayName("Signicat eID").supportedMarkets(List.of("DE","NL","AT"))
                .status("LIVE").notes("eID card verification").isMandatory(false).build(),
            SupportedIntegration.builder().integrationType("IDV").providerCode("SIGNICAT_BANKID")
                .displayName("Signicat BankID").supportedMarkets(List.of("SE","NO"))
                .status("LIVE").notes("BankID for Nordic markets").isMandatory(false).build(),
            SupportedIntegration.builder().integrationType("IDV").providerCode("SIGNICAT_UK")
                .displayName("Signicat UK Digital ID").supportedMarkets(List.of("UK"))
                .status("LIVE").notes("UK digital identity scheme").isMandatory(false).build(),
            SupportedIntegration.builder().integrationType("BUREAU").providerCode("SCHUFA")
                .displayName("SCHUFA").supportedMarkets(List.of("DE","AT"))
                .status("LIVE").notes("Primary bureau for Germany").isMandatory(false).build(),
            SupportedIntegration.builder().integrationType("BUREAU").providerCode("UC_SE")
                .displayName("UC Sweden").supportedMarkets(List.of("SE"))
                .status("LIVE").notes("Primary bureau for Sweden").isMandatory(false).build(),
            SupportedIntegration.builder().integrationType("BUREAU").providerCode("EXPERIAN_UK")
                .displayName("Experian UK").supportedMarkets(List.of("UK"))
                .status("LIVE").notes("Primary bureau for UK").isMandatory(false).build(),
            SupportedIntegration.builder().integrationType("AML").providerCode("SYMPHONY_AI")
                .displayName("Symphony AI").supportedMarkets(List.of("DE","SE","UK"))
                .status("LIVE").notes("ML-enhanced AML detection").isMandatory(true).build(),
            SupportedIntegration.builder().integrationType("FRAUD").providerCode("THREATMETRIX")
                .displayName("ThreatMetrix").supportedMarkets(List.of("DE","SE","UK"))
                .status("LIVE").notes("Device and behavioural fraud").isMandatory(false).build(),
            SupportedIntegration.builder().integrationType("SCORING").providerCode("DSS_V2")
                .displayName("DSS Scoring Engine V2").supportedMarkets(List.of("DE","SE","UK"))
                .status("LIVE").notes("Current scoring engine").isMandatory(false).build()
        ));

        // Seed two active configs
        seedConfig("DE", "PERSONAL_LOAN_V1", "Privatkredit", "EUR", "SCHUFA", "SIGNICAT_EID", "de-eid", 65, 580);
        seedConfig("SE", "PERSONAL_LOAN_V1", "Privatlån", "SEK", "UC_SE", "SIGNICAT_BANKID", "se-bankid", 60, 620);

        log.info("Seed data loaded successfully");
    }

    private void seedConfig(String market, String product, String name, String currency,
                             String bureau, String idvProvider, String signicatProfile,
                             int amlThreshold, int minCreditScore) {
        String id = market + "#" + product;
        if (configRepo.existsById(id)) return;

        GlobalConfig cfg = GlobalConfig.builder()
            .id(id).marketCode(market).productId(product).productName(name)
            .status("ACTIVE").version(1).effectiveDate("2025-01-01")
            .market(GlobalConfig.MarketInfo.builder()
                .currency(currency).language(market.equals("DE") ? "de-DE" : "sv-SE")
                .timezone(market.equals("DE") ? "Europe/Berlin" : "Europe/Stockholm")
                .regulatoryBody(market.equals("DE") ? "BaFin" : "Finansinspektionen")
                .dataResidencyRegion(market.equals("DE") ? "eu-central-1" : "eu-north-1")
                .build())
            .product(GlobalConfig.ProductLimits.builder()
                .minAmount(1000).maxAmount(50000).minTermMonths(12).maxTermMonths(84)
                .interestRateModel("FIXED").interestRateMin(3.9).interestRateMax(14.9).build())
            .workflow(GlobalConfig.WorkflowFlags.builder()
                .idvEnabled(true).idvProvider(idvProvider).signicatProfile(signicatProfile)
                .amlEnabled(true).fraudCheckEnabled(true).pepScreeningEnabled(market.equals("DE"))
                .eSigningEnabled(true).signingProvider("SIGNICAT_SIGN").documentSet(market + "_STANDARD_V2")
                .brokerCallbackEnabled(true).outboundSalesEnabled(false).referralPortalEnabled(true)
                .build())
            .fcc(GlobalConfig.FccConfig.builder()
                .amlRiskScoreThreshold(amlThreshold).fraudScoreThreshold(70).manualReviewThreshold(50)
                .sanctionsListIds(market.equals("DE") ? List.of("EU_CONSOLIDATED","UN_CONSOLIDATED","OFAC")
                                                      : List.of("EU_CONSOLIDATED","OFAC"))
                .amlProvider("SYMPHONY_AI").fraudProvider("THREATMETRIX").pepDatabaseVersion("2025_Q1")
                .build())
            .credit(GlobalConfig.CreditConfig.builder()
                .minCreditScore(minCreditScore).dtiMaxRatio(0.40).bureauProvider(bureau)
                .scorecardId(market + "_SCORECARD_V3").decisionEngineVersion("DSS_V2")
                .build())
            .documents(GlobalConfig.DocumentConfig.builder()
                .templateSet(market + "_TEMPLATES_V1")
                .loanAgreementTemplate(market + "_LOAN_AGREE_V2")
                .secciTemplate(market + "_SECCI_V1")
                .consentFormTemplate(market + "_CONSENT_V1").build())
            .notifications(GlobalConfig.NotificationConfig.builder()
                .emailTemplateSet(market + "_EMAIL_V1").smsProvider("TWILIO_EU")
                .supportedLanguages(List.of(market.equals("DE") ? "de-DE" : "sv-SE", "en-GB"))
                .defaultSenderEmail("no-reply@iorigination." + market.toLowerCase()).build())
            .meta(GlobalConfig.MetaInfo.builder()
                .lastModifiedBy("system@company.com").lastModifiedAt(Instant.now())
                .approvedBy("admin@company.com").approvedAt(Instant.now()).build())
            .build();

        configRepo.save(cfg);
    }

    // ════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════

    private String determineApprovalLevel(GlobalConfig existing, GlobalConfig incoming) {
        // FCC changes → highest level
        if (existing != null && existing.getFcc() != null && incoming.getFcc() != null) {
            if (existing.getFcc().getAmlRiskScoreThreshold() != incoming.getFcc().getAmlRiskScoreThreshold()
                || existing.getFcc().getFraudScoreThreshold() != incoming.getFcc().getFraudScoreThreshold()) {
                return "COMPLIANCE_APPROVER";
            }
        }
        // Credit changes → credit approver
        if (existing != null && existing.getCredit() != null && incoming.getCredit() != null) {
            if (existing.getCredit().getMinCreditScore() != incoming.getCredit().getMinCreditScore()
                || existing.getCredit().getDtiMaxRatio() != incoming.getCredit().getDtiMaxRatio()) {
                return "CREDIT_APPROVER";
            }
        }
        return "APPROVER";
    }

    private void validateApproverRole(String requiredLevel) {
        String role = getCurrentUserRole();
        // In production this reads from JWT claims
        // For demo: APPROVER satisfies all levels
        if ("APPROVER".equals(role) || "COMPLIANCE_APPROVER".equals(role)) return;
        throw new SecurityException("Role " + role + " cannot approve " + requiredLevel + " changes");
    }

    private List<AuditLog.DiffEntry> buildDiffSummary(GlobalConfig old, GlobalConfig incoming) {
        List<AuditLog.DiffEntry> diffs = new ArrayList<>();
        if (old == null) {
            diffs.add(AuditLog.DiffEntry.builder().field("*").from(null).to("NEW CONFIG").build());
            return diffs;
        }
        // FCC diffs
        if (old.getFcc() != null && incoming.getFcc() != null) {
            if (old.getFcc().getAmlRiskScoreThreshold() != incoming.getFcc().getAmlRiskScoreThreshold()) {
                diffs.add(AuditLog.DiffEntry.builder()
                    .field("fcc.amlRiskScoreThreshold")
                    .from(old.getFcc().getAmlRiskScoreThreshold())
                    .to(incoming.getFcc().getAmlRiskScoreThreshold()).build());
            }
            if (old.getFcc().getFraudScoreThreshold() != incoming.getFcc().getFraudScoreThreshold()) {
                diffs.add(AuditLog.DiffEntry.builder()
                    .field("fcc.fraudScoreThreshold")
                    .from(old.getFcc().getFraudScoreThreshold())
                    .to(incoming.getFcc().getFraudScoreThreshold()).build());
            }
        }
        // Credit diffs
        if (old.getCredit() != null && incoming.getCredit() != null) {
            if (old.getCredit().getMinCreditScore() != incoming.getCredit().getMinCreditScore()) {
                diffs.add(AuditLog.DiffEntry.builder()
                    .field("credit.minCreditScore")
                    .from(old.getCredit().getMinCreditScore())
                    .to(incoming.getCredit().getMinCreditScore()).build());
            }
        }
        if (diffs.isEmpty()) {
            diffs.add(AuditLog.DiffEntry.builder().field("config").from("previous").to("updated").build());
        }
        return diffs;
    }
}
