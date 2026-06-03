package com.iot.api.controller.admin;

import com.iot.api.security.SecurityUtil;
import com.iot.common.model.PageResult;
import com.iot.common.model.Result;
import com.iot.core.entity.User;
import com.iot.core.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 运营后台 — 用户管理控制器
 * <p>
 * 提供管理端用户列表查询和启用/禁用功能。
 * 所有接口需要 ROLE_ADMIN 权限（由 SecurityConfig 控制）。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/user")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    /**
     * 分页查询用户列表
     * <p>
     * 支持按手机号模糊搜索和状态筛选。
     * 返回的用户数据不包含密码字段。
     * </p>
     *
     * @param page   页码，默认1
     * @param size   每页数量，默认20
     * @param phone  手机号（可选，模糊搜索）
     * @param status 状态（可选）：0-禁用，1-正常
     * @return 分页用户列表
     */
    @GetMapping("/list")
    public Result<PageResult<User>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) Integer status) {

        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-用户列表] 操作人: {}, phone: {}, status: {}, page: {}",
                operatorId, phone, status, page);

        java.util.List<User> users = userService.adminListUsers(phone, status, page, size);
        long total = userService.adminCountUsers(phone, status);

        return Result.success(PageResult.of(users, total, page, size));
    }

    /**
     * 启用/禁用用户
     * <p>
     * 禁用后该用户将无法登录，登录接口返回 403。
     * </p>
     *
     * @param id     用户ID
     * @param status 目标状态：0-禁用，1-正常
     * @return 操作结果
     */
    @PutMapping("/{id}/status")
    public Result<String> updateUserStatus(@PathVariable Long id,
                                           @RequestParam Integer status) {
        Long operatorId = SecurityUtil.getCurrentUserId();
        log.info("[管理端-用户状态] 操作人: {}, userId: {}, status: {}", operatorId, id, status);

        if (status != 0 && status != 1) {
            return Result.error(400, "状态值必须为0（禁用）或1（启用）");
        }
        userService.adminUpdateUserStatus(id, status);
        return Result.success(status == 1 ? "已启用" : "已禁用");
    }
}
