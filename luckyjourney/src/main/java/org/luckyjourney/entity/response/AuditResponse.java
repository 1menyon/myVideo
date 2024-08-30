package org.luckyjourney.entity.response;

import lombok.Data;
import lombok.ToString;
import org.luckyjourney.entity.task.VideoTask;

/**
 * @description: 封装视频审核返回结果
 * @Author: menyon
 * @CreateTime: 2023-10-29 14:40
 */
@Data
@ToString
public class AuditResponse {
    // 0：通过   1: 审核中  2：PASS失败  3：待人工复审
    private Integer auditStatus;
    // true:违规 false:正常
    private Boolean flag;
    // 信息
    private String msg;

    private Long offset;

    public AuditResponse(Integer auditStatus,String msg){
        this.auditStatus = auditStatus;
        this.msg = msg;
    }
    public AuditResponse(){}
}
