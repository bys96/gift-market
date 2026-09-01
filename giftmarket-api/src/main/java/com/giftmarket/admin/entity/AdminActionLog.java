package com.giftmarket.admin.entity;

import com.giftmarket.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "admin_action_logs",
        indexes = {
                @Index(
                        name = "idx_admin_action_logs_admin_created_at",
                        columnList = "admin_user_id, created_at"
                ),
                @Index(
                        name = "idx_admin_action_logs_target_created_at",
                        columnList = "target_type, target_id, created_at"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminActionLog extends BaseEntity {

    private static final int MAX_REASON_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_user_id", nullable = false)
    private Long adminUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 40)
    private AdminActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private AdminActionTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(nullable = false, length = MAX_REASON_LENGTH)
    private String reason;

    private AdminActionLog(
            Long adminUserId,
            AdminActionType actionType,
            AdminActionTargetType targetType,
            Long targetId,
            String reason
    ) {
        if (adminUserId == null) {
            throw new IllegalArgumentException("관리자 사용자 ID가 필요합니다.");
        }
        if (actionType == null) {
            throw new IllegalArgumentException("관리자 액션 유형이 필요합니다.");
        }
        if (targetType == null) {
            throw new IllegalArgumentException("관리자 액션 대상 유형이 필요합니다.");
        }
        if (targetId == null) {
            throw new IllegalArgumentException("관리자 액션 대상 ID가 필요합니다.");
        }

        String normalizedReason = normalizeReason(reason);
        this.adminUserId = adminUserId;
        this.actionType = actionType;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = normalizedReason;
    }

    public static AdminActionLog create(
            Long adminUserId,
            AdminActionType actionType,
            AdminActionTargetType targetType,
            Long targetId,
            String reason
    ) {
        return new AdminActionLog(
                adminUserId,
                actionType,
                targetType,
                targetId,
                reason
        );
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("관리자 액션 사유가 필요합니다.");
        }

        String normalizedReason = reason.trim();
        if (normalizedReason.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("관리자 액션 사유는 500자 이하여야 합니다.");
        }
        return normalizedReason;
    }
}
