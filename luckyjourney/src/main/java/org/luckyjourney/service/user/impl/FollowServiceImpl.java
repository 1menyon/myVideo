package org.luckyjourney.service.user.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.lettuce.core.support.caching.RedisCache;
import org.luckyjourney.constant.RedisConstant;
import org.luckyjourney.entity.user.Follow;
import org.luckyjourney.entity.vo.BasePage;
import org.luckyjourney.entity.vo.FollowVO;
import org.luckyjourney.exception.BaseException;
import org.luckyjourney.mapper.FollowMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.luckyjourney.service.FeedService;
import org.luckyjourney.service.user.FollowService;
import org.luckyjourney.service.video.VideoService;
import org.luckyjourney.util.RedisCacheUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.annotation.security.DenyAll;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author menyon
 * @since 2023-10-25
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements FollowService {

    @Autowired
    private FeedService feedService;

    @Autowired
    @Lazy
    private VideoService videoService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RedisCacheUtil redisCacheUtil;

    @Override
    public int getFollowCount(Long userId) {
        return count(new LambdaQueryWrapper<Follow>().eq(Follow::getUserId, userId));
    }

    @Override
    public int getFansCount(Long userId) {
        return count(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowId, userId));
    }

    @Override
    public Collection<Long> getFollow(Long userId, BasePage basePage) {
        if (basePage == null) {
            final Set<Object> set = redisCacheUtil.zGet(RedisConstant.USER_FOLLOW + userId);
            if (ObjectUtils.isEmpty(set)){
                return Collections.EMPTY_SET;   // 自定义的一个空的set，且无法被修改
            }
            return set.stream().map(o->Long.valueOf(o.toString())).collect(Collectors.toList());
        }
        // redis本身没有提供分页api，自己实现的一个redis分页查询
        final Set<ZSetOperations.TypedTuple<Object>> typedTuples = redisCacheUtil.zSetGetByPage(RedisConstant.USER_FOLLOW + userId, basePage.getPage(), basePage.getLimit());
        // 可能redis崩了,从db拿（兜底的策略）
        if (ObjectUtils.isEmpty(typedTuples)) {
            final List<Follow> follows = page(basePage.page(),new LambdaQueryWrapper<Follow>().eq(Follow::getUserId, userId).orderByDesc(Follow::getGmtCreated)).getRecords();
            if (ObjectUtils.isEmpty(follows)) {
                return Collections.EMPTY_LIST;  // 自定义的一个空的list，且无法被修改
            }
            return follows.stream().map(Follow::getFollowId).collect(Collectors.toList());
        }
        return typedTuples.stream().map(t -> Long.parseLong(t.getValue().toString())).collect(Collectors.toList());
    }




    @Override
    public Collection<Long> getFans(Long userId, BasePage basePage) {
        if (basePage == null) {
            final Set<Object> set = redisCacheUtil.zGet(RedisConstant.USER_FANS + userId);
             if(ObjectUtils.isEmpty(set)){
                return Collections.EMPTY_SET;
            }
//            利用了 Java 8 的流式 API 来简洁地完成这个转换过程
            return set.stream().map(o->Long.valueOf(o.toString())).collect(Collectors.toList());
        }
        //  ZSetOperations.TypedTuple<Object>包含了value和sorce
        final Set<ZSetOperations.TypedTuple<Object>> typedTuples = redisCacheUtil.zSetGetByPage(RedisConstant.USER_FANS + userId, basePage.getPage(), basePage.getLimit());
        if (ObjectUtils.isEmpty(typedTuples)) {
            // 如果redis中不存在粉丝信息（过期了），则从数据库中查找
            final List<Follow> follows = page(basePage.page(),new LambdaQueryWrapper<Follow>().eq(Follow::getFollowId, userId)).getRecords();
            if (ObjectUtils.isEmpty(follows)){
                return Collections.EMPTY_LIST;
            }
            // 下面的表达式等价于：return follows.stream().map(follow -> follow.getUserId()).collect(Collectors.toList());
            return follows.stream().map(Follow::getUserId).collect(Collectors.toList());
        }
        return typedTuples.stream().map(t -> Long.parseLong(t.getValue().toString())).collect(Collectors.toList());
    }

    @Override
    public Boolean follows(Long followsId, Long userId) {

        if (followsId.equals(userId)) {
            throw new BaseException("你不能关注自己");
        }

        // 直接保存(唯一索引),保存失败则删除
        final Follow follow = new Follow();
        follow.setFollowId(followsId);
        follow.setUserId(userId);
        try {
            save(follow);   // 如果保存成功，则表示关注；如果抛异常，则表示取关，直接走catch代码块
            final Date date = new Date();
            // 自己关注列表添加
            redisTemplate.opsForZSet().add(RedisConstant.USER_FOLLOW + userId, followsId, date.getTime());
            // 对方粉丝列表添加
            redisTemplate.opsForZSet().add(RedisConstant.USER_FANS + followsId, userId, date.getTime());
            // 这里也可将 被关注人的发件箱中的视频 推送给粉丝的收件箱。但是不建议，因为用户可以自己点进被关注人的头像观看视频。推太多容易反感。
        } catch (Exception e) {
            // 删除（mysql数据库中有follow表，但是没有fans表，这是因为一般情况下：一个用户最多也只关注几千个人，但是一个大v，可能有几千万粉丝）
            remove(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowId, followsId).eq(Follow::getUserId, userId));
            // 删除收件箱的视频
            // 获取关注人的视频 feed流相关
            final List<Long> videoIds = (List<Long>) videoService.listVideoIdByUserId(followsId);
            feedService.deleteInBoxFeed(userId, videoIds);
            // 自己关注列表删除
            redisTemplate.opsForZSet().remove(RedisConstant.USER_FOLLOW + userId, followsId);
            // 对方粉丝列表删除
            redisTemplate.opsForZSet().remove(RedisConstant.USER_FANS + followsId, userId);
            return false;
        }

        return true;
    }

    @Override
    public Boolean isFollows(Long followId, Long userId) {
        if (userId == null || followId == null) return false;
        return count(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowId,followId).eq(Follow::getUserId,userId)) == 1;
    }
}
