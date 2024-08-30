package org.luckyjourney.entity.vo;

import lombok.Data;
import org.luckyjourney.entity.user.Follow;

/**
 * @description:
 * @Author: menyon
 * @CreateTime: 2023-10-25 18:03
 */
@Data
public class FollowVO extends Follow {

    private String nickName;
}
