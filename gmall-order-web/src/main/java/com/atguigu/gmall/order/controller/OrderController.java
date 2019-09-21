package com.atguigu.gmall.order.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.CartInfo;
import com.atguigu.gmall.bean.OrderDetail;
import com.atguigu.gmall.bean.OrderInfo;
import com.atguigu.gmall.bean.UserAddress;
import com.atguigu.gmall.config.LoginRequire;
import com.atguigu.gmall.service.CartService;
import com.atguigu.gmall.service.OrderService;
import com.atguigu.gmall.service.UserInfoService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class OrderController {

    @Reference
    private UserInfoService userInfoService;

    @Reference
    private CartService cartService;

    @Reference
    private OrderService orderService;

    @RequestMapping("trade")
    @LoginRequire(autoRedirect = true)
    public String trade(HttpServletRequest request){

        String userId = (String) request.getAttribute("userId");

        //获取选中的购物车列表
        List<CartInfo> cartCheckedList = cartService.getCartCheckedList(userId);

        //获取地址
        List<UserAddress> userAddressList = userInfoService.getUserAddressByUserId(userId);
        request.setAttribute("userAddressList",userAddressList);
        //订单信息
        List<OrderDetail> orderDetailList = new ArrayList<>();
        if (cartCheckedList != null && cartCheckedList.size() > 0){
            for (CartInfo cartInfo : cartCheckedList) {
                OrderDetail orderDetail = new OrderDetail();
                orderDetail.setSkuId(cartInfo.getSkuId());
                orderDetail.setSkuName(cartInfo.getSkuName());
                orderDetail.setImgUrl(cartInfo.getImgUrl());
                orderDetail.setSkuNum(cartInfo.getSkuNum());
                orderDetail.setOrderPrice(cartInfo.getCartPrice());
                orderDetailList.add(orderDetail);
            }
        }

        request.setAttribute("orderDetailList",orderDetailList);

        //总金额
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderDetailList(orderDetailList);
        orderInfo.sumTotalAmount();

        request.setAttribute("totalAmount",orderInfo.getTotalAmount());
        //将流水号设置到页面中
        String tradeNo = orderService.getTradeNo(userId);

        request.setAttribute("tradeNo",tradeNo);

        return "trade";

    }


    @RequestMapping("submitOrder")
    @LoginRequire
    public String submitOrder(OrderInfo orderInfo,HttpServletRequest request){

        String userId = (String) request.getAttribute("userId");
        String tradeNo = request.getParameter("tradeNo");

        //判断是否是重复提交
        boolean result = orderService.checkTradeCode(userId, tradeNo);
        if (!result){
            request.setAttribute("errMsg","不能重复提交订单！");
            return "tradeFail";
        }

//        验证库存是否足够
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        for (OrderDetail orderDetail : orderDetailList) {
            boolean res = orderService.checkStock(orderDetail.getSkuId(),orderDetail.getSkuNum());

            if (!res){
                // 验证库存失败！
                request.setAttribute("errMsg",orderDetail.getSkuName()+"库存不足！");
                return "tradeFail";
            }
        }

        //保存数据
        String orderId = orderService.saveOrder(orderInfo);

        //删除流水号
        orderService.delTradeCode(userId);
        return "redirect://payment.gmall.com/index?orderId="+orderId;
    }

    //拆单
    @RequestMapping("orderSplit")
    @ResponseBody
    public String orderSplit(HttpServletRequest request){
        String orderId = request.getParameter("orderId");
        String wareSkuMap = request.getParameter("wareSkuMap");
        List<OrderInfo> subOrderInfoList = orderService.orderSplit(orderId,wareSkuMap);

        ArrayList<Map> maps = new ArrayList<>();
        if (subOrderInfoList != null && subOrderInfoList.size() > 0){
            for (OrderInfo orderInfo : subOrderInfoList) {
                Map map = orderService.initWareOrder(orderInfo);
                maps.add(map);
            }
        }
        return JSON.toJSONString(maps);
    }


}
