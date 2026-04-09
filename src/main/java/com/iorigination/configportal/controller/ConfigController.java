package com.iorigination.configportal.controller;

import com.iorigination.configportal.model.AuditLog;
import com.iorigination.configportal.model.CountryMaster;
import com.iorigination.configportal.model.GlobalConfig;
import com.iorigination.configportal.model.SupportedIntegration;
import com.iorigination.configportal.service.ConfigService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ConfigController {

    private final ConfigService service;

    @PostConstruct
    public void init() {
        service.seedData();
    }

    // ── Countries ──────────────────────────────────────────
    @GetMapping("/countries")
    public List<CountryMaster> getCountries() {
        return service.getAllCountries();
    }

    // ── Integrations ───────────────────────────────────────
    @GetMapping("/integrations")
    public List<SupportedIntegration> getIntegrations(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String market) {
        if (type != null && market != null) return service.getIntegrationsByTypeAndMarket(type, market);
        return List.of();
    }

    // ── Configs ────────────────────────────────────────────
    @GetMapping("/configs")
    public Map getAllConfigs() {
        Map<String, Object> response = new HashMap<>();
        response.put("allConfigs", service.getAllConfigs());
        return response;
    }

    @GetMapping("/configs/{market}")
    public List<GlobalConfig> getConfigsByMarket(@PathVariable String market) {
        return service.getConfigsByMarket(market);
    }

    @GetMapping("/configs/{market}/{product}")
    public ResponseEntity<GlobalConfig> getConfig(
            @PathVariable String market, @PathVariable String product) {
        return service.getConfig(market, product)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Save as Draft ──────────────────────────────────────
    @PostMapping("/configs/{market}/{product}/draft")
    public ResponseEntity<AuditLog> saveAsDraft(
            @PathVariable String market,
            @PathVariable String product,
            @RequestBody Map<String, Object> body) {

        GlobalConfig config = parseConfig(body);
        String comment = (String) body.getOrDefault("comment", "");
        AuditLog draft = service.saveAsDraft(market, product, config, comment);
        return ResponseEntity.ok(draft);
    }

    // ── Pending Approvals ──────────────────────────────────
    @GetMapping("/approvals/pending")
    public Map getPendingApprovals() {
        Map<String, Object> response = new HashMap<>();
        response.put("allConfigs", service.getPendingApprovals());
        return response;
    }

    // ── Approve ────────────────────────────────────────────
    @PostMapping("/approvals/{auditId}/approve")
    public ResponseEntity<?> approve(
            @PathVariable String auditId,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            String comment = body != null ? body.getOrDefault("comment", "") : "";
            GlobalConfig activated = service.approveAndActivate(auditId, comment);
            return ResponseEntity.ok(activated);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Reject ─────────────────────────────────────────────
    @PostMapping("/approvals/{auditId}/reject")
    public ResponseEntity<?> reject(
            @PathVariable String auditId,
            @RequestBody Map<String, String> body) {
        try {
            AuditLog rejected = service.rejectDraft(auditId, body.getOrDefault("reason", ""));
            return ResponseEntity.ok(rejected);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Audit History ──────────────────────────────────────
    @GetMapping("/configs/{market}/{product}/history")
    public List<AuditLog> getHistory(
            @PathVariable String market, @PathVariable String product) {
        return service.getAuditHistory(market, product);
    }

    // ── Rollback ───────────────────────────────────────────
    @PostMapping("/configs/{market}/{product}/rollback/{version}")
    public ResponseEntity<?> rollback(
            @PathVariable String market, @PathVariable String product,
            @PathVariable int version,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            String comment = body != null ? body.getOrDefault("comment", "") : "";
            AuditLog draft = service.rollbackToVersion(market, product, version, comment);
            return ResponseEntity.ok(draft);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Simple mapper — in prod use Jackson ObjectMapper / MapStruct
    @SuppressWarnings("unchecked")
    private GlobalConfig parseConfig(Map<String, Object> body) {
        GlobalConfig config = new GlobalConfig();

        if (body.containsKey("fcc")) {
            Map<String, Object> fcc = (Map<String, Object>) body.get("fcc");
            config.setFcc(GlobalConfig.FccConfig.builder()
                    .amlRiskScoreThreshold(toInt(fcc.get("amlRiskScoreThreshold"), 65))
                    .fraudScoreThreshold(toInt(fcc.get("fraudScoreThreshold"), 70))
                    .manualReviewThreshold(toInt(fcc.get("manualReviewThreshold"), 50))
                    .sanctionsListIds((List<String>) fcc.getOrDefault("sanctionsListIds", List.of()))
                    .amlProvider((String) fcc.getOrDefault("amlProvider", "SYMPHONY_AI"))
                    .fraudProvider((String) fcc.getOrDefault("fraudProvider", "THREATMETRIX"))
                    .build());
        }
        if (body.containsKey("credit")) {
            Map<String, Object> credit = (Map<String, Object>) body.get("credit");
            config.setCredit(GlobalConfig.CreditConfig.builder()
                    .minCreditScore(toInt(credit.get("minCreditScore"), 580))
                    .dtiMaxRatio(toDouble(credit.get("dtiMaxRatio"), 0.40))
                    .bureauProvider((String) credit.getOrDefault("bureauProvider", ""))
                    .scorecardId((String) credit.getOrDefault("scorecardId", ""))
                    .decisionEngineVersion((String) credit.getOrDefault("decisionEngineVersion", "DSS_V2"))
                    .build());
        }
        if (body.containsKey("workflow")) {
            Map<String, Object> wf = (Map<String, Object>) body.get("workflow");
            config.setWorkflow(GlobalConfig.WorkflowFlags.builder()
                    .idvEnabled(toBool(wf.get("idvEnabled"), true))
                    .amlEnabled(toBool(wf.get("amlEnabled"), true))
                    .fraudCheckEnabled(toBool(wf.get("fraudCheckEnabled"), true))
                    .pepScreeningEnabled(toBool(wf.get("pepScreeningEnabled"), false))
                    .eSigningEnabled(toBool(wf.get("eSigningEnabled"), true))
                    .brokerCallbackEnabled(toBool(wf.get("brokerCallbackEnabled"), true))
                    .outboundSalesEnabled(toBool(wf.get("outboundSalesEnabled"), false))
                    .referralPortalEnabled(toBool(wf.get("referralPortalEnabled"), true))
                    .idvProvider((String) wf.getOrDefault("idvProvider", ""))
                    .signicatProfile((String) wf.getOrDefault("signicatProfile", ""))
                    .signingProvider((String) wf.getOrDefault("signingProvider", "SIGNICAT_SIGN"))
                    .documentSet((String) wf.getOrDefault("documentSet", ""))
                    .workflowSteps((List<String>) wf.getOrDefault("workflowSteps", List.of()))
                    .build());
        }
        if (body.containsKey("product")) {
            Map<String, Object> p = (Map<String, Object>) body.get("product");
            config.setProduct(GlobalConfig.ProductLimits.builder()
                    .minAmount(toDouble(p.get("minAmount"), 1000))
                    .maxAmount(toDouble(p.get("maxAmount"), 50000))
                    .minTermMonths(toInt(p.get("minTermMonths"), 12))
                    .maxTermMonths(toInt(p.get("maxTermMonths"), 84))
                    .interestRateMin(toDouble(p.get("interestRateMin"), 3.9))
                    .interestRateMax(toDouble(p.get("interestRateMax"), 14.9))
                    .interestRateModel((String) p.getOrDefault("interestRateModel", "FIXED"))
                    .build());
        }
        config.setProductName((String) body.getOrDefault("productName", ""));
        return config;
    }

    private int toInt(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Number) return ((Number) v).intValue();
        try {
            return Integer.parseInt(v.toString());
        } catch (Exception e) {
            return def;
        }
    }

    private double toDouble(Object v, double def) {
        if (v == null) return def;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try {
            return Double.parseDouble(v.toString());
        } catch (Exception e) {
            return def;
        }
    }

    private boolean toBool(Object v, boolean def) {
        if (v == null) return def;
        if (v instanceof Boolean) return (Boolean) v;
        return Boolean.parseBoolean(v.toString());
    }
}
