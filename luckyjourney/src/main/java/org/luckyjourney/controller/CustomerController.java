package org.luckyjourney.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.annotations.Api;
import org.luckyjourney.config.QiNiuConfig;
import org.luckyjourney.entity.user.Favorites;
import org.luckyjourney.entity.user.User;
import org.luckyjourney.entity.vo.*;
import org.luckyjourney.holder.UserHolder;
import org.luckyjourney.service.user.FavoritesService;
import org.luckyjourney.service.user.UserService;
import org.luckyjourney.util.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ObjectUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @description:
 * @Author: menyon
 * @CreateTime: 2023-10-25 15:27
 */
@RestController
@RequestMapping("/luckyjourney/customer")
@Api(tags = "获取用户个人信息相关接口")
public class CustomerController {


    @Autowired
    QiNiuConfig qiNiuConfig;

    @Autowired
    private UserService userService;

    @Autowired
    private FavoritesService favoritesService;


    /**
     * 获取个人信息
     * 点击 个人中心、创作中心、收藏夹、历史记录、订阅分类、关注/粉丝 会调用该接口
     * 该接口需要移动到IndexCustomerController下
     * @param userId
     * @return
     * @throws Exception
     */
    @GetMapping("/getInfo/{userId}")
    public R getInfo(@PathVariable Long userId){
        return R.ok().data(userService.getInfo(userId));
    }

    /**
     * 获取用户信息（登录成功时执行该请求）
     * @return
     */
    @GetMapping("/getInfo")
    public R getDefaultInfo(){
        return R.ok().data(userService.getInfo(UserHolder.get()));
    }

    /**
     * 获取关注人员
     * 个人中心 ——> 点击关注/粉丝 会调用这个接口
     * 请求参数是如下格式：userId=21&page=1&limit=10，mvc会将page=1&limit=10封装成basePage对象
     * @param basePage
     * @param userId
     * @return
     */
    @GetMapping("/follows")
    public R getFollows(BasePage basePage,Long userId){
        return R.ok().data(userService.getFollows(userId,basePage));
    }

    /**
     * 获取粉丝
     * 个人中心 ——> 点击关注/粉丝 会调用这个接口
     * 请求参数是如下格式：userId=21&page=1&limit=10，mvc会将page=1&limit=10封装成basePage对象
     * @param basePage
     * @param userId
     * @return
     */
    @GetMapping("/fans")
    public R getFans(BasePage basePage,Long userId){
        return R.ok().data(userService.getFans(userId,basePage));
    }


    /**
     * 获取所有的收藏夹
     * 1、个人中心 ——> 点击收藏夹 会调用这个接口
     * 2、用户刷视频时，如果收藏某个视频，也会调用该接口，显示用户的收藏夹信息，让用户选择一个收藏夹进行收藏
     * @return
     */
    @GetMapping("/favorites")
    public R listFavorites(){
        final Long userId = UserHolder.get();
        List<Favorites> favorites = favoritesService.listByUserId(userId);
        return R.ok().data(favorites);
    }


    /**
     * 获取指定收藏夹
     * 个人中心 ——> 收藏夹 ——> 点击某一个收藏夹 会调用该接口（一般是修改收藏夹信息时先显示在页面上）
     * @param id
     * @return
     */
    @GetMapping("/favorites/{id}")
    public R getFavorites(@PathVariable Long id){
        // 这里最好带上用户id，不然别人也能通过工具获取到你的收藏夹
        return R.ok().data(favoritesService.getById(id));
    }

    /**
     * 添加/修改收藏夹
     * 个人中心 ——> 收藏夹 ——> 添加收藏夹/修改收藏夹 会调用这个接口
     * 这个接口 要跟获取所有的收藏夹、获取收藏夹下的视频 两个接口一起调用，因为页面需要进行回显
     * @param favorites
     * @return
     */
    @PostMapping("/favorites")
    public R saveOrUpdateFavorites(@RequestBody @Validated Favorites favorites){
        final Long userId = UserHolder.get();
        final Long id = favorites.getId();
        favorites.setUserId(userId);
        final int count = favoritesService.count(new LambdaQueryWrapper<Favorites>()
                .eq(Favorites::getName, favorites.getName()).eq(Favorites::getUserId, userId).ne(Favorites::getId,favorites.getId()));
        if (count == 1){
            return R.error().message("已存在相同名称的收藏夹");
        }
        favoritesService.saveOrUpdate(favorites);
        return R.ok().message(id !=null ? "修改成功" : "添加成功");
    }

    /**
     * 删除收藏夹
     * 删除mysql中的收藏夹表、收藏夹-视频表 对应信息
     * @param id
     * @return
     */
    @DeleteMapping("/favorites/{id}")
    public R deleteFavorites(@PathVariable Long id){
        favoritesService.remove(id,UserHolder.get());
        return R.ok().message("删除成功");
    }


    /**
     * 订阅分类 types: "体育,音乐,美食"
     * 点击 个人中心 ——> 订阅分类 ——> 用户在自己的订阅分类中 添加/删除某一个分类 会调用该接口
     * 同时，调用该接口一定会调用下面两个接口（进行操作后的回显）
     */
    @PostMapping("/subscribe")
    public R subscribe(@RequestParam(required = false) String types){
        final HashSet<Long> typeSet = new HashSet<>();
        String msg = "取消订阅";
        if (!ObjectUtils.isEmpty(types)){
            for (String s : types.split(",")) {
                typeSet.add(Long.parseLong(s));
            }
            msg = "订阅成功";
        }
        userService.subscribe(typeSet);
        return R.ok().message(msg);
    }

    /**
     * 获取用户订阅的分类
     * 点击 个人中心 ——> 订阅分类 会调用该接口
     * @return
     */
    @GetMapping("/subscribe")
    public R listSubscribeType(){
        return R.ok().data(userService.listSubscribeType(UserHolder.get()));
    }

    /**
     * 获取用户没订阅的分类
     * 点击 个人中心 ——> 订阅分类 会调用该接口
     * @return
     */
    @GetMapping("/noSubscribe")
    public R listNoSubscribeType(){
        return R.ok().data(userService.listNoSubscribeType(UserHolder.get()));
    }

    /** √
     * 关注/取关
     * 点击用户头像下面的"＋" 会调用该接口
     * @param followsUserId
     * @return
     */
    @PostMapping("/follows")
    public R follows(@RequestParam Long followsUserId){

        return R.ok().message(userService.follows(followsUserId) ? "已关注" : "已取关");
    }

    /** √
     * 用户停留时长修改模型
     * 用户在刷视频时，上下切换视频后，会调用该接口    （根据用户在上一条视频的停留时长 修改兴趣模型）
     * @param model
     * @return
     */
    @PostMapping("/updateUserModel")
    public R updateUserModel(@RequestBody Model model){
        final Double score = model.getScore();
        if (score == -0.5 || score == 1.0){
            final UserModel userModel = new UserModel();
            userModel.setUserId(UserHolder.get());
            // 使用 Collections.singletonList 创建的列表只包含一个元素，且是不可变的。这意味着你不能对列表进行添加、删除或修改操作
            userModel.setModels(Collections.singletonList(model));
            userService.updateUserModel(userModel);
        }
        return R.ok();
    }

    /**
     * 获取用户上传头像的token
     * @return
     */
    @GetMapping("/avatar/token")
    public R avatarToken(){
        return R.ok().data(qiNiuConfig.imageUploadToken());
    }

    /** √
     *  修改用户信息
     *  点击 个人中心 ——> 编辑信息 ——> 提交修改 会调用该接口
     * @param user
     * @return
     */
    @PutMapping
    public R updateUser(@RequestBody @Validated UpdateUserVO user){
        Long userId = UserHolder.get();
        user.setUserId(userId);
        userService.updateUser(user);
        return R.ok().message("修改成功");
    }


}
