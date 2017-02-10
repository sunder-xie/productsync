package com.ymatou.productsync.infrastructure.util;

import com.ymatou.messagebus.client.KafkaBusClient;
import com.ymatou.messagebus.client.Message;
import com.ymatou.messagebus.client.MessageBusException;
import com.ymatou.productsync.infrastructure.constants.Constants;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.UUID;

/**
 * Created by chenfei on 2017/2/9.
 * 消息总线分发
 */
@Component("messageBusDispatcher")
public class MessageBusDispatcher {

    /**
     * 消息总线客户端
     */
    @Resource
    private KafkaBusClient kafkaBusClient;

    /**
     *
     * @param activityId
     * @param productId
     * @param actionType
     * @throws MessageBusException
     */
    public void PublishAsync(long activityId,String productId, String actionType) throws MessageBusException {
        Message req = new Message();
        req.setAppId(Constants.SNAPSHOP_MQ_ID);
        req.setCode(Constants.SNAPSHOP_MQ_CODE);
        req.setMessageId(UUID.randomUUID().toString());
        req.setBody(new MessageBusInfo() {{
            setAppId(Constants.APP_ID);
            setProductId(productId);
            setActionType(actionType);
        }});
        kafkaBusClient.sendMessage(req);
    }

    /**
     * 消息总线信息格式
     */
    class MessageBusInfo {
        /**
         * appId
         */
        private String appId;

        /**
         * 商品id
         */
        private String productId;

        /**
         * 直播id
         */
        private long activityId;

        /**
         * 业务类型
         */
        private String actionType;

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getProductId() {
            return productId;
        }

        public void setProductId(String productId) {
            this.productId = productId;
        }

        public long getActivityId() {
            return activityId;
        }

        public void setActivityId(long activityId) {
            this.activityId = activityId;
        }

        public String getActionType() {
            return actionType;
        }

        public void setActionType(String actionType) {
            this.actionType = actionType;
        }
    }
}

