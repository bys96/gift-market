package com.giftmarket.admin.service;

import com.giftmarket.admin.dto.response.AdministratorResponse;
import com.giftmarket.admin.entity.AdminRoleChangeLog;
import com.giftmarket.admin.exception.AdminUserException;
import com.giftmarket.admin.exception.AdminUserOperationException;
import com.giftmarket.admin.repository.AdminRoleChangeLogRepository;
import com.giftmarket.auth.exception.AuthenticationException;
import com.giftmarket.seller.repository.SellerRepository;
import com.giftmarket.user.entity.User;
import com.giftmarket.user.entity.UserRole;
import com.giftmarket.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdministratorService {

    private static final List<UserRole> ADMIN_ROLES = List.of(
            UserRole.ADMIN,
            UserRole.SUPER_ADMIN
    );

    private final UserRepository userRepository;
    private final SellerRepository sellerRepository;
    private final AdminRoleChangeLogRepository roleChangeLogRepository;

    @Transactional(readOnly = true)
    public List<AdministratorResponse> getAdministrators(Long operatorUserId) {
        requireSuperAdmin(operatorUserId);

        List<User> administrators = userRepository
                .findAllByRoleInOrderByCreatedAtAscIdAsc(ADMIN_ROLES);
        List<Long> userIds = administrators.stream().map(User::getId).toList();
        Set<Long> sellerUserIds = userIds.isEmpty()
                ? Set.of()
                : Set.copyOf(sellerRepository.findUserIdsByUserIdIn(userIds));
        Map<Long, LocalDateTime> assignedAtByUserId = latestAssignedAt(userIds);

        return administrators.stream()
                .map(user -> AdministratorResponse.from(
                        user,
                        sellerUserIds.contains(user.getId()),
                        assignedAtByUserId.get(user.getId())
                ))
                .toList();
    }

    @Transactional
    public AdministratorResponse grantAdministrator(Long operatorUserId, Long targetUserId) {
        requireSuperAdmin(operatorUserId);
        User target = getTargetForUpdate(targetUserId);

        if (target.getRole().isAdmin()) {
            throw new AdminUserOperationException("이미 관리자 권한을 가진 회원입니다.");
        }

        UserRole previousRole = target.getRole();
        if (previousRole != UserRole.USER && previousRole != UserRole.SELLER) {
            throw new AdminUserOperationException("관리자로 지정할 수 없는 회원입니다.");
        }

        target.changeRole(UserRole.ADMIN);
        AdminRoleChangeLog log = roleChangeLogRepository.save(AdminRoleChangeLog.create(
                operatorUserId,
                targetUserId,
                previousRole,
                UserRole.ADMIN
        ));

        return AdministratorResponse.from(
                target,
                sellerRepository.existsByUser(target),
                log.getCreatedAt()
        );
    }

    @Transactional
    public AdministratorResponse revokeAdministrator(Long operatorUserId, Long targetUserId) {
        requireSuperAdmin(operatorUserId);
        User target = getTargetForUpdate(targetUserId);

        if (target.getRole() == UserRole.SUPER_ADMIN) {
            throw new AdminUserOperationException("SUPER_ADMIN 권한은 API에서 변경할 수 없습니다.");
        }
        if (target.getRole() != UserRole.ADMIN) {
            throw new AdminUserOperationException("관리자 권한을 가진 회원이 아닙니다.");
        }

        boolean seller = sellerRepository.existsByUser(target);
        UserRole restoredRole = seller ? UserRole.SELLER : UserRole.USER;
        target.changeRole(restoredRole);
        roleChangeLogRepository.save(AdminRoleChangeLog.create(
                operatorUserId,
                targetUserId,
                UserRole.ADMIN,
                restoredRole
        ));

        return AdministratorResponse.from(target, seller, null);
    }

    private Map<Long, LocalDateTime> latestAssignedAt(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, LocalDateTime> result = new HashMap<>();
        roleChangeLogRepository.findByTargetUserIdInOrderByCreatedAtDescIdDesc(userIds)
                .stream()
                .filter(log -> log.getChangedRole() == UserRole.ADMIN)
                .forEach(log -> result.putIfAbsent(log.getTargetUserId(), log.getCreatedAt()));
        return result;
    }

    private User getTargetForUpdate(Long targetUserId) {
        return userRepository.findByIdForUpdate(targetUserId)
                .orElseThrow(() -> new AdminUserException("회원을 찾을 수 없습니다."));
    }

    private User requireSuperAdmin(Long operatorUserId) {
        if (operatorUserId == null) {
            throw new AuthenticationException("인증이 필요합니다.");
        }
        User operator = userRepository.findById(operatorUserId)
                .orElseThrow(() -> new AuthenticationException("사용자를 찾을 수 없습니다."));
        if (!operator.getRole().isSuperAdmin()) {
            throw new AuthenticationException("SUPER_ADMIN 권한이 필요합니다.");
        }
        return operator;
    }
}
