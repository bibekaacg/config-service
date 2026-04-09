package com.iorigination.configportal.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * ConfigAuditLog — every save/approve/rollback action is recorded here.
 * Equivalent of DynamoDB ConfigAuditLog table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "config_audit_log")
public class AuditLog {

    @Id
    private String id;

    @Indexed
    private String configId;      // matches GlobalConfig._id

    private String marketCode;
    private String productId;
    private int    version;
    private String status;        // DRAFT | ACTIVE | SUPERSEDED | REJECTED

    private String changeType;    // CREATE | UPDATE | ACTIVATE | ROLLBACK | REJECT
    private String changedBy;     // user email / sub
    private String approvedBy;
    private String rejectedBy;
    private String rejectionComment;

    private Instant changedAt;
    private Instant approvedAt;

    private Object previousValue; // full snapshot
    private Object newValue;      // full snapshot
    private List<DiffEntry> diffSummary;

    private boolean requiresComplianceApproval;
    private String  approvalLevel;  // APPROVER | CREDIT_APPROVER | COMPLIANCE_APPROVER

    private String ipAddress;
    private String changeComment;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DiffEntry {
        private String field;
        private Object from;
        private Object to;
    }
}
