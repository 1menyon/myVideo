package org.luckyjourney.service.audit;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import org.luckyjourney.config.LocalCache;
import org.luckyjourney.config.QiNiuConfig;
import org.luckyjourney.constant.AuditMsgMap;
import org.luckyjourney.constant.AuditStatus;
import org.luckyjourney.entity.Setting;
import org.luckyjourney.entity.json.*;
import org.luckyjourney.entity.response.AuditResponse;
import org.luckyjourney.service.SettingService;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.List;
import java.util.UUID;

/**
 * @description:  统一封装审核逻辑，并留给子类进行编排或者调用普通逻辑
 * @Author: menyon
 * @CreateTime: 2023-11-03 12:05
 */
@Service
public abstract class AbstractAuditService<T,R> implements AuditService<T,R> {

    @Autowired
    protected QiNiuConfig qiNiuConfig;

    @Autowired
    protected SettingService settingService;


    protected ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    static final String contentType = "application/json";

    /**
     * 审核
     * @param scoreJsonList
     * @param bodyJson
     * @return
     */
    // 形参一：后台设置的审核区间    形参二：审核结果及得分
    protected AuditResponse audit(List<ScoreJson> scoreJsonList, BodyJson bodyJson) {
        AuditResponse audit = new AuditResponse();
        audit.setAuditStatus(AuditStatus.PROCESS);   // 初始化为审核中
        ScoreJson scoreJson = scoreJsonList.get(2);  // 取出自动审核的置信度区间
        // 遍历的是人工,失败,通过的审核规则,我当前没有办法知道是什么状态
//        for (ScoreJson scoreJson : scoreJsonList) {
            audit = audit(scoreJson, bodyJson);
            // 如果为true,说明视频违规，提前返回
            if (audit.getFlag()){
                // 审核违规
                audit.setAuditStatus(AuditStatus.PASS);
                return audit;
            }else if(audit.getAuditStatus() == AuditStatus.MANUAL){
                // 待人工审核
                return audit;
            }else{
                final ScenesJson scenes = bodyJson.getResult().getResult().getScenes();
                // 如果七牛云给出的suggestion是block，则认定违规
                if (!endCheck(scenes)){
                    audit.setAuditStatus(AuditStatus.PASS);
                    audit.setMsg("内容不合法");
                }
                // 如果七牛云给出的suggestion是review，则也认定违规
                if (!endCheck2(scenes)){
                    audit.setAuditStatus(AuditStatus.PASS);
                    audit.setMsg("内容无法判断是否违规");
                }
                // 审核通过
                audit.setAuditStatus(AuditStatus.SUCCESS);
                return audit;
            }
    }


    /**
     * 返回对应规则的信息
     * @param types
     * @param min
     * @return
     */
    private AuditResponse getInfo(List<CutsJson> types, Double min, String key) {
        AuditResponse auditResponse = new AuditResponse();
        auditResponse.setFlag(false);
        String info = null;
        // 获取信息
        for (CutsJson type : types) {
            for (DetailsJson detail : type.getDetails()) {
                if (detail.getScore() > min) {
                    // 如果违规,则填充额外信息（如果分数在区间中，并且这个标签显示的内容不是“normal”，就表示违规）
                    if (!detail.getLabel().equals(key)) {
                        info = AuditMsgMap.getInfo(detail.getLabel());
                        auditResponse.setMsg(info);
                        auditResponse.setOffset(type.getOffset());
                        auditResponse.setFlag(true);       // 如果置信度很高，并且label不是normal，则违规
                    }
                }

            }
        }
        if (auditResponse.getFlag() && ObjectUtils.isEmpty(auditResponse.getMsg())){
            auditResponse.setMsg("该视频违法幸运日平台规则");
        }

        return auditResponse;
    }


    /**
     * 当前审核规则如果能匹配上也就是进入了if判断中,则需要获取违规信息
     * 如果走到末尾则说明没有匹配上
     * @param scoreJson
     * @param bodyJson
     * @return
     */
    private AuditResponse audit(ScoreJson scoreJson, BodyJson bodyJson) {

        AuditResponse auditResponse = new AuditResponse();
        auditResponse.setFlag(true);
        auditResponse.setAuditStatus(scoreJson.getAuditStatus());

        final Double minPolitician = scoreJson.getMinPolitician();
        final Double maxPolitician = scoreJson.getMaxPolitician();

        final Double minPulp = scoreJson.getMinPulp();
        final Double maxPulp = scoreJson.getMaxPulp();

        final Double minTerror = scoreJson.getMinTerror();
        final Double maxTerror = scoreJson.getMaxTerror();

        // 所有都要比较,如果返回的有问题则直接返回
        if (!ObjectUtils.isEmpty(bodyJson.getPolitician())) {
            // 检查视频的Politician得分 是否命中当前审核策略（自动、人工、PASS失败）的区间
            int PoliticianValue = bodyJson.checkViolation(bodyJson.getPolitician(),minPolitician,maxPolitician);
            if (PoliticianValue != 3) {
                // 图片敏感人物识别的label返回的是敏感人物的姓名；
                // group返回的是该敏感人物所属的分组，类似于Pulp、Terror分类的Label
                final AuditResponse response = getInfo(bodyJson.getPolitician(), minPolitician, "normal");
                auditResponse.setMsg(response.getMsg());
                // 只要有一个截帧的置信度大于自动审核的min，并且label不等于"normal"，认定违规，提前返回
                if (response.getFlag()) {
                    auditResponse.setOffset(response.getOffset());
                    return auditResponse;
                }
                if(PoliticianValue == 2){
                    auditResponse.setAuditStatus(AuditStatus.MANUAL);
                }
            }
        }
        if (!ObjectUtils.isEmpty(bodyJson.getPulp())) {
            int PulpValue = bodyJson.checkViolation(bodyJson.getPulp(),minPulp,maxPulp);
            if (PulpValue != 3) {
                final AuditResponse response = getInfo(bodyJson.getPulp(), minPulp, "normal");
                auditResponse.setMsg(response.getMsg());
                // 只要有一个截帧的置信度大于自动审核的min，并且label不等于"normal"，认定违规，提前返回
                if (response.getFlag()) {
                    auditResponse.setOffset(response.getOffset());
                    return auditResponse;
                }
                if(PulpValue == 2){
                    auditResponse.setAuditStatus(AuditStatus.MANUAL);
                }
            }
        }
        if (!ObjectUtils.isEmpty(bodyJson.getTerror())) {
            int TerrorValue = bodyJson.checkViolation(bodyJson.getTerror(),minTerror,maxTerror);
            if (TerrorValue != 3) {
                final AuditResponse response = getInfo(bodyJson.getTerror(), minTerror, "normal");
                auditResponse.setMsg(response.getMsg());
                // 只要有一个截帧的置信度大于自动审核的min，并且label不等于"normal"，认定违规，提前返回
                if (response.getFlag()) {
                    auditResponse.setOffset(response.getOffset());
                    return auditResponse;
                }
                if(TerrorValue == 2){
                    auditResponse.setAuditStatus(AuditStatus.MANUAL);
                }
            }
        }
        auditResponse.setMsg("正常");
        auditResponse.setFlag(false);
        return auditResponse;
    }

    /**
     * 最后检查,可能没得分,检查suggestion
     * @param scenes
     * @return
     */
    private boolean endCheck(ScenesJson scenes){
        final TypeJson terror = scenes.getTerror();
        final TypeJson politician = scenes.getPolitician();
        final TypeJson pulp = scenes.getPulp();
        if (terror.getSuggestion().equals("block") || politician.getSuggestion().equals("block") || pulp.getSuggestion().equals("block")) {
            return false;
        }
        return true;
    }

    private boolean endCheck2(ScenesJson scenes){
        final TypeJson terror = scenes.getTerror();
        final TypeJson politician = scenes.getPolitician();
        final TypeJson pulp = scenes.getPulp();
        if (terror.getSuggestion().equals("review") || politician.getSuggestion().equals("review") || pulp.getSuggestion().equals("review")) {
            return false;
        }
        return true;
    }
    /**
     * 根据系统配置表查询是否需要审核
     * @return
     */
    protected Boolean isNeedAudit(){
        final Setting setting = settingService.list(null).get(0);
        // 如果系统配置不会频繁变化，可以考虑将配置结果缓存，以减少数据库访问次数，提高性能。
        return setting.getAuditOpen();
    }


    // url的请求参数中拼接uuid
    protected String appendUUID(String url){

        final Setting setting = settingService.list(null).get(0);

        if (setting.getAuth()) {
            final String uuid = UUID.randomUUID().toString();
            LocalCache.put(uuid,true);
            if (url.contains("?")){
                url = url+"&uuid="+uuid;
            }else {
                url = url+"?uuid="+uuid;
            }
            return url;
        }
        return url;
    }
}
