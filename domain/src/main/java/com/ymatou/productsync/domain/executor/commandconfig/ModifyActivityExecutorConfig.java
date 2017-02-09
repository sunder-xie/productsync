package com.ymatou.productsync.domain.executor.commandconfig;

import com.ymatou.productsync.domain.executor.CmdTypeEnum;
import com.ymatou.productsync.domain.executor.ExecutorConfig;
import com.ymatou.productsync.domain.executor.MongoDataCreator;
import com.ymatou.productsync.domain.executor.MongoQueryCreator;
import com.ymatou.productsync.domain.model.MongoData;
import com.ymatou.productsync.domain.sqlrepo.CommandQuery;
import com.ymatou.productsync.domain.sqlrepo.LiveCommandQuery;
import com.ymatou.productsync.infrastructure.util.MapUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Created by chenfei on 2017/2/8.
 * 修改直播
 */
@Component("modifyActivityExecutorConfig")
public class ModifyActivityExecutorConfig implements ExecutorConfig {

    @Autowired
    private CommandQuery commandQuery;
    @Autowired
    private LiveCommandQuery liveCommandQuery;

    @Override
    public CmdTypeEnum getCommand() {
        return CmdTypeEnum.ModifyActivity;
    }

    @Override
    public List<MongoData> loadSourceData(long activityId, String productId) {
        List<MongoData> mongoDataList = new ArrayList<>();
        ///1.直播数据更新
        List<Map<String, Object>> mapList = liveCommandQuery.getActivityInfo(activityId);
        //处理brands,country,flag
        if (mapList != null && !mapList.isEmpty()) {
            MapUtil.MapFieldToStringArray(mapList, "brands", ",");
            Map<String, Object> activity = mapList.get(0);
            int countryId = Integer.parseInt(activity.get("iCountryId").toString());
            activity.remove("iCountryId");
            Optional<Map<String, Object>> country = liveCommandQuery.getCountryInfo(countryId).stream().findFirst();
            if (country.isPresent()) {
                Map<String, Object> con = country.get();
                activity.put("country", con.get("sCountryNameZh"));
                activity.put("flag", con.get("sFlag"));
            }
        }
        //设置要更新的数据
        MongoData liveMongoData = MongoDataCreator.CreateLiveUpdate(MongoQueryCreator.CreateLiveId(activityId), mapList);
        mongoDataList.add(liveMongoData);

        ///2.直播商品数据更新 -- fixme: 123级分类需要处理
        List<Map<String, Object>> liveProductMapList = commandQuery.getLiveProductByActivityId(activityId);
        List<Map<String, Object>> productMapList = commandQuery.getProductNewTimeByActivityId(activityId);
        if (liveProductMapList != null) {
            liveProductMapList.parallelStream().forEach(liveProductItem -> {
                Object pid = liveProductItem.get("spid");
                Map<String, Object> pidCondition = MongoQueryCreator.CreateProductId(pid.toString());
                MongoData liveProductMongoData = MongoDataCreator.CreateLiveProductUpdate(pidCondition, MapUtil.MapToList(liveProductItem));

                ///2.商品数据更新
                if (productMapList != null && productMapList.parallelStream().anyMatch(p -> p.containsValue(pid))) {
                    List<Map<String, Object>> productData = productMapList.parallelStream().filter(p -> p.containsValue(pid)).collect(Collectors.toList());
                    MongoData productMongoData = MongoDataCreator.CreateProductUpdate(pidCondition, productData);
                    mongoDataList.add(productMongoData);
                }

                mongoDataList.add(liveProductMongoData);
            });
        }
        return mongoDataList;
    }
}