package com.ymatou.productsync.domain.executor.commandconfig;

import com.ymatou.productsync.domain.executor.CmdTypeEnum;
import com.ymatou.productsync.domain.executor.ExecutorConfig;
import com.ymatou.productsync.domain.model.mongo.MongoData;
import com.ymatou.productsync.domain.model.mongo.MongoDataBuilder;
import com.ymatou.productsync.domain.model.mongo.MongoQueryBuilder;
import com.ymatou.productsync.domain.model.sql.SyncStatusEnum;
import com.ymatou.productsync.domain.sqlrepo.CommandQuery;
import com.ymatou.productsync.facade.model.BizException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by zhangyong on 2017/2/9.
 */
@Component("autoOnShelfProductExecutorConfig")
public class AutoOnShelfProductExecutorConfig implements ExecutorConfig {
    @Autowired
    private CommandQuery commandQuery;

    @Override
    public CmdTypeEnum getCommand() {
        return CmdTypeEnum.AutoOnShelf;
    }

    public List<MongoData> loadSourceData(long activityId, String productId) {
        List<MongoData> mongoDataList = new ArrayList<>();
        List<Map<String, Object>> sqlDataList = commandQuery.getProductTime(productId);
        if (sqlDataList == null || sqlDataList.isEmpty()) {
            throw new BizException(SyncStatusEnum.BizEXCEPTION.getCode(), "getProductTime 为空");
        }
        mongoDataList.add(MongoDataBuilder.createProductUpdate(MongoQueryBuilder.queryProductId(productId), sqlDataList));

        return mongoDataList;
    }
}

