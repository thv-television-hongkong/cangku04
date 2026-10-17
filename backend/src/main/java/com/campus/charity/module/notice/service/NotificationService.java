package com.campus.charity.module.notice.service;

import com.campus.charity.module.notice.entity.Notification;

import java.util.List;

/**
 * 站内通知服务（extends 通知相关方 / 审核结果通知 / 预警通知）。
 */
public interface NotificationService {

    /** 发送给指定用户 */
    void send(Long userId, String title, String content, String noticeType, String bizId);

    /** 发送给指定角色的全部用户 */
    void sendToRole(String role, String title, String content, String noticeType, String bizId);

    List<Notification> listCurrentUser();

    long unreadCount();

    void markRead(List<Long> noticeIds);
}
