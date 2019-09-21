package com.atguigu.gmall.list.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.*;
import com.atguigu.gmall.service.ListService;
import com.atguigu.gmall.service.ManageService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Controller
public class ListController {

    @Reference
    private ListService listService;

    @Reference
    private ManageService manageService;

    @RequestMapping("list.html")
//    @ResponseBody
    public String getList(SkuLsParams skuLsParams, HttpServletRequest request){

        //设置每页显示数
        skuLsParams.setPageSize(2);
        SkuLsResult skuLsResult = listService.search(skuLsParams);

        System.out.println(JSON.toJSONString(skuLsResult));


        //从结果集中取出skulsinfo放入request域对象 中
        List<SkuLsInfo> skuLsInfoList = skuLsResult.getSkuLsInfoList();

        //获取平台属性和属性值
        List<BaseAttrInfo> baseAttrInfoList = null;
        List<String> attrValueIdList = skuLsResult.getAttrValueIdList();
        if (skuLsParams.getCatalog3Id() != null && skuLsParams.getCatalog3Id().length() > 0 ){
//            baseAttrInfoList = manageService.getAttrList(skuLsParams.getCatalog3Id());
            baseAttrInfoList = manageService.getAttrList(attrValueIdList);
        }else {
            baseAttrInfoList = manageService.getAttrList(attrValueIdList);
        }

        //编写方法记录当前的查询条件，返回到页面
        String urlParam = makeUrlParam(skuLsParams);

        //声明面包屑的集合
        List<BaseAttrValue> baseAttrValueArrayList = new ArrayList<>();

        for (Iterator<BaseAttrInfo> iterator = baseAttrInfoList.iterator(); iterator.hasNext(); ) {
            BaseAttrInfo baseAttrInfo =  iterator.next();

            List<BaseAttrValue> attrValueList = baseAttrInfo.getAttrValueList();
            for (BaseAttrValue baseAttrValue : attrValueList) {
                if (skuLsParams.getValueId() != null && skuLsParams.getValueId().length > 0){
                    for (String valueId : skuLsParams.getValueId()) {
                        //如果前台返回的查询条件中有该属性值id，则在平台展示的平台属性中删除此平台属性
                        if (valueId.equals(baseAttrValue.getId())){
                            iterator.remove();

                            //将此属性和属性值添加到面包屑中
                            BaseAttrValue baseAttrValueed = new BaseAttrValue();
                            baseAttrValueed.setValueName(baseAttrInfo.getAttrName()+":"+baseAttrValue.getValueName());

                            //获取面包屑中除了此属性值id外的请求参数，为了做面包屑的删除
                            String newUrlParam = makeUrlParam(skuLsParams,valueId);
                            baseAttrValueed.setUrlParam(newUrlParam);
                            baseAttrValueArrayList.add(baseAttrValueed);
                        }
                    }
                }
            }

        }

        //保存检索的关键字
        request.setAttribute("keyword",skuLsParams.getKeyword());
        //将面包屑保存到request域中
        request.setAttribute("baseAttrValueArrayList",baseAttrValueArrayList);
        //将请求参数保存到域中
        request.setAttribute("urlParam",urlParam);
        request.setAttribute("baseAttrInfoList",baseAttrInfoList);
        request.setAttribute("skuLsInfoList",skuLsInfoList);

        //分页
        request.setAttribute("totalPage",skuLsResult.getTotalPages());
        request.setAttribute("pageNo",skuLsParams.getPageNo());

        return "list";
    }

    private String makeUrlParam(SkuLsParams skuLsParams,String... excludeValueIds) {

        String urlParam = "";

        if (skuLsParams.getKeyword() != null && skuLsParams.getKeyword().length() > 0){
            urlParam += "keyword="+skuLsParams.getKeyword();
        }

        if (skuLsParams.getCatalog3Id() != null && skuLsParams.getCatalog3Id().length() > 0){
            if (urlParam.length() > 0){
                urlParam += "&";
            }

            urlParam += "catalog3Id="+skuLsParams.getCatalog3Id();
        }

        if (skuLsParams.getValueId() != null && skuLsParams.getValueId().length > 0){
            for (String valueId : skuLsParams.getValueId()) {

                if (excludeValueIds != null && excludeValueIds.length > 0){
                    String excludeValueId = excludeValueIds[0];
                    if (excludeValueId.equals(valueId)){
                        continue;
                    }
                }

                if (urlParam.length() > 0){
                    urlParam += "&";
                }
                urlParam += "valueId="+valueId;

            }
        }
        return urlParam;
    }

}
