package org.luckyjourney.entity.user;

import lombok.Data;

/**
 * @description: 用户订阅表
 * @Author: menyon
 * @CreateTime: 2023-11-01 14:59
 */
@Data
public class UserSubscribe {
    private Long id;
    private Long typeId;
    private Long userId;
}
