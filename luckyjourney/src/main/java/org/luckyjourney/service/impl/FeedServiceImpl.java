package org.luckyjourney.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.luckyjourney.constant.RedisConstant;
import org.luckyjourney.service.FeedService;
import org.luckyjourney.service.user.FollowService;
import org.luckyjourney.service.video.VideoService;
import org.luckyjourney.util.DateUtil;
import org.luckyjourney.util.RedisCacheUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @description:
 * @Author: menyon
 * @CreateTime: 2023-11-03 16:30
 */
@Service
public class FeedServiceImpl implements FeedService {



    @Autowired
    private RedisCacheUtil redisCacheUtil;

    @Autowired
    private RedisTemplate redisTemplate;



    @Override
    @Async
    public void pusOutBoxFeed(Long userId, Long videoId, Long time) {
        redisCacheUtil.zadd(RedisConstant.OUT_FOLLOW + userId, time, videoId, -1);
    }

    @Override
    public void pushInBoxFeed(Long userId, Long videoId, Long time) {
        // 需要推吗这个场景？只需要拉
    }

    // 删除粉丝收件箱中的视频id，删除博主发件箱中的视频id
    @Override
    @Async
    public void deleteOutBoxFeed(Long userId,Collection<Long> fans,Long videoId) {
        String t = RedisConstant.IN_FOLLOW;
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Long fan : fans) {
                connection.zRem((t+fan).getBytes(),String.valueOf(videoId).getBytes());
            }
            connection.zRem((RedisConstant.OUT_FOLLOW + userId).getBytes(), String.valueOf(videoId).getBytes());
            return null;
        });
    }

    /**
     *  删除用户收件箱中的 某些视频
     */
    @Override
    @Async
    public void deleteInBoxFeed(Long userId,List<Long> videoIds) {
        // remove 方法设计上会忽略不存在的元素，而不会抛出异常。这使得调用此方法更为健壮，因为在执行移除操作时，不需要事先检查元素是否存在
        redisTemplate.opsForZSet().remove(RedisConstant.IN_FOLLOW + userId, videoIds.toArray());
    }


    @Override
    @Async
    public void initFollowFeed(Long userId,Collection<Long> followIds) {
        String t2 = RedisConstant.IN_FOLLOW;
        final Date curDate = new Date();
        // 这里没有局限查多少条，而是指定查到多少天为止
        final Date limitDate = DateUtil.addDateDays(curDate, -7);
        // 先查自己的收件箱有没有最新的视频
        final Set<ZSetOperations.TypedTuple<Long>> set = redisTemplate.opsForZSet().rangeWithScores(t2 + userId, -1, -1);
        if (!ObjectUtils.isEmpty(set)) {
            Double oldTime = set.iterator().next().getScore();
            // 收件箱有最新视频，则拉取 当前时间->最新视频时间 范围内的所有博主的视频
            init(userId,oldTime.longValue(),new Date().getTime(),followIds);
        } else {
            // 收件箱没有最新视频，则拉取 最近7天内的所有博主的视频
            init(userId,limitDate.getTime(),curDate.getTime(),followIds);
        }

    }

    public void init(Long userId,Long min,Long max,Collection<Long> followIds) {
        String t1 = RedisConstant.OUT_FOLLOW;
        String t2 = RedisConstant.IN_FOLLOW;
        // 执行 Redis 管道操作，从每个关注用户的发件箱中获取指定时间范围内的视频数据
        final List<Set<DefaultTypedTuple>> result = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                for (Long followId : followIds) {
                    connection.zRevRangeByScoreWithScores((t1 + followId).getBytes(), min, max, 0, 50);
                }
                return null;
            });
        final ObjectMapper objectMapper = new ObjectMapper();
        final HashSet<Long> ids = new HashSet<>();

        // 执行 Redis 管道操作，将视频数据添加到当前用户的收件箱中
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Set<DefaultTypedTuple> tuples : result) {
                if (!ObjectUtils.isEmpty(tuples)) {

                    for (DefaultTypedTuple tuple : tuples) {
                        // 获取视频 ID 和评分（时间戳）
                        final Object value = tuple.getValue();
                        ids.add(Long.parseLong(value.toString()));
                        final byte[] key = (t2 + userId).getBytes();
                        try {
                            // 将视频 ID 和评分添加到收件箱中，并序列化视频 ID
                            connection.zAdd(key, tuple.getScore(), objectMapper.writeValueAsBytes(value));
                        } catch (JsonProcessingException e) {
                            e.printStackTrace();
                        }
                        // 设置收件箱的过期时间
                        connection.expire(key, RedisConstant.HISTORY_TIME);
                    }
                }
            }
            return null;
        });
    }

}
