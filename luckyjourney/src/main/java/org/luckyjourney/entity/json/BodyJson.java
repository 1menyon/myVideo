package org.luckyjourney.entity.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.ToString;
import org.springframework.util.ObjectUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @description:
 * @Author: menyon
 * @CreateTime: 2023-10-29 02:13
 */
@Data
//@ToString
public class BodyJson implements Serializable {
    String id;
    String status;
    ResultJson result;


    public boolean compare(Double min, Double max, Double value) {
        return value >= min && value <= max;
    }
    // 每一帧 进行检查
    public Integer checkViolation(List<CutsJson> types,Double min, Double max){
        Integer flag = 3;
        for (CutsJson cutsJson : types) {
            // 审核politician类别 直接给suggestion，没有details
            if (ObjectUtils.isEmpty(cutsJson.getDetails())){
                continue;
            }
            for (DetailsJson detail : cutsJson.getDetails()) {
                // 视频的得分 如果不在当前审核策略（自动、人工、PASS失败）的区间，则返回false
                if (compare(min,max,detail.getScore())){
                    flag = 1;
                }else{
                    flag = 2;
                    break;
                }
            }
            if(flag==2) break;
        }
        return flag;
    }
    /**
     *   返回的审核结果（类别包括：Terror、Politician、Pulp） 存在json格式中 需要取出来
     *    每一种类别都 需要获取视频的帧信息
     * */

    // 视频和图片分开处理
    // 视频审核响应结果：每一种审核类型（terror、politician、pulp）都包含了suggestion、cuts(cuts内部封装了details)。
    // 图片审核响应结果：每一种审核类型（terror、politician、pulp）都包含了suggestion、details(也可能没有details)。
    public List<CutsJson> getTerror(){
        final TypeJson terror = result.getResult().getScenes().getTerror();
        // 如果是视频审核
        if (!ObjectUtils.isEmpty(terror.getCuts())){
            return terror.getCuts();
        }
        // 如果是图片审核
        final CutsJson cutsJson = new CutsJson();
        cutsJson.setDetails(terror.getDetails());
        cutsJson.setSuggestion(terror.getSuggestion());

        return Collections.singletonList(cutsJson);
    }

    public List<CutsJson> getPolitician(){
        final TypeJson politician = result.getResult().getScenes().getPolitician();
        // 如果是视频审核
        if (!ObjectUtils.isEmpty(politician.getCuts())){
            return politician.getCuts();
        }
        // 如果是图片审核
        final CutsJson cutsJson = new CutsJson();
        cutsJson.setDetails(politician.getDetails());
        cutsJson.setSuggestion(politician.getSuggestion());

        return Collections.singletonList(cutsJson);
    }

    public List<CutsJson> getPulp(){
        final TypeJson pulp = result.getResult().getScenes().getPulp();
        // 如果是视频审核
        if (!ObjectUtils.isEmpty(pulp.getCuts())){
            return pulp.cuts;
        }
        // 如果是图片审核
        final CutsJson cutsJson = new CutsJson();
        cutsJson.setDetails(pulp.getDetails());
        cutsJson.setSuggestion(pulp.getSuggestion());

        return Collections.singletonList(cutsJson);
    }





}
