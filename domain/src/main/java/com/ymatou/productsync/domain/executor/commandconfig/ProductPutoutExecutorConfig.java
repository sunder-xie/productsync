package com.ymatou.productsync.domain.executor.commandconfig;

import com.ymatou.messagebus.client.MessageBusException;
import com.ymatou.productsync.domain.executor.CmdTypeEnum;
import com.ymatou.productsync.domain.executor.ExecutorConfig;
import com.ymatou.productsync.domain.executor.MongoDataBuilder;
import com.ymatou.productsync.domain.executor.MongoQueryBuilder;
import com.ymatou.productsync.domain.model.MongoData;
import com.ymatou.productsync.domain.sqlrepo.CommandQuery;
import com.ymatou.productsync.infrastructure.constants.Constants;
import com.ymatou.sellerquery.facade.OrderProductInfoFacade;
import com.ymatou.sellerquery.facade.model.req.GetOrderProductAmountInfosReq;
import com.ymatou.sellerquery.facade.model.resp.GetOrderProductAmountInfosResp;
import com.ymatou.sellerquery.facade.model.vo.OrderProductAmountInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by chenfei on 2017/2/8.
 * 商品下架
 */
@Component("productPutoutExecutorConfig")
public class ProductPutoutExecutorConfig implements ExecutorConfig {

    @Autowired
    private CommandQuery commandQuery;

    @Resource
    private OrderProductInfoFacade orderProductInfoFacade;

    @Override
    public CmdTypeEnum getCommand() {
        return CmdTypeEnum.ProductPutout;
    }

    @Override
    public List<MongoData> loadSourceData(long activityId, String productId) throws MessageBusException {
        List<MongoData> mongoDataList = new ArrayList<>();
        if (activityId <= 0) {
            List<Map<String, Object>> deleteProducts = commandQuery.getDeleteProducts(productId);
            if (deleteProducts != null && !deleteProducts.isEmpty()) {
                //更新商品
                MongoData productMd = MongoDataBuilder.createProductUpdate(MongoQueryBuilder.queryProductId(productId), deleteProducts);
                mongoDataList.add(productMd);
                //删除直播商品
                Map<String, Object> matchConditionInfo = new HashMap();
                matchConditionInfo.put("spid", productId);
                //fixme:matchConditionInfo.put("end",now); <
                MongoData liveProductMd = MongoDataBuilder.createLiveProductDelete(matchConditionInfo, null);
                mongoDataList.add(liveProductMd);
            }
        } else {
            //是否下过单，调订单接口
            int productOrderCount = 0;
            List<Map<String, Object>> userIdSource = commandQuery.getProductUser(productId);
            if (userIdSource != null && !userIdSource.isEmpty()) {
                Optional<Map<String, Object>> sellerMap = userIdSource.stream().findFirst();
                if(sellerMap==null || !sellerMap.isPresent())
                    return mongoDataList;
                //fixme:error
                GetOrderProductAmountInfosResp respOrderAmount = orderProductInfoFacade.getOrderProductAmountInfos(new GetOrderProductAmountInfosReq() {{
                    setProductIds(new ArrayList<String>() {{
                        add(productId);
                    }});
                    setSellerId(Long.parseLong(sellerMap.get().toString()));
                    //setBeginTime();
                }});
                if (respOrderAmount != null && respOrderAmount.isSuccess()) {
                    HashMap<String, OrderProductAmountInfo> orderAmountMap = respOrderAmount.getAmountInfos();
                    if (orderAmountMap.get(productId) != null)
                        productOrderCount = orderAmountMap.get(productId).getPaid();
                }

                //商品下过单更新状态，否则下架后删除Mongo数据
                if (productOrderCount > 0) {
                    //直播商品更新-istop,status
                    List<Map<String, Object>> productTop = commandQuery.getLiveProductTop(productId, activityId);
                    mongoDataList.add(MongoDataBuilder.createLiveProductUpdate(MongoQueryBuilder.queryProductIdAndLiveId(productId, activityId), productTop));
                } else {
                    //删除商品相关Mongo数据
                    mongoDataList.add(MongoDataBuilder.createDelete(Constants.ProductDb, MongoQueryBuilder.queryProductId(productId)));
                    mongoDataList.add(MongoDataBuilder.createDelete(Constants.LiveProudctDb, MongoQueryBuilder.queryProductId(productId)));
                    mongoDataList.add(MongoDataBuilder.createDelete(Constants.ProductDescriptionDb, MongoQueryBuilder.queryProductId(productId)));
                    mongoDataList.add(MongoDataBuilder.createDelete(Constants.CatalogDb, MongoQueryBuilder.queryProductId(productId)));
                }
            }
        }
        return mongoDataList;
    }
}
