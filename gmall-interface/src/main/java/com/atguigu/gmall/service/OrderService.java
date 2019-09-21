package com.atguigu.gmall.service;

import com.atguigu.gmall.bean.OrderInfo;
import com.atguigu.gmall.bean.enums.ProcessStatus;

import java.util.List;
import java.util.Map;

public interface OrderService {

    String saveOrder(OrderInfo orderInfo);

    /**
     * 生成流水号用于防止订单重复提交
     * @param userId
     * @return
     */
    String getTradeNo(String userId);

    /**
     * 验证流水号。
     * @param userId
     * @param tradeNo
     * @return
     */
    boolean checkTradeCode(String userId,String tradeNo);

    /**
     * 删除redis中的流水号
     * @param userId
     */
    void delTradeCode(String userId);

    boolean checkStock(String skuId, Integer skuNum);

    OrderInfo getOrderInfo(String orderId);

    void updateOrderStatus(String orderId, ProcessStatus processStatus);

    void sendOrderStatus(String orderId);

    List<OrderInfo> getExpiredOrderList();

    void execExpiredOrder(OrderInfo orderInfo);

    Map initWareOrder(OrderInfo orderInfo);

    /**
     * 拆单
     * @param orderId
     * @param wareSkuMap
     * @return
     */
    List<OrderInfo> orderSplit(String orderId, String wareSkuMap);
}
