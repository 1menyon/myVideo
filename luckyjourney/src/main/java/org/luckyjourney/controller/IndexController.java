package org.luckyjourney.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.luckyjourney.entity.video.Type;
import org.luckyjourney.entity.video.Video;
import org.luckyjourney.entity.video.VideoShare;
import org.luckyjourney.entity.vo.BasePage;
import org.luckyjourney.holder.UserHolder;
import org.luckyjourney.service.user.UserService;
import org.luckyjourney.service.video.TypeService;
import org.luckyjourney.service.video.VideoService;
import org.luckyjourney.util.JwtUtils;
import org.luckyjourney.util.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @description:
 * @Author: menyon
 * @CreateTime: 2023-10-27 16:06
 */
@RestController
@RequestMapping("/luckyjourney/index")
@Api(tags = "兴趣、排行榜、推送、分享、搜索相关接口")
public class IndexController {

    @Autowired
    private UserService userService;

    @Autowired
    private VideoService videoService;

    @Autowired
    private TypeService typeService;

    /**
     * 兴趣推送视频
     * 点击"推荐视频" 调用该接口
     * @return
     */
    @GetMapping("/pushVideos")
    @ApiOperation(value = "兴趣推送视频")
    public R pushVideos(HttpServletRequest request){
        final Long userId = JwtUtils.getUserId(request);
        return R.ok().data(videoService.pushVideos(userId));
    }

    /**
     * 搜索视频
     * 点击"搜索视频"
     * 请求参数： 视频标题、分页参数page=1&limit=15（mvc会将其封装为basePage）
     * 返回：video对象集合（之后前端获取一组视频id，请求fileController中的访问资源接口）
     * @return
     */
    @GetMapping("/search")
    public R searchVideo(@RequestParam(required = false) String searchName, BasePage basePage,HttpServletRequest request){

        return R.ok().data(videoService.searchVideo(searchName,basePage,JwtUtils.getUserId(request)));
    }

    /**
     * 根据视频分类获取
     * 点击主菜单的分类栏（美女、帅哥、体育、美食） 调用该方法
     * @param typeId
     * @return
     */
    @GetMapping("/video/type/{typeId}")
    public R getVideoByTypeId(@PathVariable Long typeId){

        return R.ok().data(videoService.getVideoByTypeId(typeId));
    }

    /**
     * 获取所有分类
     * 1、个人中心 ——> 创作中心 ——> 上传稿件
     * 前端调用该接口 展示给用户所有可选的分类 例如：体育、美食、舞蹈，用户可以给视频加上分类
     * 2、个人中心 ——> 创作中心 ——> 稿件管理 ——> 编辑 （用户可以修改分类、视频描述、视频标题）
     * @return
     */
    @GetMapping("/types")
    public R getTypes(HttpServletRequest request){
        final List<Type> types = typeService.list(new LambdaQueryWrapper<Type>().select(Type::getIcon, Type::getId, Type::getName).orderByDesc(Type::getSort));

        final Set<Long> set = userService.listSubscribeType(JwtUtils.getUserId(request)).stream().map(Type::getId).collect(Collectors.toSet());

        for (Type type : types) {
            if (set.contains(type.getId())) {
                type.setUsed(true);
            }else {
                type.setUsed(false);
            }
        }
        return R.ok().data(types);
    }

    /**
     * 分享视频（游客也可调用）
     * 用户在刷视频时，如果点击分享视频按钮 会调用该接口 复制的视频地址如下：127.0.0.1:5378/#/?play=4817
     * mysql中视频表中 对应视频的 分享次数字段+1
     * @param videoId
     * @param request
     * @return
     */
    @PostMapping("/share/{videoId}")
    public R share(@PathVariable Long videoId, HttpServletRequest request){

        String ip = null;
        if (request.getHeader("x-forwarded-for") == null)
            ip = request.getRemoteAddr();
        else
            ip = request.getHeader("x-forwarded-for");
        final VideoShare videoShare = new VideoShare();

        videoShare.setVideoId(videoId);
        videoShare.setIp(ip);
        if (JwtUtils.checkToken(request)) {
            videoShare.setUserId(JwtUtils.getUserId(request));
        }
        videoService.shareVideo(videoShare);
        return R.ok();
    }

    /**
     * 根据id获取视频详情
     * 请求参数：视频表主键id（视频id）
     * 观看视频会调用
     * @param id
     * @return
     */
    @GetMapping("/video/{id}")
    public R getVideoById(@PathVariable Long id,HttpServletRequest request){
        final Long userId = JwtUtils.getUserId(request);
        return R.ok().data(videoService.getVideoById(id,userId));
    }

    /**
     * 获取热度排行榜
     * 1、点击主菜单最后一栏 调用该接口；
     * 2、点击"热门视频" 会调用该接口
     * 3、点击"搜索视频" 会调用该接口
     * @return
     */
    @GetMapping("/video/hot/rank")
    public R listHotRank(){
        return R.ok().data(videoService.hotRank());
    }

    /**
     * 根据视频标签推送相似视频
     * 删除收藏夹时，如果收藏夹中存在视频，会调用该接口....
     * @param video
     * @return
     */
    @GetMapping("/video/similar")
    public R pushVideoSimilar(Video video){
        return R.ok().data(videoService.listSimilarVideo(video));
    }

    /**
     * 推送热门视频
     * 点击"热门视频" 会调用该接口
     * @return
     */
    @GetMapping("/video/hot")
    public R listHotVideo(){
        return R.ok().data(videoService.listHotVideo());
    }

    /** √
     * 根据用户id获取视频
     * 请求参数：userId=21&page=1&limit=10，mvc会将page=1&limit=10封装为BasePage对象
     * 1、游客没有自己的主页，不会携带token和userid，所以获取为空；
     * 2、用户/游客访问别人的头像，路径参数中携带userid，所以可以获取到别人的公开视频；
     * 3、用户访问自己的个人主页，路径参数中不会携带userid，需要request中获取userid。/用户也无法点击自己的头像访问自己的主页。
     * @param userId
     * @param basePage
     * @return
     */
    @GetMapping("/video/user")
    public R listVideoByUserId(@RequestParam(required = false) Long userId,
                               BasePage basePage,HttpServletRequest request){
        // 这里如果是点击头像 则可以获取到userid，如果是点击个人中心，则没有用户id，需要从request中获取
        userId = userId == null ? JwtUtils.getUserId(request) : userId;
        return R.ok().data(videoService.listByUserIdOpenVideo(userId,basePage));
    }

    /**
     * 获取用户搜索记录
     * "搜索视频"后 页面回显搜索记录 会调用该接口
     * @return
     */
    @GetMapping("/search/history")
    public R searchHistory(HttpServletRequest request){
        return R.ok().data(userService.searchHistory(JwtUtils.getUserId(request)));
    }

    // 删除搜索记录
    @DeleteMapping("/search/history")
    public R deleteSearchHistory(HttpServletRequest request){
        userService.deleteSearchHistory(JwtUtils.getUserId(request));
        return R.ok();
    }
}
