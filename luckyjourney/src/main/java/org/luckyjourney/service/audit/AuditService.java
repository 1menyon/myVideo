package org.luckyjourney.service.audit;

import java.util.function.Supplier;

/**
 * @description: 用于处理审核
 * @Author: menyon
 * @CreateTime: 2023-10-29 14:39
 */
// <T,R> : T是形参类型，R是返回值类型
public interface AuditService<T,R> {

    /**
     *  审核规范
     * @param task
     * @return
     */
    R audit(T task);
}
