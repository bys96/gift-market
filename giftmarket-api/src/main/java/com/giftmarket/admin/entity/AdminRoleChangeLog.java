package com.giftmarket.admin.entity;

import com.giftmarket.global.entity.BaseEntity;
import com.giftmarket.user.entity.UserRole;
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
        name = "admin_role_change_logs",
        indexes = {
                @Index(
                        name = "idx_admin_role_logs_operator_created_at",
                        columnList = "operator_user_id, created_at"
                ),
                @Index(
                        name = "idx_admin_role_logs_target_created_at",
                        columnList = "target_user_id, created_at"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminRoleChangeLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operator_user_id", nullable = false)
    private Long operatorUserId;

    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_role", nullable = false, length = 20)
    private UserRole previousRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "changed_role", nullable = false, length = 20)
    private UserRole changedRole;

    private AdminRoleChangeLog(
            Long operatorUserId,
            Long targetUserId,
            UserRole previousRole,
            UserRole changedRole
    ) {
        this.operatorUserId = operatorUserId;
        this.targetUserId = targetUserId;
        this.previousRole = previousRole;
        this.changedRole = changedRole;
    }

    public static AdminRoleChangeLog create(
            Long operatorUserId,
            Long targetUserId,
            UserRole previousRole,
            UserRole changedRole
    ) {
        if (operatorUserId == null || targetUserId == null
                || previousRole == null || changedRole == null
                || previousRole == changedRole) {
            throw new IllegalArgumentException("관리자 권한 변경 이력 정보가 올바르지 않습니다.");
        }
        return new AdminRoleChangeLog(
                operatorUserId,
                targetUserId,
                previousRole,
                changedRole
        );
    }
}
