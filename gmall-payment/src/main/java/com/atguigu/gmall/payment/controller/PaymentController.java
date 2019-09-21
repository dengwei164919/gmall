package com.atguigu.gmall.payment.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.atguigu.gmall.bean.OrderInfo;
import com.atguigu.gmall.bean.PaymentInfo;
import com.atguigu.gmall.bean.enums.PaymentStatus;
import com.atguigu.gmall.config.LoginRequire;
import com.atguigu.gmall.payment.config.AlipayConfig;
import com.atguigu.gmall.service.OrderService;
import com.atguigu.gmall.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.apache.catalina.manager.Constants.CHARSET;

@Controller
public class PaymentController {

    @Reference
    private OrderService orderService;

    @Autowired
    private AlipayClient alipayClient;

    @Reference
    private PaymentService paymentService;

    @RequestMapping("index")
    @LoginRequire(autoRedirect = true)
    public String index(HttpServletRequest request){

        String orderId = request.getParameter("orderId");

        OrderInfo orderInfo = orderService.getOrderInfo(orderId);

        request.setAttribute("totalAmount",orderInfo.getTotalAmount());
        request.setAttribute("orderId",orderId);

        return "index";
    }

    @RequestMapping("alipay/submit")
    @ResponseBody
    public String alipaySubmit(HttpServletRequest request, HttpServletResponse response){
        //从orderinfo中获取数据
        String orderId = request.getParameter("orderId");
        OrderInfo orderInfo = orderService.getOrderInfo(orderId);

        //保存数据到paymentinfo
        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setOrderId(orderId);
        paymentInfo.setOutTradeNo(orderInfo.getOutTradeNo());
        paymentInfo.setTotalAmount(orderInfo.getTotalAmount());
        paymentInfo.setSubject("买袜子--绿色的！");
        paymentInfo.setPaymentStatus(PaymentStatus.UNPAID);
        paymentInfo.setCreateTime(new Date());
        paymentService.savePaymentInfo(paymentInfo);

//        AlipayClient alipayClient = new DefaultAlipayClient("https://openapi.alipay.com/gateway.do", APP_ID, APP_PRIVATE_KEY, FORMAT, CHARSET, ALIPAY_PUBLIC_KEY, SIGN_TYPE); //获得初始化的AlipayClient
        AlipayTradePagePayRequest alipayRequest = new AlipayTradePagePayRequest();//创建API对应的request
        alipayRequest.setReturnUrl(AlipayConfig.return_payment_url);
        alipayRequest.setNotifyUrl(AlipayConfig.notify_payment_url);//在公共参数中设置回跳和通知地址
        HashMap<String, Object> map = new HashMap<>();

        //设置参数
        map.put("out_trade_no",paymentInfo.getOutTradeNo());
        map.put("product_code","FAST_INSTANT_TRADE_PAY");
        map.put("total_amount",paymentInfo.getTotalAmount());
        map.put("subject",paymentInfo.getSubject());

        alipayRequest.setBizContent(JSON.toJSONString(map));
//        alipayRequest.setBizContent("{" +
//                "    \"out_trade_no\":\"20150320010101001\"," +
//                "    \"product_code\":\"FAST_INSTANT_TRADE_PAY\"," +
//                "    \"total_amount\":88.88," +
//                "    \"subject\":\"Iphone6 16G\"," +
//                "    \"body\":\"Iphone6 16G\"," +
//                "    \"passback_params\":\"merchantBizType%3d3C%26merchantBizNo%3d2016010101111\"," +
//                "    \"extend_params\":{" +
//                "    \"sys_service_provider_id\":\"2088511833207846\"" +
//                "    }"+
//                "  }");//填充业务参数
        String form="";
        try {
            form = alipayClient.pageExecute(alipayRequest).getBody(); //调用SDK生成表单
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        response.setContentType("text/html;charset=" + CHARSET);
        paymentService.sendDelayPaymentResult(paymentInfo.getOutTradeNo(),15,3);
        return form;
    }

    //同步回调接口
    @RequestMapping("alipay/callback/return")
    public String callbackReturn(){
        //返回订单页面
        //清空购物车中被勾选项目,从redis中清空
        return "redirect:"+ AlipayConfig.return_order_url;
    }

    //异步回调接口
    @RequestMapping("alipay/callback/notify")
    public String callbackNotify(@RequestParam Map<String,String> paramMap, HttpServletRequest request){

        try {
            boolean flag = AlipaySignature.rsaCheckV1(paramMap, AlipayConfig.alipay_public_key, CHARSET, AlipayConfig.sign_type);
            //获取交易状态和交易编号
            String trade_status = paramMap.get("trade_status");
            String out_trade_no = paramMap.get("out_trade_no");

            if(flag){
                // TODO 验签成功后，按照支付结果异步通知中的描述，对支付结果中的业务内容进行二次校验，校验成功后在response中返回success并继续商户自身业务处理，校验失败返回failure
                //判断交易状态
                if ("TRADE_SUCCESS".equals(trade_status) || "TRADE_FINISHED".equals(trade_status)){
                    //如果是这两种状态表示支付成功
                    //根据outtradeno获取payment对象
                    PaymentInfo paymentInfoQuery = new PaymentInfo();
                    paymentInfoQuery.setOutTradeNo(out_trade_no);
                    PaymentInfo paymentInfo = paymentService.getPaymentInfo(paymentInfoQuery);

                    //判断订单状态，如果是已支付或是已关闭，则验签失败
                    if (paymentInfo.getPaymentStatus() == PaymentStatus.PAID || paymentInfo.getPaymentStatus() == PaymentStatus.ClOSED){
                        return "failure";
                    }

                    //如果订单验签成功，修改交易记录状态，更新数据库
                    PaymentInfo paymentInfoUPD = new PaymentInfo();
                    paymentInfoUPD.setPaymentStatus(PaymentStatus.PAID);
                    paymentInfoUPD.setCreateTime(new Date());
                    // 更新交易记录
                    paymentService.updatePaymentInfo(out_trade_no,paymentInfoUPD);
                    // 支付成功！订单状态变成支付！ 发送消息队列！
//                    paymentService.sendPaymentResult(paymentInfoQuery,"success");
                    return "success";
                }
            }else{
                // TODO 验签失败则记录异常日志，并在response中返回failure.
                return "failure";
            }
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        return "failure";
    }

    @RequestMapping("refund")
    @ResponseBody
    public String refund(String orderId){

        boolean result = paymentService.refund(orderId);
        return ""+result;
    }

    @RequestMapping("sendPaymentResult")
    @ResponseBody
    public String sendPaymentResult(PaymentInfo paymentInfo,@RequestParam("result") String result){
        paymentService.sendPaymentResult(paymentInfo,result);
        return "ok";
    }

    //查询当前交易是否已经付款
    @RequestMapping("queryPaymentResult")
    @ResponseBody
    public String queryPaymentResult(PaymentInfo paymentInfo){
        PaymentInfo paymentInfoQuery = paymentService.getPaymentInfo(paymentInfo);
        boolean result = false;
        if (paymentInfoQuery != null ){
            result = paymentService.checkPayment(paymentInfoQuery);
        }
        return ""+result;
    }
}
