package cn.wanghaomiao.seimi.core;

/*
   Copyright 2015 - now original author

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

import cn.wanghaomiao.seimi.http.HttpMethod;
import cn.wanghaomiao.seimi.httpd.CrawlerStatusHttpProcessor;
import cn.wanghaomiao.seimi.httpd.PushRequestHttpProcessor;
import cn.wanghaomiao.seimi.httpd.SeimiHttpHandler;
import cn.wanghaomiao.seimi.struct.CrawlerModel;
import cn.wanghaomiao.seimi.struct.Request;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.server.Server;

import java.util.Map;

/**
 * @author 汪浩淼 [et.tw@163.com]
 * @since 2015/10/16.
 */
public class Seimi extends SeimiContext {
    /**
     * 主启动
     * start master
     * @param crawlerNames
     */
    public void start(String... crawlerNames){
        if (crawlerNames==null||crawlerNames.length==0){
            for (Map.Entry<String,CrawlerModel> entry:crawlerModelContext.entrySet()){
                sendRequest(entry.getKey(),entry.getValue().getQueueInstance(),entry.getValue().getInstance().startUrls());
            }
        }else {
            for (String name:crawlerNames){
                CrawlerModel crawlerModel = crawlerModelContext.get(name);
                if (crawlerModel!=null){
                    sendRequest(crawlerModel.getCrawlerName(),crawlerModel.getQueueInstance(),crawlerModel.getInstance().startUrls());
                }else {
                    logger.error("error crawler name '{}',can not find it!",name);
                }
            }
        }
    }

    /**
     * 按名称启动爬虫并开启http服务接口API
     */
    public void startWithHttpd(int port,String... crawlerNames){
        start(crawlerNames);
        SeimiHttpHandler seimiHttpHandler = new SeimiHttpHandler(crawlerModelContext);
        if (crawlerNames==null||crawlerNames.length==0){
            for (Map.Entry<String,CrawlerModel> entry:crawlerModelContext.entrySet()){
                seimiHttpHandler.add("/push/"+entry.getKey(),new PushRequestHttpProcessor(entry.getValue().getQueueInstance(),entry.getKey()))
                        .add("/status/"+entry.getKey(),new CrawlerStatusHttpProcessor(entry.getValue().getQueueInstance(),entry.getKey()));
            }
        }else {
            for (String name:crawlerNames){
                CrawlerModel crawlerModel = crawlerModelContext.get(name);
                if (crawlerModel!=null){
                    seimiHttpHandler.add("/push/"+name,new PushRequestHttpProcessor(crawlerModel.getQueueInstance(),name))
                            .add("/status/"+name,new CrawlerStatusHttpProcessor(crawlerModel.getQueueInstance(),name));
                }
            }
        }
        logger.info("Http request push service also started on port:{}",port);
        startJetty(port,seimiHttpHandler);
    }

    public void startAll(){
        start();
    }
    public void startAllWithHttpd(int port){
        startWithHttpd(port);
    }
    public void startWorkers(){
        //初始化Seimi对象时即完成了workers的创建，故这里仅用作引导说明。
        logger.info("workers started!");
    }

    private void sendRequest(String crawlerName,SeimiQueue queue,String[] startUrls){
        if (ArrayUtils.isNotEmpty(startUrls)){
            for (String url:startUrls){
                Request request = new Request();
                String[] urlPies = url.split("##");
                if (urlPies.length>=2&& StringUtils.lowerCase(urlPies[1]).equals("post")){
                    request.setHttpMethod(HttpMethod.POST);
                }
                request.setCrawlerName(crawlerName);
                request.setUrl(url);
                request.setCallBack("start");
                queue.push(request);
                logger.info("{} url={} started",crawlerName,url);
            }
        }else {
            logger.error("crawler:{} can not find start urls!",crawlerName);
        }
    }

    private void startJetty(int port,SeimiHttpHandler seimiHttpHandler){
        Server server = new Server(port);
        server.setHandler(seimiHttpHandler);
        try {
            server.start();
            server.join();
        } catch (Exception e) {
            logger.error("http service start error,{}",e.getMessage(),e);
        }
    }
}
