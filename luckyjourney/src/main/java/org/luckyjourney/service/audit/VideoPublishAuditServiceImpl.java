package org.luckyjourney.service.audit;

import org.luckyjourney.config.LocalCache;
import org.luckyjourney.config.QiNiuConfig;
import org.luckyjourney.constant.AuditStatus;
import org.luckyjourney.entity.File;
import org.luckyjourney.entity.response.AuditResponse;
import org.luckyjourney.entity.task.VideoTask;
import org.luckyjourney.entity.video.Video;
import org.luckyjourney.entity.vo.VideoVO;
import org.luckyjourney.mapper.FileMapper;
import org.luckyjourney.mapper.video.VideoMapper;
import org.luckyjourney.service.FeedService;
import org.luckyjourney.service.FileService;
import org.luckyjourney.service.QiNiuFileService;
import org.luckyjourney.service.InterestPushService;
import org.luckyjourney.service.user.FollowService;
import org.luckyjourney.util.FileUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @description: 视频发布审核
 * @Author: menyon
 * @CreateTime: 2023-10-29 14:40
 */
@Service
public class VideoPublishAuditServiceImpl implements AuditService<VideoTask,VideoTask> , InitializingBean,BeanPostProcessor {

    @Autowired
    private FeedService feedService;

    @Autowired
    private VideoMapper videoMapper;

    @Autowired
    private FileMapper fileMapper;

    @Autowired
    private InterestPushService interestPushService;

    @Autowired
    private QiNiuFileService qiNiuFileService;

    @Autowired
    private TextAuditService textAuditService;

    @Autowired
    private ImageAuditService imageAuditService;

    @Autowired
    private VideoAuditService videoAuditService;

    @Autowired
    private FollowService followService;

    @Autowired
    private FileService fileService;

    private int maximumPoolSize = 8;

    protected ThreadPoolExecutor executor;

    /**
     *
     * @param videoTask
     * @param auditQueueState 申请快/慢审核
     * @return
     */
    public VideoTask audit(VideoTask videoTask,Boolean auditQueueState){
        //  这里的审核队列是用的线程池队列，还可以自己实现，采用阻塞队列BlockingArrayDeque、redis消息队列、RabbitMQ消息队列
        // 快审核：加一个线程执行。但是感觉慢审核也得异步执行，只不过慢审核交给一个线程数较少的线程池
        if (auditQueueState){
            // 快审核再单独开一个线程
            new Thread(()->{
                audit(videoTask);
            }).start();
        }else {
            // 慢审核用线程池
            audit(videoTask);
        }
        return null;
    }

    // 进行任务编排
    @Override
    public VideoTask audit(VideoTask videoTask) {
        executor.submit(()->{
            final Video video = videoTask.getVideo();
            final Video video1 = new Video();
            BeanUtils.copyProperties(video,video1);
            // 只有视频在新增或者公开时候才需要调用审核视频/封面
            // 新增 ： 必须审核
            // 修改: 新老状态不一致
            // 需要审核视频/封面
            boolean needAuditVideo = false;
            if (videoTask.getIsAdd()  && videoTask.getOldState() == videoTask.getNewState()){
                needAuditVideo = true;
            }else if (!videoTask.getIsAdd() && videoTask.getOldState() != videoTask.getNewState()){
                // 修改的情况下新老状态不一致,说明需要更新
                if (!videoTask.getNewState()){
                   needAuditVideo = true;
                }
            }
            AuditResponse videoAuditResponse = new AuditResponse(AuditStatus.SUCCESS,"正常");
            AuditResponse coverAuditResponse = new AuditResponse(AuditStatus.SUCCESS,"正常");
            AuditResponse titleAuditResponse = new AuditResponse(AuditStatus.SUCCESS,"正常");
            AuditResponse descAuditResponse = new AuditResponse(AuditStatus.SUCCESS,"正常");

            if (needAuditVideo){

                  videoAuditResponse = videoAuditService.audit(QiNiuConfig.CNAME+"/"+fileService.getById(video.getUrl()).getFileKey());
                  coverAuditResponse = imageAuditService.audit(QiNiuConfig.CNAME+"/"+fileService.getById(video.getCover()).getFileKey());
            }else if (videoTask.getNewState()){
                // 视频被设置为私密，则删除redis的视频标签库中该视频id
                interestPushService.deleteSystemStockIn(video1);
                interestPushService.deleteSystemTypeStockIn(video1);
                // 删除发件箱以及收件箱
                final Collection<Long> fans = followService.getFans(video.getUserId(), null);
                feedService.deleteOutBoxFeed(video.getUserId(),fans,video.getId());
            }

            // 新老视频标题、简介一致
            final Video oldVideo = videoTask.getOldVideo();
            if (oldVideo != null){
                if (!video.getTitle().equals(oldVideo.getTitle())) {
                    titleAuditResponse = textAuditService.audit(video.getTitle());
                }
                if (!video.getDescription().equals(oldVideo.getDescription()) && !ObjectUtils.isEmpty(video.getDescription())){
                    descAuditResponse = textAuditService.audit(video.getDescription());
                }
            }else{
                titleAuditResponse = textAuditService.audit(video.getTitle());
                descAuditResponse = textAuditService.audit(video.getDescription());
            }

            final Integer videoAuditStatus = videoAuditResponse.getAuditStatus();
            final Integer coverAuditStatus = coverAuditResponse.getAuditStatus();
            final Integer titleAuditStatus = titleAuditResponse.getAuditStatus();
            final Integer descAuditStatus = descAuditResponse.getAuditStatus();
            // 审核通过：必须都审核通过
            boolean f1 = videoAuditStatus == AuditStatus.SUCCESS;
            boolean f2 = coverAuditStatus == AuditStatus.SUCCESS;
            boolean f3 = titleAuditStatus == AuditStatus.SUCCESS;
            boolean f4 = descAuditStatus == AuditStatus.SUCCESS;
            // 审核违规：只要有一个违规 则视频违规
            boolean e1 = videoAuditStatus == AuditStatus.PASS;
            boolean e2 = coverAuditStatus == AuditStatus.PASS;
            boolean e3 = titleAuditStatus == AuditStatus.PASS;
            boolean e4 = descAuditStatus == AuditStatus.PASS;

            if (f1 && f2 && f3 && f4) {
                video1.setMsg("通过");
                video1.setAuditStatus(AuditStatus.SUCCESS);
                // 填充视频时长
            }else if(e1 || e2 || e3 || e4){
                video1.setAuditStatus(AuditStatus.PASS);
                // 避免干扰
                video1.setMsg("");
                if (!f1){
                    video1.setMsg("视频有违规行为: "+videoAuditResponse.getMsg());
                }
                if (!f2){
                    video1.setMsg(video1.getMsg()+"\n封面有违规行为: " + coverAuditResponse.getMsg());
                }
                if (!f3){
                    video1.setMsg(video1.getMsg()+"\n标题有违规行为: " + titleAuditResponse.getMsg());
                }
                if (!f4){
                    video1.setMsg(video1.getMsg()+"\n简介有违规行为: " + descAuditResponse.getMsg());
                }
                // 这里应该先判断是否违规.违规则下架视频，从系统库和标签库中删除视频，不执行后续流程
                // 违规，则删除文件表、视频表、七牛云buket中的视频
                Video v = videoMapper.selectById(video1);
                File fVideo = fileMapper.selectById(v.getUrl());
                File fImage = fileMapper.selectById(v.getCover());
                fileMapper.deleteById(fVideo);      // 删除视频
                fileMapper.deleteById(fImage);      // 删除封面
                videoMapper.deleteById(video1);
                // 七牛云删除视频文件
                qiNiuFileService.deleteFile(fVideo.getFileKey());
                // 七牛云删除封面文件
                qiNiuFileService.deleteFile(fImage.getFileKey());
                return;
            }else{
                // 人工复审，将视频状态设置为审核中即可
                video1.setMsg("待人工复审");
                video1.setAuditStatus(AuditStatus.PROCESS);
                return;
            }

            interestPushService.pushSystemTypeStockIn(video1);
            interestPushService.pushSystemStockIn(video1);

            // 推入发件箱
            feedService.pusOutBoxFeed(video.getUserId(),video.getId(),video1.getGmtCreated().getTime());
            videoMapper.updateById(video1);
        });

        return null;
    }

    // 判断当前执行器（executor）中的任务数量是否小于最大池大小（maximumPoolSize）
    public boolean getAuditQueueState(){
        return executor.getTaskCount() < maximumPoolSize;
    }

    // afterPropertiesSet() 方法用于在 Spring 容器完成对 bean 的属性注入之后执行自定义的初始化逻辑。
    @Override
    public void afterPropertiesSet() throws Exception {
        executor  = new ThreadPoolExecutor(5, maximumPoolSize, 60, TimeUnit.SECONDS, new ArrayBlockingQueue(1000));
    }
}
