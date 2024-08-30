package org.luckyjourney.util;

import ws.schild.jave.MultimediaObject;
import ws.schild.jave.info.MultimediaInfo;

import java.net.URL;

/**
 * @description:
 * @Author: menyon
 * @CreateTime: 2023-11-01 23:09
 */
public class FileUtil {

    public static String getFormat(String fileName){
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }
    /**
     * 获取视频时长
     * @param fileUrl 网络url
     * @return
     */
    public static String getVideoDuration(String fileUrl) {
        String[] length = new String[2];
        try {
            // 这个 URL 对象指向网络上的视频文件。
            URL source = new URL(fileUrl);
            // 通过 MultimediaObject 的构造函数，传入视频文件的 URL，用于解析和处理视频文件。
            MultimediaObject instance = new MultimediaObject(source);
            // getInfo() 方法返回 MultimediaInfo 对象，其中包含视频的时长、格式等信息。
            MultimediaInfo result = instance.getInfo();
            Long ls = result.getDuration() / 1000;
            length[0] = String.valueOf(ls);
            Integer hour = (int) (ls / 3600);
            Integer minute = (int) (ls % 3600) / 60;
            Integer second = (int) (ls - hour * 3600 - minute * 60);
            String hr = hour.toString();
            String mi = minute.toString();
            String se = second.toString();
            if (hr.length() < 2) {
                hr = "0" + hr;
            }
            if (mi.length() < 2) {
                mi = "0" + mi;
            }
            if (se.length() < 2) {
                se = "0" + se;
            }

            String noHour = "00";
            if (noHour.equals(hr)) {
                length[1] = mi + ":" + se;
            } else {
                length[1] = hr + ":" + mi + ":" + se;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return length[1];
    }
}
