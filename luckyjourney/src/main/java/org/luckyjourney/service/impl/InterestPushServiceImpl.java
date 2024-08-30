package org.luckyjourney.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.luckyjourney.constant.RedisConstant;
import org.luckyjourney.entity.video.Video;
import org.luckyjourney.entity.user.User;
import org.luckyjourney.entity.vo.HotVideo;
import org.luckyjourney.entity.vo.Model;
import org.luckyjourney.entity.vo.UserModel;
import org.luckyjourney.service.InterestPushService;
import org.luckyjourney.service.video.TypeService;
import org.luckyjourney.util.RedisCacheUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @description:
 * @Author: menyon
 * @CreateTime: 2023-10-26 11:54
 */
// 暂时为异步
@Service
public class InterestPushServiceImpl implements InterestPushService {

    @Autowired
    private RedisCacheUtil redisCacheUtil;

    @Autowired
    private TypeService typeService;

    @Autowired
    private RedisTemplate redisTemplate;


    final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Async
    public void pushSystemStockIn(Video video) {
        // 往系统库中添加
        final List<String> labels = video.buildLabel();
        final Long videoId = video.getId();
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (String label : labels) {
                connection.sAdd((RedisConstant.SYSTEM_STOCK + label).getBytes(),String.valueOf(videoId).getBytes());
            }
            return null;
        });
    }

    @Override
    @Async
    public void pushSystemTypeStockIn(Video video) {
        final Long typeId = video.getTypeId();
        redisCacheUtil.sSet(RedisConstant.SYSTEM_TYPE_STOCK + typeId,video.getId());
    }

    @Override
    public Collection<Long> listVideoIdByTypeId(Long typeId) {
        // 随机推送10个

        final List<Object> list = redisTemplate.opsForSet().randomMembers(RedisConstant.SYSTEM_TYPE_STOCK + typeId, 12);
        // 可能会有null
        final HashSet<Long> result = new HashSet<>();
        for (Object aLong : list) {
            if (aLong!=null){
                result.add(Long.parseLong(aLong.toString()));
            }
        }
        return result;
    }

    /**
     *   删除该视频对应标签的 对应系统标签库中的该视频id
     * */
    @Override
    @Async
    public void deleteSystemStockIn(Video video) {
        final List<String> labels = video.buildLabel();
        final Long videoId = video.getId();
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (String label : labels) {
                connection.sRem((RedisConstant.SYSTEM_STOCK + label).getBytes(),String.valueOf(videoId).getBytes());
            }
            return null;
        });
    }

    @Override
    @Async
    public void initUserModel(Long userId, List<String> labels) {
        // 为什么要用redis？因为用户在每刷一条视频，都需要更新兴趣模型（点赞、停留时长、收藏、转发）
        final String key = RedisConstant.USER_MODEL + userId;
        Map<Object, Object> modelMap = new HashMap<>();
        if (!ObjectUtils.isEmpty(labels)) {
            final int size = labels.size();
            // 将标签分为等分概率,不可能超过100个分类
            double probabilityValue = 100 / size;
            for (String labelName : labels) {
                modelMap.put(labelName, probabilityValue);
            }
        }
        redisCacheUtil.del(key);
        redisCacheUtil.hmset(key, modelMap);
        // TODO 为用户模型设置ttl，设置过期时间，用户一段时间不上线后，清空用户兴趣模型
        // 这个还是比较好实现，因为用户在线肯定会刷视频，也就会执行更新兴趣模型的方法，在方法中延长TTL即可

    }

    /**
     *  用户刷视频时 会根据观看动作 执行下面方法（点赞、收藏、观看时长达到多长时间）
     * */
    @Override
    @Async
    public void updateUserModel(UserModel userModel) {
        // userModel的作用：视频的标签id前端传过来不只有一个，因为视频可能被标上多个标签，将标签一个一个装到models中
        final Long userId = userModel.getUserId();
        // 游客不用管
        if (userId != null) {
            final List<Model> models = userModel.getModels();
            // 获取用户模型
            String key = RedisConstant.USER_MODEL + userId;
            // key：分类标签的id  value：概率/比重/兴趣数组长度
            Map<Object, Object> modelMap = redisCacheUtil.hmget(key);

            if (modelMap == null) {
                // 用户模型是空的，从redis中获取为Null，则需要初始化为一个新的空集合
                modelMap = new HashMap<>();
            }
            for (Model model : models) {
                // 修改用户模型
                if (modelMap.containsKey(model.getLabel())) {
                    modelMap.put(model.getLabel(), Double.parseDouble(modelMap.get(model.getLabel()).toString()) + model.getScore());
                    final Object o = modelMap.get(model.getLabel());
                    // 用户模型中某个标签的分数小于0，则表示用户不感兴趣，从modelMap中删除
                    if (o == null || Double.parseDouble(o.toString()) <= 0.0){
                        modelMap.remove(model.getLabel());
                    }
                } else {
                    // 这里可能是用户长期不登录，用户模型被清空，则根据前端送过来的：标签id、视频id、得分来更新用户模型
                    modelMap.put(model.getLabel(), model.getScore());
                }
            }

            // 每个标签概率同等加上标签数，再同等除以标签数  防止数据膨胀
            /**
             *   同等比例处理的主要目的是为了防止数据膨胀，确保模型的公平性和动态平衡。
             *   这种处理方法可以使用户模型在面对标签数量的变化时保持稳定，避免某些标签得分过高或过低，
             *   从而更准确地反映用户的兴趣分布。对于实际应用中的数据调整和模型更新，这种方法是有效且常见的。
             * */
            final int labelSize = modelMap.keySet().size();
            for (Object o : modelMap.keySet()) {
                modelMap.put(o,(Double.parseDouble(modelMap.get(o).toString()) + labelSize )/ labelSize);
            }
            // 更新用户模型
            redisCacheUtil.hmset(key, modelMap);
        }

    }

    @Override
    public Collection<Long> listVideoIdByUserModel(User user) {
        // 创建结果集
        Set<Long> videoIds = new HashSet<>(10);

        if (user != null) {
            final Long userId = user.getId();
            // 从模型中拿概率
            final Map<Object, Object> modelMap = redisCacheUtil.hmget(RedisConstant.USER_MODEL + userId);
            if (!ObjectUtils.isEmpty(modelMap)) {
                // 组成数组
                final String[] probabilityArray = initProbabilityArray(modelMap);
                final Boolean sex = user.getSex();
                // 获取视频
                final Random randomObject = new Random();
                final ArrayList<String> labelNames = new ArrayList<>();
                // 随机获取X个视频
                for (int i = 0; i < 8; i++) {
                    String labelName = probabilityArray[randomObject.nextInt(probabilityArray.length)];
                    labelNames.add(labelName);
                }
                // 提升性能（获取分类推送的视频也可以采用如下方式）
                String t = RedisConstant.SYSTEM_STOCK;
                // 随机获取  executePipelined管道——>一批指令
                List<Object> list = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                    for (String labelName : labelNames) {
                        String key = t + labelName;
                        // 随机获取set中的一个视频id
                        connection.sRandMember(key.getBytes());
                    }
                    return null;
                });
                // 获取到的videoIds（Set对视频id去重）
                Set<Long> ids = list.stream().filter(id->id!=null).map(id->Long.parseLong(id.toString())).collect(Collectors.toSet());
                String key2 = RedisConstant.HISTORY_VIDEO;

                // 去重 用户看过的视频就不推送了 下面这块代码可以用 布隆过滤器来实现
                List simpIds = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                    for (Long id : ids) {
                        connection.get((key2 + id + ":" + userId).getBytes());
                    }
                    return null;
                });
                simpIds = (List) simpIds.stream().filter(o->!ObjectUtils.isEmpty(o)).collect(Collectors.toList());;
                if (!ObjectUtils.isEmpty(simpIds)){
                    for (Object simpId : simpIds) {
                        final Long l = Long.valueOf(simpId.toString());
                        if (ids.contains(l)){
                            ids.remove(l);
                        }
                    }
                }


                videoIds.addAll(ids);

                // 随机挑选一个视频,根据性别: 男：美女 女：宠物
                final Long aLong = randomVideoId(sex);
                if (aLong!=null){
                    videoIds.add(aLong);
                }
                // 随机挑选一个热门视频
                final Long bLong = randomHotVideoId();
                if (bLong!=null){
                    videoIds.add(bLong);
                }

                return videoIds;
            }
        }
        // 游客
        // 随机获取10个标签
        final List<String> labels = typeService.random10Labels();
        final ArrayList<String> labelNames = new ArrayList<>();
        int size = labels.size();
        final Random random = new Random();
        // 获取随机的标签
        for (int i = 0; i < 10; i++) {
            final int randomIndex = random.nextInt(size);
            labelNames.add(RedisConstant.SYSTEM_STOCK + labels.get(randomIndex));
        }
        // 获取videoId，用set去重了
        final List<Object> list = redisCacheUtil.sRandom(labelNames);
        if (!ObjectUtils.isEmpty(list)){
            videoIds = list.stream().filter(id ->!ObjectUtils.isEmpty(id)).map(id -> Long.valueOf(id.toString())).collect(Collectors.toSet());
        }

        return videoIds;
    }

    @Override
    public Collection<Long> listVideoIdByLabels(List<String> labelNames) {
        final ArrayList<String> labelKeys = new ArrayList<>();
        for (String labelName : labelNames) {
            labelKeys.add(RedisConstant.SYSTEM_STOCK + labelName);
        }
        Set<Long> videoIds = new HashSet<>();
        final List<Object> list = redisCacheUtil.sRandom(labelKeys);
        if (!ObjectUtils.isEmpty(list)){
            videoIds = list.stream().filter(id ->!ObjectUtils.isEmpty(id)).map(id -> Long.valueOf(id.toString())).collect(Collectors.toSet());
        }
        return videoIds;
    }

    /**
     *   删除该视频对应分类的 对应的系统分类库中的该视频id
     * */
    @Override
    @Async
    public void deleteSystemTypeStockIn(Video video) {
        final Long typeId = video.getTypeId();
        redisCacheUtil.setRemove(RedisConstant.SYSTEM_TYPE_STOCK + typeId,video.getId());
    }


    public Long randomHotVideoId(){
        final Object o = redisTemplate.opsForZSet().randomMember(RedisConstant.HOT_RANK);
        try {
            return objectMapper.readValue(o.toString(),HotVideo.class).getVideoId();
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Long randomVideoId(Boolean sex) {
        String key = RedisConstant.SYSTEM_STOCK + (sex ? "美女" : "宠物");
        final Object o = redisCacheUtil.sRandom(key);
        if (o!=null){
            return Long.parseLong(o.toString());
        }
        return null;
    }

    // 随机获取视频id
    public Long getVideoId(Random random, String[] probabilityArray) {
        String labelName = probabilityArray[random.nextInt(probabilityArray.length)];
        // 获取对应所有视频
        String key = RedisConstant.SYSTEM_STOCK + labelName;
        final Object o = redisCacheUtil.sRandom(key);
        if (o!=null){
            return Long.parseLong(o.toString()) ;
        }
        return null;
    }

    // 初始化概率数组 -> 保存的元素是标签  算法：对标抽奖算法
    public String[] initProbabilityArray(Map<Object, Object> modelMap) {
        // key: 标签  value：概率
        Map<String, Integer> probabilityMap = new HashMap<>();
        int size = modelMap.size();
        final AtomicInteger n = new AtomicInteger(0);
        modelMap.forEach((k, v) -> {
            // 防止结果为0,每个同等加上标签数
            /**
             *  同等比例处理的主要目的是为了确保标签的概率分布在概率数组中公平且平滑，
             *  从而避免概率为零或数据偏斜问题。这种方法可以确保在进行抽样或随机选择时，
             *  各个标签都有合适的机会被选中，并且能够更好地反映实际标签的分布情况。
             * */
            int probability = (((Double) v).intValue() + size) / size;
            n.getAndAdd(probability);
            probabilityMap.put(k.toString(), probability);
        });
        final String[] probabilityArray = new String[n.get()];

        final AtomicInteger index = new AtomicInteger(0);
        // 初始化数组
        probabilityMap.forEach((labelsId, p) -> {
            int i = index.get();
            int limit = i + p;
            while (i < limit) {
                probabilityArray[i++] = labelsId;
            }
            index.set(limit);
        });
        return probabilityArray;
    }




}
