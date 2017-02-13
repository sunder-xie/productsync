package com.ymatou.productsync.facade;

import com.alibaba.dubbo.config.annotation.Service;
import com.ymatou.messagebus.client.MessageBusException;
import com.ymatou.productsync.domain.executor.*;
import com.ymatou.productsync.facade.model.BizException;
import com.ymatou.productsync.facade.model.ErrorCode;
import com.ymatou.productsync.facade.model.req.SyncByCommandReq;
import com.ymatou.productsync.facade.model.resp.BaseResponse;
import com.ymatou.productsync.infrastructure.util.MessageBusDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * 商品同步业务场景
 * 同时支持http rpc
 * Created by chenpengxuan on 2017/1/19.
 */
@Service(protocol = {"rest", "dubbo"})
@Component
@Path("/{api:(?i:api)}")
public class SyncByCommandFacadeImpl implements SyncCommandFacade {

    private static final Logger DEFAULT_LOGGER = LoggerFactory.getLogger(SyncByCommandFacadeImpl.class);
    /**
     * 业务指令器工厂
     */
    @Autowired
    private ExecutorConfigFactory executorConfigFactory;

    /**
     * 同步执行器
     */
    @Autowired
    private CommandExecutor executor;

    /**
     * 消息总线客户端
     */
    @Autowired
    private MessageBusDispatcher messageBusDispatcher;

    /**
     * 根据业务场景指令同步相关信息
     *
     * @param req 基于业务场景的请求
     * @return
     */
    @POST
    @Path("/{cache:(?i:cache)}/{invokemongocrud:(?i:invokemongocrud)}")
    @Override
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public BaseResponse syncByCommand(SyncByCommandReq req) {
        ExecutorConfig config = executorConfigFactory.getCommand(req.getActionType());
        if (config == null) {
            //参数错误，无需MQ重试
            executor.updateTransactionInfo(req.getTransactionId(), SyncStatusEnum.IllegalArgEXCEPTION);
            DEFAULT_LOGGER.info(String.format("发生业务指令异常，异常原因为：ProductId:%s,LiveId:%d,ActionType:%s,TransactionId:%d", req.getProductId(), req.getActivityId(), req.getActionType(), req.getTransactionId()));
            BaseResponse response = BaseResponse.newSuccessInstance();
            response.setMessage("没有对应场景，场景指令不正确");
            return response;
        }
        //执行成功的并且是商品相关操作
        if (executor.executorCommand(req, config) && CmdTypeEnum.valueOf(req.getActionType()).ordinal() < CmdTypeEnum.AddActivity.ordinal()) {
            try {
                messageBusDispatcher.PublishAsync(req.getProductId(), req.getActionType());
                return BaseResponse.newSuccessInstance();
            } catch (MessageBusException e) {
                executor.updateTransactionInfo(req.getTransactionId(), SyncStatusEnum.SUCCESS);
                //目前商品部分业务相关指令消息的分发只是针对商品快照，如果发生消息总线异常，则只是记录到异常日志
                DEFAULT_LOGGER.error(String.format("同步服务发送消息发生异常,transactionId为%d,productId为%s,actionType为%ss",
                        req.getTransactionId(), req.getProductId(), req.getActionType()), e);
                return BaseResponse.newSuccessInstance();
            } catch (IllegalArgumentException argExceptin) {
                executor.updateTransactionInfo(req.getTransactionId(), SyncStatusEnum.IllegalArgEXCEPTION);
                DEFAULT_LOGGER.info(String.format("发生业务参数级异常，异常原因为：ProductId:%s,LiveId:%d,ActionType:%s,TransactionId:%d,%s", req.getProductId(), req.getActivityId(), req.getActionType(), req.getTransactionId(), argExceptin.getMessage()));
                return BaseResponse.newSuccessInstance();
            } catch (BizException bizException) {
                executor.updateTransactionInfo(req.getTransactionId(), SyncStatusEnum.BizEXCEPTION);
                DEFAULT_LOGGER.error(String.format("发生业务级异常，异常原因为：ProductId:%s,LiveId:%d,ActionType:%s,TransactionId:%d,%s", req.getProductId(), req.getActivityId(), req.getActionType(), req.getTransactionId(), bizException.getMessage()));
                BaseResponse.newFailInstance(ErrorCode.BIZFAIL);
            }
        }
        return BaseResponse.newFailInstance(ErrorCode.FAIL);
    }
}
