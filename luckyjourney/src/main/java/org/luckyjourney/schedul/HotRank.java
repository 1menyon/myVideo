package org.luckyjourney.schedul;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.luckyjourney.constant.AuditStatus;
import org.luckyjourney.constant.RedisConstant;
import org.luckyjourney.entity.Setting;
import org.luckyjourney.entity.video.Video;
import org.luckyjourney.entity.vo.HotVideo;
import org.luckyjourney.service.SettingService;
import org.luckyjourney.service.video.VideoService;
import org.luckyjourney.util.RedisCacheUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @description: 热度排行榜
 * @Author: menyon
 * @CreateTime: 2023-10-31 00:50
 */
/**
 *   热度排行榜 前端显示不出来问题的原因：
 *      1、定时任务没触发
 *      2、没有达到热门视频的热度阈值
 *      3、redis没启动
 * */
@Component
public class HotRank {

    @Autowired
    private VideoService videoService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private SettingService settingService;

    @Autowired
    private RedisCacheUtil redisCacheUtil;

    Jackson2JsonRedisSerializer jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer(Object.class);
    ObjectMapper om = new ObjectMapper();

    {
        jackson2JsonRedisSerializer.setObjectMapper(om);
    }

    /**
     * 热度排行榜  使用了spring的定时任务：通过@Scheduled实现
     */
    @Scheduled(cron = "0 0 */1 * * ?")
    public void hotRank() {
        // 控制数量
        final TopK topK = new TopK(10, new PriorityQueue<HotVideo>(10, Comparator.comparing(HotVideo::getHot)));
        long limit = 1000;
        // 每次拿1000个，分批将所有视频都在topk中比较一遍。避免一次性全拿，否则阻塞redis的线程，其他服务无法正常使用redis
        long id = 0;
        List<Video> videos = videoService.list(new LambdaQueryWrapper<Video>()
                .select(Video::getId, Video::getShareCount, Video::getHistoryCount, Video::getStartCount, Video::getFavoritesCount,
                        Video::getGmtCreated, Video::getTitle).gt(Video::getId, id)
                .eq(Video::getAuditStatus, AuditStatus.SUCCESS).eq(Video::getOpen, 0).last("limit " + limit));
                                                                        //last(String sql): 这个方法用于在生成的 SQL 语句的最后添加额外的 SQL 片段

        while (!ObjectUtils.isEmpty(videos)) {
            for (Video video : videos) {
                Long shareCount = video.getShareCount();
                Double historyCount = video.getHistoryCount() * 0.8;
                Long startCount = video.getStartCount();
                Double favoritesCount = video.getFavoritesCount() * 1.5;
                final Date date = new Date();
                long t = date.getTime() - video.getGmtCreated().getTime();
                // 随机获取6位数,用于去重
                final double v = weightRandom();
                final double hot = hot(shareCount + historyCount + startCount + favoritesCount + v, TimeUnit.MILLISECONDS.toDays(t));
                final HotVideo hotVideo = new HotVideo(hot, video.getId(), video.getTitle());

                topK.add(hotVideo);
            }
            id = videos.get(videos.size() - 1).getId();
            videos = videoService.list(new LambdaQueryWrapper<Video>()
                    .select(Video::getId, Video::getShareCount, Video::getHistoryCount, Video::getStartCount, Video::getFavoritesCount,
                            Video::getGmtCreated, Video::getTitle).gt(Video::getId, id)
                    .eq(Video::getAuditStatus, AuditStatus.SUCCESS).eq(Video::getOpen, 0).last("limit " + limit));

        }
        final byte[] key = RedisConstant.HOT_RANK.getBytes();
        final List<HotVideo> hotVideos = topK.get();
        final Double minHot = hotVideos.get(0).getHot();
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (HotVideo hotVideo : hotVideos) {
                final Double hot = hotVideo.getHot();
                try {
                    hotVideo.setHot(null);
                    // 不这样写铁报错！序列化问题
                    connection.zAdd(key, hot, jackson2JsonRedisSerializer.serialize(om.writeValueAsString(hotVideo)));
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }
            }
            return null;
        });
        // 按照定时任务，每10min更新热度排行榜，利用topk找出所有视频中最热门的k条视频后，
        // 先插入这k条视频，再清空hot:rank。redis宕机，如何保证redis操作的原子性呢？没有清空也无关紧要
        redisTemplate.opsForZSet().removeRangeByScore(RedisConstant.HOT_RANK, minHot,0);


    }

    // 热门视频,没有热度排行榜实时且重要
    @Scheduled(cron = "0 0 */3 * * ?")
    public void hotVideo() {
        // 分片查询3天内的视频
        int limit = 1000;
        long id = 1;
        List<Video> videos = videoService.selectNDaysAgeVideo(id, 3, limit);
        final Double hotLimit = settingService.list(new LambdaQueryWrapper<Setting>()).get(0).getHotLimit();
        Calendar calendar = Calendar.getInstance();
        int today = calendar.get(Calendar.DATE);

        while (!ObjectUtils.isEmpty(videos)) {
            final ArrayList<Long> hotVideos = new ArrayList<>();
            for (Video video : videos) {
                Long shareCount = video.getShareCount();
                Double historyCount = video.getHistoryCount() * 0.8;
                Long startCount = video.getStartCount();
                Double favoritesCount = video.getFavoritesCount() * 1.5;
                final Date date = new Date();
                long t = date.getTime() - video.getGmtCreated().getTime();
                final double hot = hot(shareCount + historyCount + startCount + favoritesCount, TimeUnit.MILLISECONDS.toDays(t));

                // 大于X热度说明是热门视频
                if (hot > hotLimit) {
                    hotVideos.add(video.getId());
                }

            }
            id = videos.get(videos.size() - 1).getId();
            videos = videoService.selectNDaysAgeVideo(id, 3, limit);
            // RedisConstant.HOT_VIDEO + 今日日期 作为key  达到元素过期效果
            if (!ObjectUtils.isEmpty(hotVideos)){
                String key = RedisConstant.HOT_VIDEO + today;
                redisTemplate.opsForSet().add(key, hotVideos.toArray(new Object[hotVideos.size()]));
                redisTemplate.expire(key, 3, TimeUnit.DAYS);
            }

        }


    }

    static double a = 0.011;

    public static double hot(double weight, double t) {
        return weight * Math.exp(-a * t);
    }


    public double weightRandom() {
        int i = (int) ((Math.random() * 9 + 1) * 100000);
        return i / 1000000.0;
    }

}
