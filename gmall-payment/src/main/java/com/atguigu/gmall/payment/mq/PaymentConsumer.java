package com.atguigu.gmall.payment.mq;

import com.alibaba.dubbo.config.annotation.Reference;
import com.atguigu.gmall.bean.PaymentInfo;
import com.atguigu.gmall.service.PaymentService;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import javax.jms.JMSException;
import javax.jms.MapMessage;

@Component
public class PaymentConsumer {

    @Reference
    private PaymentService paymentService;

    @JmsListener(destination = "PAYMENT_RESULT_CHECK_QUEUE",containerFactory = "jmsQueueListener")
    public void consumeSkuDeduct(MapMessage mapMessage){
        try {
            String outTradeNo = mapMessage.getString("outTradeNo");
            int delaySec = mapMessage.getInt("delaySec");
            int checkCount = mapMessage.getInt("checkCount");

            PaymentInfo paymentInfo = new PaymentInfo();
            paymentInfo.setOutTradeNo(outTradeNo);
            PaymentInfo paymentInfoQuery = paymentService.getPaymentInfo(paymentInfo);
            //调用检查是否支付的方法
            boolean result = paymentService.checkPayment(paymentInfoQuery);
            System.out.println("检查结果为："+result);

            if (!result && checkCount > 0){
                System.out.println("检查次数:"+checkCount);
                //继续检查
                paymentService.sendDelayPaymentResult(outTradeNo,delaySec,checkCount-1);
            }

        } catch (JMSException e) {
            e.printStackTrace();
        }
    }
}
