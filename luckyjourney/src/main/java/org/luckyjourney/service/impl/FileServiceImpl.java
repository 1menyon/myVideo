package org.luckyjourney.service.impl;

import com.qiniu.storage.model.FileInfo;
import org.luckyjourney.config.LocalCache;
import org.luckyjourney.config.QiNiuConfig;
import org.luckyjourney.entity.File;
import org.luckyjourney.exception.BaseException;
import org.luckyjourney.mapper.FileMapper;
import org.luckyjourney.service.FileService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.luckyjourney.service.QiNiuFileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author menyon
 * @since 2023-11-20
 */
@Service
public class FileServiceImpl extends ServiceImpl<FileMapper, File> implements FileService {

    @Autowired
    private QiNiuFileService qiNiuFileService;

    @Override
    public Long save(String fileKey,Long userId) {

        // 判断文件
        final FileInfo videoFileInfo = qiNiuFileService.getFileInfo(fileKey);

        if (videoFileInfo == null){
            throw new IllegalArgumentException("参数不正确");
        }

        final File videoFile = new File();
        String type = videoFileInfo.mimeType;
        videoFile.setFileKey(fileKey);
        videoFile.setFormat(type);
        videoFile.setType(type.contains("video") ? "视频" : "图片");
        videoFile.setUserId(userId);
        videoFile.setSize(videoFileInfo.fsize);
        save(videoFile);

        return videoFile.getId();
    }

    /**
     *   生成视频封面 保存到mysql的文件表 返回封面的url
     * */
    @Override
    public Long generatePhoto(Long fileId,Long userId) {
        final File file = getById(fileId);;
        final String fileKey = file.getFileKey() + "?vframe/jpg/offset/1";
        final File fileInfo = new File();
        fileInfo.setFileKey(fileKey);
        fileInfo.setFormat("image/*");
        fileInfo.setType("图片");
        fileInfo.setUserId(userId);
        save(fileInfo);
        return fileInfo.getId();
    }
    /**
     *  fileId一般就是videoId、imageId（视频id/封面图片id）
     *  从mysql的file表中 拿到对应文件的fileKey
     * */
    @Override
    public File getFileTrustUrl(Long fileId) {
        File file = getById(fileId);
        if (Objects.isNull(file)) {
            throw new BaseException("未找到该文件");
        }
        final String s = UUID.randomUUID().toString();
        LocalCache.put(s,true);
        // 重定向的地址格式：七牛云的cdn测试域名/文件的fileKey?uuid=********
        String url = QiNiuConfig.CNAME + "/" + file.getFileKey();

        if (url.contains("?")){
            url = url+"&uuid="+s;
        }else {
            url = url+"?uuid="+s;
        }
        file.setFileKey(url);
        return file;
    }
}
