package com.train.ticket.max12306.common;

import cn.hutool.json.JSONUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @ClassName CdnUtil
 * @ClassExplain: 说明
 * @Author Duxiaoyu
 * @Date 2020/9/5 10:21
 * @Since V 1.0
 */
public class CdnUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(CdnUtil.class);

    private static String cdnPath = "src/main/resources/cdn.txt";

    private static String host = "kyfw.12306.cn";

    private static String station_url = "https://%s/otn/resources/js/framework/station_name.js?station_version=1.9151";

    private static final int SUCCESS = 200;

    private static int isCdnCount = 0;

    private static int noCdnCount = 0;

    private static Set<String> availableCdnList = new HashSet<>();

    // 用于执行cdn检测任务的线程池
    private static ExecutorService executorService = Executors.newCachedThreadPool();

    /**
     * 读取cdn文件
     *
     * @return
     * @throws Exception
     */
    public static List<String> readerCdnFile() throws Exception {
        File file = new File(cdnPath);
        List<String> cdnList = new ArrayList<>();
        try (BufferedReader bfr = new BufferedReader(new FileReader(file))) {
            String lineContent = "";
            while (StringUtils.isNotBlank((lineContent = bfr.readLine()))) {
                cdnList.add(lineContent);
            }
        }
        return cdnList;
    }

    /**
     * 检测cdn是否可用
     *
     * @param ip
     * @return
     */
    public static boolean checkCdn(String ip) {
        HttpGet get = HttpURL12306.httpGetBuild(String.format(station_url, ip), null);
        get.addHeader("Host", host);
        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(3000)
                .setConnectionRequestTimeout(1000).setSocketTimeout(3000).build();
        get.setConfig(requestConfig);
        try (CloseableHttpClient client = HttpURL12306.httpClientBuild()) {
            try (CloseableHttpResponse response = client.execute(get)) {
                if (response.getStatusLine().getStatusCode() == SUCCESS) {
                    HttpEntity entity = response.getEntity();
                    String result = EntityUtils.toString(entity);
                    // 释放资源
                    EntityUtils.consume(entity);
                    if (StringUtils.isNotBlank(result)) {
                        LOGGER.info("======> cdn: {} -> 可用...", ip);
                        return true;
                    } else {
                        LOGGER.info("======> cdn: {} -> 不可用...", ip);
                    }
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    /**
     * 获取可用cdn
     *
     * @return
     */
    public static List<String> getAvailableCdn() throws Exception {
        List<String> existCdn = readerCdnFile();
        for (String cdn : existCdn) {
            executorService.execute(createCheckCdnTask(cdn));
        }
        // 等待任务执行结束
        Thread.sleep(15000);
        LOGGER.info("======> cdn检测结束 <======");
        LOGGER.info("======> 可用cdn: {}个 -> {} ...", isCdnCount, JSONUtil.toJsonStr(availableCdnList));
        LOGGER.info("======> 不可用cdn: {}个 ...", noCdnCount);
        // 检测结束 -> 关闭线程池
        executorService.shutdown();
        return new ArrayList<>(availableCdnList);
    }

    /**
     * 创建多线程检测任务
     *
     * @param ip
     * @return
     */
    public static Runnable createCheckCdnTask(String ip) {
        return new Runnable() {
            @Override
            public void run() {
                synchronized (this) {
                    boolean flag = checkCdn(ip);
                    if (flag) {

                        isCdnCount++;
                        availableCdnList.add(ip);
                    } else {
                        noCdnCount++;
                    }
                }
            }
        };
    }
}