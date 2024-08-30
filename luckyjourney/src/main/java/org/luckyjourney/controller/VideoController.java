package org.luckyjourney.controller;


import io.swagger.annotations.Api;
import org.luckyjourney.entity.video.Video;
import org.luckyjourney.entity.vo.BasePage;
import org.luckyjourney.holder.UserHolder;
import org.luckyjourney.limit.Limit;
import org.luckyjourney.service.QiNiuFileService;
import org.luckyjourney.service.video.VideoService;
import org.luckyjourney.util.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author menyon
 * @since 2023-10-24
 */
@RestController
@RequestMapping("/luckyjourney/video")
@Api(tags = "视频相关接口")
public class VideoController {

    @Autowired
    private VideoService videoService;


    @Autowired
    private QiNiuFileService fileService;

    /**
     * 获取文件上传token（签名上传）
     * @return
     */
    @GetMapping("/token")
    public R getToken(){
        return R.ok().data(fileService.getToken());
    }

    /**  √
     * 发布视频/修改视频
     * 发布视频：个人中心 ——> 创作中心 ——> 上传稿件 ——> 点击"上传"
     * 整个流程是：首先前端通过签名上传 七牛云存储成功后 前端再调用FileController的保存到文件表接口 最后会调用该接口
     * 但是这里存在一个问题，如果用户连续点多次"上传"，可能会向mysql的video表中存入多条同一视频信息（解决：加一个同步锁；mysql表建立唯一索引）
     * @param video
     * @return
     */
    @PostMapping
    @Limit(limit = 5,time = 3600L,msg = "发布视频一小时内不可超过5次")
    public R publishVideo(@RequestBody @Validated Video video){
        videoService.publishVideo(video);
        return R.ok().message("发布成功,请等待审核");
    }

    /**
     * 删除视频
     * 个人中心 ——> 创作中心 ——> 稿件管理 ——> 删除
     * 请求参数：视频id
     * 注意：1、用户删除视频，最好本服务还调用七牛云删除文件资源，防止内存泄露
     *       2、如果是管理人员在七牛云的bucket中删除了某条视频（如审核不通过），
     *       则需要清空cdn缓存，保证数据一致性，避免用户还能访问到已删除的视频
     * @param id
     * @return
     */
    @DeleteMapping("/{id}")
    public R deleteVideo(@PathVariable Long id){
        videoService.deleteVideo(id);
        return R.ok().message("删除成功");
    }

    /** √
     * 个人中心 ——> 用户查看自己所管理的视频 -稿件管理（请求参数是 第几页视频，每页多少个视频 mvc封装成basepage对象）
     * 在稿件管理中 删除视频后，页面刷新，也需要调用该接口 重新展示视频
     * @param basePage
     * @return
     */
    @GetMapping
    public R listVideo(BasePage basePage){
        return R.ok().data(videoService.listByUserIdVideo(basePage,UserHolder.get()));
    }


    /**  √
     * 点赞视频/取消点赞
     */
    @PostMapping("/star/{id}")
    public R starVideo(@PathVariable Long id){
        String msg = "已点赞";
        if (!videoService.startVideo(id)) {
            msg = "取消点赞";
        }
        return R.ok().message(msg);
    }

    /**  √
     * 添加浏览记录
     * 用户刷视频的时候，就会调用该接口：1、更新视频的浏览量；2、放进用户的历史记录缓存中
     * 请求参数：视频id
     * @return
     */
    @PostMapping("/history/{id}")
    public R addHistory(@PathVariable Long id) throws Exception {
        videoService.historyVideo(id, UserHolder.get());
        return R.ok();
    }

    /**  √
     * 获取用户的浏览记录
     * 个人中心 ——> 点击历史记录 会调用这个接口
     * 请求参数：前端设置好 （第几页视频，每页多少个视频 mvc封装成basepage对象）
     * @return
     */
    @GetMapping("/history")
    public R getHistory(BasePage basePage){
        return R.ok().data(videoService.getHistory(basePage));
    }

    /**  √
     * 获取收藏夹下的视频
     * 1、个人中心 ——> 点击收藏夹 会调用这个接口
     */
    @GetMapping("/favorites/{favoritesId}")
    public R listVideoByFavorites(@PathVariable Long favoritesId){
        return R.ok().data(videoService.listVideoByFavorites(favoritesId));
    }

    /**
     * 收藏视频
     * 用户在刷视频时，如果收藏某个视频，会调用该接口，将收藏夹id、视频id、用户id（threadlocal中获取） 写入mysql的收藏-视频表中
     * 请求参数：收藏夹id、视频id
     * 注意：用户收藏的视频是无法取消收藏的（可以包装成可以取消收藏）
     * @param fId
     * @param vId
     * @return
     */
    @PostMapping("/favorites/{fId}/{vId}")
    public R favoritesVideo(@PathVariable Long fId,@PathVariable Long vId){
        String msg = videoService.favoritesVideo(fId,vId) ? "已收藏" : "取消收藏";
        return R.ok().message(msg);
    }

    /**
     * 返回当前审核队列状态（用户点击 自己的个人中心——>点击创作中心——>上传稿件页面 显示查询到的审核队列状态：快速/慢速）
     * @return
     */
    @GetMapping("/audit/queue/state")
    public R getAuditQueueState(){
        return R.ok().message(videoService.getAuditQueueState());
    }


    /**  √
     * 推送关注的人视频 拉模式
     * 点击 关注的人 会调用该接口
     * 注意：该接口需要前后端协调，前端传过来上一次查询的最小时间戳：lastTime，后端每次也需要更新lastTime并返回给前端，以便前端再次发过来
     * @param lastTime ======《滚动分页》========
     * @return
     */
    @GetMapping("/follow/feed")
    public R followFeed(@RequestParam(required = false) Long lastTime) throws ParseException {
        final Long userId = UserHolder.get();
        //因为service层已经将vedio按照发布时间排序了，并且video对象中都包含发布时间这一属性，所以前端可以解析出 上一次查询视频的最小时间戳
        return R.ok().data(videoService.followFeed(userId,lastTime));
    }

    /**  √
     * 初始化收件箱
     * 1、用户登录 会调用该接口
     * 2、用户关注别人 也会调用该接口（取关别人不会调用）
     * @return
     */
    @PostMapping("/init/follow/feed")
    public R initFollowFeed(){
        final Long userId = UserHolder.get();
        videoService.initFollowFeed(userId);
        return R.ok();
    }

}

