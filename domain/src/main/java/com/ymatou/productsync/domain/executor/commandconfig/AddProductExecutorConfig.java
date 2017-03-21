package com.ymatou.productsync.domain.executor.commandconfig;

import com.ymatou.productsync.domain.executor.CmdTypeEnum;
import com.ymatou.productsync.domain.executor.ExecutorConfig;
import com.ymatou.productsync.domain.model.mongo.MongoDataBuilder;
import com.ymatou.productsync.domain.model.mongo.MongoQueryBuilder;
import com.ymatou.productsync.domain.model.mongo.MongoData;
import com.ymatou.productsync.domain.model.mongo.ProductChangedRange;
import com.ymatou.productsync.domain.model.sql.SyncStatusEnum;
import com.ymatou.productsync.domain.mongorepo.MongoRepository;
import com.ymatou.productsync.domain.sqlrepo.CommandQuery;
import com.ymatou.productsync.domain.sqlrepo.LiveCommandQuery;
import com.ymatou.productsync.facade.model.BizException;
import com.ymatou.productsync.infrastructure.constants.Constants;
import com.ymatou.productsync.infrastructure.util.MapUtil;
import com.ymatou.productsync.infrastructure.util.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 添加商品
 * Created by chenpengxuan on 2017/2/8.
 */
@Component("addProductExecutorConfig")
public class AddProductExecutorConfig implements ExecutorConfig {
    @Autowired
    private CommandQuery commandQuery;

    @Autowired
    private LiveCommandQuery liveCommandQuery;

    @Autowired
    private MongoRepository mongoRepository;

    @Override
    public CmdTypeEnum getCommand() {
        return CmdTypeEnum.AddProduct;
    }

    private static ProductChangedRange productChangedRange = new ProductChangedRange();

    private static List<String> productChangedTableNameList = new ArrayList<>();

    private static List<String> productIdList = new ArrayList<>();

    @Override
    public List<MongoData> loadSourceData(long activityId, String productId) throws BizException {
        productIdList.clear();
        productChangedTableNameList.clear();
        //商品信息
        List<Map<String, Object>> sqlProductDataList = commandQuery.getProductDetailInfo(productId);
        //商品规格信息
        List<Map<String, Object>> sqlCatalogDataList = commandQuery.getProductCatalogInfo(productId);
        //商品图文描述
        List<Map<String, Object>> sqlProductDescDataList = commandQuery.getProductDescInfo(productId);
        //商品直播信息
        List<Map<String, Object>> sqlProductInLiveDataList = commandQuery.getProductLiveInfo(activityId, productId);
        //直播信息
        List<Map<String, Object>> sqlLiveDataList = liveCommandQuery.getProductInfoByActivityId(activityId);

        //前置条件检查
        if (sqlProductDataList == null || sqlProductDataList.isEmpty()) {
            throw new BizException(SyncStatusEnum.BizEXCEPTION.getCode(), "getProductDetailInfo为空");
        }
        if (sqlCatalogDataList == null || sqlCatalogDataList.isEmpty()) {
            throw new BizException(SyncStatusEnum.BizEXCEPTION.getCode(), "getProductCatalogInfo为空");
        }
        if (activityId > 0 && (sqlProductInLiveDataList == null || sqlProductInLiveDataList.isEmpty())) {
            throw new BizException(SyncStatusEnum.BizEXCEPTION.getCode(), "getProductLiveInfo为空");
        }
        if (activityId > 0 && (sqlLiveDataList == null || sqlLiveDataList.isEmpty())) {
            throw new BizException(SyncStatusEnum.BizEXCEPTION.getCode(), "getProductInfoByActivityId为空");
        }

        List<MongoData> mongoDataList = new ArrayList<>();

        //创建商品信息
        MapUtil.mapFieldToStringArray(sqlProductDataList, "pics", ",");
        Map<String, Object> tempProductDataMap = sqlProductDataList.stream().findFirst().orElse(Collections.emptyMap());
        tempProductDataMap.replace("newdesc", tempProductDataMap.get("newdesc"), ( tempProductDataMap.get("newdesc") != null ? (int)tempProductDataMap.get("newdesc") : 0) == 1);
        //针对添加商品进直播的情况不能覆盖版本号,如果商品已经存在的话，则不更新商品快照信息
        if (mongoRepository.queryMongo(MongoDataBuilder.querySingleProductInfo(MongoQueryBuilder.queryProductId(productId)))
                .stream().findFirst().orElse(Collections.emptyMap()).isEmpty()) {
            sqlProductDataList.stream().findFirst().orElse(Collections.emptyMap()).put("ver", "1001");
            sqlProductDataList.stream().findFirst().orElse(Collections.emptyMap()).put("verupdate", Utils.getNow());
        }
        mongoDataList.add(MongoDataBuilder.createProductUpsert(MongoQueryBuilder.queryProductId(productId), sqlProductDataList));

        //创建规格信息 先删除再更新
        mongoDataList.add(MongoDataBuilder.createCatalogDelete(MongoQueryBuilder.queryProductId(productId), null));
        mongoDataList.add(MongoDataBuilder.createCatalogAdd(MapUtil.mapFieldArrayToNestedObj(sqlCatalogDataList, new String[]{"name", "pic", "value"}, "props", "cid")));

        //创建商品图文描述信息
        if (sqlProductDescDataList != null && !sqlProductDescDataList.isEmpty()) {
            Map<String, Object> tempDescMap = new HashMap<>();
            tempDescMap.putAll(sqlProductDescDataList.stream().findFirst().orElse(Collections.emptyMap()));
            tempDescMap.remove("pic");
            tempDescMap.put("pics", sqlProductDescDataList.stream().map(x -> x.get("pic")).toArray());
            sqlProductDescDataList.clear();
            sqlProductDescDataList.add(tempDescMap);
            mongoDataList.add(MongoDataBuilder.createProductDescUpsert(MongoQueryBuilder.queryProductId(productId), sqlProductDescDataList));
        }

        //针对添加是商品进直播与直播中添加商品的场景
        if (activityId > 0) {
            //创建直播商品信息
            Map<String, Object> tempLiveProductMap = sqlProductInLiveDataList.stream().findFirst().orElse(Collections.emptyMap());
            Map<String, Object> productMap = sqlProductDataList.stream().findFirst().orElse(Collections.emptyMap());
            tempLiveProductMap.put("bid", productMap.get("bid"));
            tempLiveProductMap.put("mcatid", productMap.get("mcatid"));
            tempLiveProductMap.put("mcatname", productMap.get("mcatname"));
            tempLiveProductMap.put("scatid", productMap.get("scatid"));
            tempLiveProductMap.put("scatname", productMap.get("scatname"));
            tempLiveProductMap.put("tcatid", productMap.get("tcatid"));
            tempLiveProductMap.put("tcatname", productMap.get("tcatname"));
            tempLiveProductMap.put("brand", productMap.get("brand"));
            tempLiveProductMap.put("ebrand", productMap.get("ebrand"));
            tempLiveProductMap.put("comments", 0);
            mongoDataList.add(MongoDataBuilder.createProductLiveUpsert(MongoQueryBuilder.queryProductIdAndLiveId(productId, activityId), sqlProductInLiveDataList));

            //更新直播信息
            Object[] brands = sqlLiveDataList.stream().map(t -> t.get("sBrand")).distinct().toArray();
            sqlLiveDataList.clear();
            Map<String, Object> tempLiveMap = new HashMap<>();
            tempLiveMap.put("brands", brands);
            sqlLiveDataList.add(tempLiveMap);
            mongoDataList.add(MongoDataBuilder.createLiveUpsert(MongoQueryBuilder.queryLiveId(activityId), sqlLiveDataList));

            productChangedTableNameList.add(Constants.LiveProudctDb);
        }

        productIdList.add(productId);
        productChangedTableNameList.add(Constants.ProductDb);
        productChangedTableNameList.add(Constants.CatalogDb);
        productChangedRange.setProductIdList(productIdList);
        productChangedRange.setProductTableRangeList(productChangedTableNameList);
        return mongoDataList;
    }

    @Override
    public ProductChangedRange getProductChangeRangeInfo() {
        return productChangedRange;
    }
}
