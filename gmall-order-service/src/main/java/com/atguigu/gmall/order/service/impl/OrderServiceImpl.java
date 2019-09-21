package com.atguigu.gmall.order.service.impl;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.OrderDetail;
import com.atguigu.gmall.bean.OrderInfo;
import com.atguigu.gmall.bean.enums.OrderStatus;
import com.atguigu.gmall.bean.enums.ProcessStatus;
import com.atguigu.gmall.config.ActiveMQUtil;
import com.atguigu.gmall.config.RedisUtil;
import com.atguigu.gmall.order.mapper.OrderDetailMapper;
import com.atguigu.gmall.order.mapper.OrderInfoMapper;
import com.atguigu.gmall.service.OrderService;
import com.atguigu.gmall.service.PaymentService;
import com.atguigu.gmall.util.HttpClientUtil;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import redis.clients.jedis.Jedis;
import tk.mybatis.mapper.entity.Example;

import javax.jms.*;
import javax.jms.Queue;
import java.util.*;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderInfoMapper orderInfoMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private ActiveMQUtil activeMQUtil;

    @Reference
    private PaymentService paymentService;

    @Override
    @Transactional
    public String saveOrder(OrderInfo orderInfo) {
        //总金额，订单状态，userId ,第三方交易编号，创建时间，过期时间，进程状态
        orderInfo.sumTotalAmount(); //总金额
        orderInfo.setOrderStatus(OrderStatus.UNPAID);
        //设置创建时间
        orderInfo.setCreateTime(new Date());
        //设置失效时间
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE,1);
        orderInfo.setExpireTime(calendar.getTime());
        //设置进程状态
        orderInfo.setProcessStatus(ProcessStatus.UNPAID);
        //生成第三方编号
        String outTradeNo = "ATGUIGU"+System.currentTimeMillis()+""+new Random().nextInt(1000);
        orderInfo.setOutTradeNo(outTradeNo);

        //保存订单
        orderInfoMapper.insertSelective(orderInfo);
        //在订单详情表中设置订单id
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        for (OrderDetail orderDetail : orderDetailList) {
            orderDetail.setOrderId(orderInfo.getId());
            orderDetailMapper.insertSelective(orderDetail);
        }

        return orderInfo.getId();
    }

    @Override
    public String getTradeNo(String userId) {
        Jedis jedis = null;
        try {
            jedis = redisUtil.getJedis();

            String tradeNoKey="user:"+userId+":tradeCode";

            String tradeCode = UUID.randomUUID().toString().replaceAll("-","");

            jedis.set(tradeNoKey,tradeCode);

            return tradeCode;

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (jedis != null){
                jedis.close();
            }
        }

        return null;
    }

    @Override
    public boolean checkTradeCode(String userId, String tradeNo) {
        Jedis jedis = null;
        try {
            jedis = redisUtil.getJedis();

            String tradeNoKey = "user:"+userId+":tradeCode";

            String tradeNoCode = jedis.get(tradeNoKey);

            return tradeNo.equals(tradeNoCode);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (jedis != null){
                jedis.close();
            }
        }

        return false;
    }

    @Override
    public void delTradeCode(String userId) {
        Jedis jedis = null;
        try {
            jedis = redisUtil.getJedis();
            String tradeNoKey = "user:"+userId+":tradeCode";
            jedis.del(tradeNoKey);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (jedis != null){
                jedis.close();
            }
        }
    }

    @Override
    public boolean checkStock(String skuId, Integer skuNum) {

        String result = HttpClientUtil.doGet("http://www.gware.com/hasStock?skuId=" + skuId + "&num=" + skuNum);

        return "1".equals(result);
    }

    @Override
    public OrderInfo getOrderInfo(String orderId) {
        OrderInfo orderInfo = orderInfoMapper.selectByPrimaryKey(orderId);

        //将orderdtail的数据放入orderinfo中
        OrderDetail orderDetail = new OrderDetail();
        orderDetail.setOrderId(orderId);
        List<OrderDetail> orderDetailList = orderDetailMapper.select(orderDetail);
        orderInfo.setOrderDetailList(orderDetailList);
        return orderInfo;
    }

    @Override
    public void updateOrderStatus(String orderId, ProcessStatus processStatus) {

        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setId(orderId);

        orderInfo.setProcessStatus(processStatus);
        orderInfo.setOrderStatus(processStatus.getOrderStatus());

        orderInfoMapper.updateByPrimaryKeySelective(orderInfo);
    }

    @Override
    public void sendOrderStatus(String orderId) {
        Connection connection = activeMQUtil.getConnection();
        //获取要发送的json字符串
        String orderJson = initWareOrder(orderId);
        try {
            connection.start();
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            Queue order_result_queue = session.createQueue("ORDER_RESULT_QUEUE");
            MessageProducer producer = session.createProducer(order_result_queue);

            ActiveMQTextMessage activeMQTextMessage = new ActiveMQTextMessage();
            activeMQTextMessage.setText(orderJson);

            producer.send(activeMQTextMessage);
            //提交消息
            session.commit();
            producer.close();
            session.close();
            connection.close();


        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<OrderInfo> getExpiredOrderList() {

        Example example = new Example(OrderInfo.class);

        example.createCriteria().andLessThan("expireTime",new Date()).andEqualTo("processStatus",ProcessStatus.UNPAID);

        List<OrderInfo> orderInfoList = orderInfoMapper.selectByExample(example);

        return orderInfoList;
    }

    @Override
    @Async
    public void execExpiredOrder(OrderInfo orderInfo) {
        updateOrderStatus(orderInfo.getId(), ProcessStatus.CLOSED);
        paymentService.closePayment(orderInfo.getId());
    }

    private String initWareOrder(String orderId) {
        OrderInfo orderInfo = getOrderInfo(orderId);
        Map map =initWareOrder(orderInfo);

        return JSON.toJSONString(map);
    }

    public Map initWareOrder(OrderInfo orderInfo) {
        HashMap<String, Object> map = new HashMap<>();

        map.put("orderId",orderInfo.getId());
        map.put("consignee",orderInfo.getConsignee());
        map.put("consigneeTel",orderInfo.getConsigneeTel());
        map.put("orderComment",orderInfo.getOrderComment());
        map.put("orderBody","test--测试");
        map.put("deliveryAddress",orderInfo.getDeliveryAddress());
        map.put("paymentWay","2");
        map.put("wareId",orderInfo.getWareId());

        //获取订单详情中的数据
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        ArrayList<Map> mapArrayList = new ArrayList<>();
        if (orderDetailList != null && orderDetailList.size() > 0){
            for (OrderDetail orderDetail : orderDetailList) {
                HashMap<String, Object> orderDetailMap = new HashMap<>();
                orderDetailMap.put("skuId",orderDetail.getSkuId());
                orderDetailMap.put("skuNum",orderDetail.getSkuNum());
                orderDetailMap.put("skuName",orderDetail.getSkuName());

                mapArrayList.add(orderDetailMap);
            }
        }

        map.put("details",mapArrayList);
        return map;
    }

    @Override
    public List<OrderInfo> orderSplit(String orderId, String wareSkuMap) {
        List<OrderInfo> subOrderInfoList = new ArrayList<>();
        /*
        1.  获取原始订单
        2.  需要将wareSkuMap[{"wareId":"1","skuIds":["2","10"]},{"wareId":"2","skuIds":["3"]}] 中的数据判断是否需要拆单并写拆单规则
            wareSkuMap 转换为我们能操作的对象
        3.  创建新的子订单
        4.  给新的子订单赋值
        5.  保存子订单
        6.  将子订单添加到集合中List<OrderInfo>
        7.  更新原始订单的状态！
         */
        OrderInfo orderInfoOrigin = getOrderInfo(orderId);
        List<Map> maps = JSON.parseArray(wareSkuMap, Map.class);

        if (maps != null && maps.size() > 0){
            for (Map map : maps) {
                String wareId = (String) map.get("wareId");
                List<String> skuIds = (List<String>) map.get("skuIds");

                //创建新的子订单
                OrderInfo subOrderInfo = new OrderInfo();
                BeanUtils.copyProperties(orderInfoOrigin,subOrderInfo);

                subOrderInfo.setId(null);
                ArrayList<OrderDetail> orderDetailsList = new ArrayList<>();
                List<OrderDetail> orderDetailList = orderInfoOrigin.getOrderDetailList();
                if (orderDetailList != null && orderDetailList.size() > 0){
                    for (OrderDetail orderDetail : orderDetailList) {
                        for (String skuId : skuIds) {
                            if (orderDetail.getSkuId().equals(skuId)){
                                orderDetail.setId(null);
                                orderDetailsList.add(orderDetail);
                            }
                        }
                    }
                }

                subOrderInfo.setOrderDetailList(orderDetailsList);
                subOrderInfo.sumTotalAmount();
                subOrderInfo.setWareId(wareId);
                subOrderInfo.setParentOrderId(orderId);
                //保存子订单
                saveOrder(subOrderInfo);
                //添加子订单
                subOrderInfoList.add(subOrderInfo);
            }
        }

        //修改原始订单状态
        updateOrderStatus(orderId,ProcessStatus.SPLIT);
        return subOrderInfoList;

    }
}
