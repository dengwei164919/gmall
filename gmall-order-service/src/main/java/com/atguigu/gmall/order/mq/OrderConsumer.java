package com.atguigu.gmall.order.mq;

import com.atguigu.gmall.bean.enums.ProcessStatus;
import com.atguigu.gmall.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import javax.jms.JMSException;
import javax.jms.MapMessage;

@Component
public class OrderConsumer {

    @Autowired
    private OrderService orderService;

    @JmsListener(destination = "PAYMENT_RESULT_QUEUE",containerFactory = "jmsQueueListener")
    public void consumerPaymentResult(MapMessage mapMessage) throws JMSException {
        String orderId = mapMessage.getString("orderId");
        String result = mapMessage.getString("result");

        if ("success".equals(result)){
            //更改支付状态为已支付
            orderService.updateOrderStatus(orderId, ProcessStatus.PAID);

            //发送到库存模块减库存
            orderService.sendOrderStatus(orderId);

            //减完库存后更新订单状态为已通知仓储
            orderService.updateOrderStatus(orderId,ProcessStatus.NOTIFIED_WARE);
        }
    }

    @JmsListener(destination = "SKU_DEDUCT_QUEUE",containerFactory = "jmsQueueListener")
    public void consumeSkuDeduct(MapMessage mapMessage) throws JMSException {
        String orderId = mapMessage.getString("orderId");
        String status = mapMessage.getString("status");

        if ("DEDUCTED".equals(status)){
            //更新订单状态为已发货
            orderService.updateOrderStatus(orderId,ProcessStatus.DELEVERED);

        }
    }
}
