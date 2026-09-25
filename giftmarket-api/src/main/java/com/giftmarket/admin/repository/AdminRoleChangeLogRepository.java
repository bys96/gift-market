package com.giftmarket.admin.repository;

import com.giftmarket.admin.entity.AdminRoleChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AdminRoleChangeLogRepository extends JpaRepository<AdminRoleChangeLog, Long> {

    List<AdminRoleChangeLog> findByTargetUserIdInOrderByCreatedAtDescIdDesc(
            Collection<Long> targetUserIds
    );
}
