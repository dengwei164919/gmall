package com.atguigu.gmall.order.task;

import com.alibaba.dubbo.config.annotation.Reference;
import com.atguigu.gmall.bean.OrderInfo;
import com.atguigu.gmall.service.OrderService;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@EnableScheduling //开启定时任务
public class OrderTask {

    @Reference
    private OrderService orderService;

    //设置每20秒扫描一次 分时日月周   年为可选,用问号表示
    @Scheduled(cron = "0/20 * * * * ?")
    public void checkOrder(){
        List<OrderInfo> orderInfoList = orderService.getExpiredOrderList();

        if (orderInfoList != null && orderInfoList.size() > 0){
            for (OrderInfo orderInfo : orderInfoList) {
                orderService.execExpiredOrder(orderInfo);
            }
        }
    }
}
