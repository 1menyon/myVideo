package org.luckyjourney.service.audit;

import com.qiniu.http.Client;
import com.qiniu.http.Response;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.util.StringMap;
import org.luckyjourney.config.QiNiuConfig;
import org.luckyjourney.constant.AuditStatus;
import org.luckyjourney.entity.Setting;
import org.luckyjourney.entity.json.*;
import org.luckyjourney.entity.response.AuditResponse;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @description: 图片审核
 * @Author: menyon
 * @CreateTime: 2023-11-04 15:48
 */
@Service
public class ImageAuditService extends AbstractAuditService<String, AuditResponse> {

    // 七牛云图片审核 API 的 URL
    static String imageUlr = "http://ai.qiniuapi.com/v3/image/censor";

    // 请求体模板，包含图片的 URL 和审核场景
    static String imageBody = "{\n" +
            "    \"data\": {\n" +
            "        \"uri\": \"${url}\"\n" +
            "    },\n" +
            "    \"params\": {\n" +
            "        \"scenes\": [\n" +
            "            \"pulp\",\n" +
            "            \"terror\",\n" +
            "            \"politician\"\n" +
            "        ]\n" +
            "    }\n" +
            "}";

    @Override
    public AuditResponse audit(String url) {
        // 初始化审核响应对象
        AuditResponse auditResponse = new AuditResponse();
        auditResponse.setAuditStatus(AuditStatus.SUCCESS);

        // 判断是否需要进行审核
        if (!isNeedAudit()) {
            return auditResponse;
        }

        try {
            // 如果 URL 不包含七牛云的 CNAME，进行编码并拼接完整 URL
            if (!url.contains(QiNiuConfig.CNAME)) {
                String encodedFileName = URLEncoder.encode(url, "utf-8").replace("+", "%20");
                url = String.format("%s/%s", QiNiuConfig.CNAME, encodedFileName);
            }

            // 添加一个 UUID 用于鉴权
            url = appendUUID(url);

            // 替换请求体中的 URL
            String body = imageBody.replace("${url}", url);
            String method = "POST";
            String contentType = "application/json"; // 添加 contentType 定义

            // 获取七牛云 API 请求的签名令牌
            final String token = qiNiuConfig.getToken(imageUlr, method, body, contentType);
            StringMap header = new StringMap();
            header.put("Host", "ai.qiniuapi.com");
            header.put("Authorization", token);
            header.put("Content-Type", contentType);

            // 配置七牛云 SDK 的客户端
            Configuration cfg = new Configuration(Region.region2());
            final Client client = new Client(cfg);

            // 发送 POST 请求
            Response response = client.post(imageUlr, body.getBytes(), header, contentType);

            // 解析响应结果（获取响应体）
            final Map map = objectMapper.readValue(response.getInfo().split(" \n")[2], Map.class);
            final ResultChildJson result = objectMapper.convertValue(map.get("result"), ResultChildJson.class);
            final BodyJson bodyJson = new BodyJson();
            final ResultJson resultJson = new ResultJson();
            resultJson.setResult(result);
            bodyJson.setResult(resultJson);

            // 获取审核设置
            final Setting setting = settingService.getById(1);
            // setting.getAuditPolicy()取出后台设置的自定义审核分值区间，并保存为SettingScoreJson类型
            final SettingScoreJson settingScoreRule = objectMapper.readValue(setting.getAuditPolicy(), SettingScoreJson.class);

            final List<ScoreJson> auditRule = Arrays.asList(settingScoreRule.getManualScore(), settingScoreRule.getPassScore(), settingScoreRule.getSuccessScore());

            // 执行审核
            auditResponse = audit(auditRule, bodyJson);
            return auditResponse;
        } catch (Exception e) {
            // 出现异常时，设置审核状态为成功，并打印堆栈信息
            auditResponse.setAuditStatus(AuditStatus.SUCCESS);
            e.printStackTrace();
        }
        return auditResponse;
    }
}

