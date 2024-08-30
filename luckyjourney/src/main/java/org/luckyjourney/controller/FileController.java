package org.luckyjourney.controller;

import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.luckyjourney.config.LocalCache;
import org.luckyjourney.config.QiNiuConfig;
import org.luckyjourney.entity.File;
import org.luckyjourney.entity.Setting;
import org.luckyjourney.holder.UserHolder;
import org.luckyjourney.service.FileService;
import org.luckyjourney.service.SettingService;
import org.luckyjourney.util.R;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @description:
 * @Author: menyon
 * @CreateTime: 2023-11-27 20:47
 */
@RestController
@RequestMapping("/luckyjourney/file")
@Api(tags = "文件相关接口")
@Slf4j
public class FileController implements InitializingBean {


    @Autowired
    FileService fileService;

    @Autowired
    QiNiuConfig qiNiuConfig;

    @Autowired
    SettingService settingService;

    /**
     * 签名上传
     * 保存到文件表
     * 个人中心 ——> 创作中心 ——> 上传视频 前端首先调用下面那个接口获取后端的签名token 再用签名去访问七牛云 最后调用该接口
     * 请求参数：文件的fileKey（由七牛云返回给前端：文件的hash值作为fileKey）。
     * 在七牛云存储文件成功后，前端调用该接口，后端通过七牛云工具、配置的buketName、文件的fileKey，获取文件信息，
     * 保存到mysql的file表中 返回给前端一个videoId 前端最后会调用VideoController中的发布视频接口
     * 这里存在文件隔离问题：存入mysql的file表，需要将userid + fileKey 作为fileKey.不然文件名可能会有重复
     * @return
     */
    @PostMapping
    public R save(String fileKey){

        return R.ok().data(fileService.save(fileKey, UserHolder.get()));
    }
    /**
     *  签名上传
     *  个人中心 ——> 创作中心 ——> 上传视频 点击"上传"按钮后 前端先调用该接口 获取签名token
     *  获取签名后，前端要执行下面两个请求：https://api.qiniu.com/v2/query?ak=2xR6ucGn2-zF8Jw37fcKQOR-VXawEMC9iClXCEoc&bucket=show-demo1
     *  https://upload-z2.qiniup.com/
     *  第一个请求：用于 查询和验证签名，通常是为了确认您的签名是否正确并能够使用。
     *  第二个请求：这个请求是实际进行文件 上传 的 API 地址。七牛云收到POST请求后 保存前端上传的文件，并将文件的hash值返回给前端
     * */
    @GetMapping("/getToken")
    public R token(String type){

        return R.ok().data(qiNiuConfig.uploadToken(type));
    }

    /**
     *  --访问资源--
     *  前端请求资源，发送一个文件的id
     *  1、点击 个人中心 ——> 点击 “编辑信息” 按钮 会调用该接口
     *  2、用户刷视频时，会调用该接口，获取视频资源，参数是 视频id 返回的是资源的url 被重定向到七牛云cdn 前端根据url拿到七牛云的资源
     *  3、用户搜索视频成功后，前端会调用该接口，根据视频id 获取视频资源的url：七牛云的cdn测试域名/视频文件的fileKey?uuid=********
     * */
    @GetMapping("/{fileId}")
    public void getUUid(HttpServletRequest request, HttpServletResponse response, @PathVariable  Long fileId) throws IOException {
        // 这里去掉判断ip是否有效 是因为拦截器中已经只允许本ip的网站调用请求资源的服务
        //  CORS 是一种浏览器机制，用于防止不同源的网页通过 JavaScript 访问您的资源。如果攻击者通过脚本、工具或直接的 HTTP 请求访问您的资源，CORS 配置不会阻止他们。
        String ip = request.getHeader("referer");
        // 如果不是ip白名单 调用该接口，则不返回
        if (!LocalCache.containsKey(ip)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        File url = fileService.getFileTrustUrl(fileId);
        response.setContentType(url.getType());
        // fileKey属性封装了重定向的资源地址：七牛云的cdn测试域名/文件的fileKey?uuid=********
        // 前端获取Redirect就可以重定向到七牛云的cdn，然后七牛云再回调后端服务器，校验uuid，校验成功就可以拿到云上存储的视频、图片资源
        response.sendRedirect(url.getFileKey());
    }
    /**
     *  当前端重定向到七牛云cdn后 七牛云cdn回调该接口，校验文件uuid是否正确，防止资源泄露
     *  注意：七牛云cdn服务器回调这个接口 必须要求 配置已备案的域名 否则不能回调成功
     *  当然，七牛云提供的回源鉴权服务 是无法回调该接口的 因为本服务在局域网中 且没有备案的域名。除此之外，其他访问七牛云资源的方法都要回调该接口，确保资源安全
     * */
    @PostMapping("/auth")
    public void auth(@RequestParam(required = false) String uuid, HttpServletResponse response) throws IOException {
        if (uuid == null || LocalCache.containsKey(uuid) == null){
            response.sendError(401);  // 未授权
        }else {
            LocalCache.rem(uuid);
            response.sendError(200);
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        final Setting setting = settingService.list(null).get(0);
        for (String s : setting.getAllowIp().split(",")) {
            LocalCache.put(s,true);
        }
    }
}
