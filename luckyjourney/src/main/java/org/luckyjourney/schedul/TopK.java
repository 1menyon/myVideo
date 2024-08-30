package org.luckyjourney.schedul;

import org.luckyjourney.entity.video.Video;
import org.luckyjourney.entity.vo.HotVideo;

import java.util.*;

/**
*
 * @description:
 * @Author: menyon
 * @CreateTime: 2023-11-04 17:42

*/
public class TopK {

    private int k = 0;

    private Queue<HotVideo> queue;

    public TopK(int k,Queue<HotVideo> queue){
        this.k = k;
        this.queue = queue;
    }
    // 使用的是最小堆（PriorityQueue 默认的行为），那么在升序排列的情况下，peek 和 poll 方法都将返回最小的元素。
    public void add(HotVideo hotVideo) {
        if (queue.size() < k) {
            queue.add(hotVideo);
        } else if (queue.peek().getHot() < hotVideo.getHot()){
            queue.poll();
            queue.add(hotVideo);
        }

        return;
    }


    public List<HotVideo> get(){
        final ArrayList<HotVideo> list = new ArrayList<>(queue.size());
        while (!queue.isEmpty()) {
            list.add(queue.poll());
        }
        Collections.reverse(list);      // 最先取出来的最小，所以反转
        return list;
    }


}
