package com.campus.charity.module.notice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.charity.common.util.SecurityUtil;
import com.campus.charity.module.notice.entity.Notification;
import com.campus.charity.module.notice.mapper.NotificationMapper;
import com.campus.charity.module.notice.service.NotificationService;
import com.campus.charity.module.user.entity.SysUser;
import com.campus.charity.module.user.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 站内通知服务实现。
 */
@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final SysUserMapper userMapper;

    public NotificationServiceImpl(NotificationMapper notificationMapper, SysUserMapper userMapper) {
        this.notificationMapper = notificationMapper;
        this.userMapper = userMapper;
    }

    @Override
    public void send(Long userId, String title, String content, String noticeType, String bizId) {
        if (userId == null) {
            return;
        }
        try {
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setTitle(title);
            notification.setContent(content);
            notification.setNoticeType(noticeType);
            notification.setBizId(bizId);
            notification.setReadStatus(0);
            notification.setCreateTime(LocalDateTime.now());
            notificationMapper.insert(notification);
        } catch (Exception e) {
            // 通知失败不应阻断主业务
            log.warn("发送站内通知失败 userId={} title={} error={}", userId, title, e.getMessage());
        }
    }

    @Override
    public void sendToRole(String role, String title, String content, String noticeType, String bizId) {
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>().eq(SysUser::getRole, role));
        for (SysUser user : users) {
            send(user.getUserId(), title, content, noticeType, bizId);
        }
    }

    @Override
    public List<Notification> listCurrentUser() {
        return notificationMapper.selectList(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, SecurityUtil.getUserId())
                .orderByDesc(Notification::getNoticeId)
                .last("LIMIT 100"));
    }

    @Override
    public long unreadCount() {
        return notificationMapper.countUnread(SecurityUtil.getUserId());
    }

    @Override
    public void markRead(List<Long> noticeIds) {
        if (noticeIds == null || noticeIds.isEmpty()) {
            return;
        }
        for (Long id : noticeIds) {
            Notification notification = notificationMapper.selectById(id);
            if (notification != null && notification.getUserId().equals(SecurityUtil.getUserId())) {
                notification.setReadStatus(1);
                notificationMapper.updateById(notification);
            }
        }
    }
}
