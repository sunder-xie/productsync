package com.ymatou.productsync.domain.executor;

import com.ymatou.productsync.domain.model.MongoData;

import java.util.List;

/**
 * Created by chenpengxuan on 2017/1/19.
 */
public interface ExecutorConfig {
    /**
     * 获取指令类型
     *
     * @return
     */
    ProductCmdTypeEnum getCommand();

    /**
     * 获取待同步数据
     * @param activityId
     * @param productId
     * @return
     */
    List<MongoData> loadSourceData(long activityId, String productId);
}
