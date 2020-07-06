package com.bsoft.nis.controller.synchron;

import com.alibaba.fastjson.JSON;
import com.bsoft.nis.domain.advicesplit.advice.User;
import com.bsoft.nis.domain.synchron.InArgument;
import com.bsoft.nis.domain.synchron.SelectResult;
import com.bsoft.nis.domain.synchron.Sheet;
import com.bsoft.nis.pojo.exchange.Response;
import com.bsoft.nis.service.synchron.SynchronService;
import com.bsoft.nis.util.redis.RedisUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import redis.clients.jedis.Jedis;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.UUID;

/**
 * Describtion:同步服务控制器
 * Created: dragon
 * Date： 2017/1/16.
 */
@Controller
public class SynchronController {
    @Autowired
    SynchronService service;
    @Autowired
    JedisConnectionFactory jedisConnectionFactory;

    public final Integer EXPERT_TIME_SECONDS = 180;   // 缓存过期时间(单位:秒)

    /**
     * 同步服务
     * @param inArgument
     * @return
     */
    @RequestMapping(value = "synchron/data")
    public @ResponseBody
    Response<SelectResult> synchron(@RequestBody InArgument inArgument){
        Response<SelectResult> response = new Response<>();
        Response<List<Sheet>> bizresponse = service.synchron(inArgument);

        response.ReType = bizresponse.ReType;
        response.Msg = bizresponse.Msg;

        // 需用户选择
        if (response.ReType == 2){
            List<Sheet> sheets = bizresponse.Data;
            SelectResult selectResult = new SelectResult();
            selectResult.sheets = sheets;
            selectResult.UUID = UUID.randomUUID().toString();
            // 缓存数据
            String key = "synchron:synservice:uuid:" + selectResult.UUID;
            Jedis jedis = RedisUtils.getRedisClient(jedisConnectionFactory);
            if (jedis == null || !jedis.ping().equals("PONG")){
                response.ReType = 0;
                response.Msg = "Redis服务器连接失败!";
                return response;
            }
            //设置redis缓存
            String value = JSON.toJSONString(inArgument);
            jedis.set(key,value);
            jedis.expire(key,EXPERT_TIME_SECONDS);

            //设置返回结果
            response.Data = selectResult;
        }
        return response;
    }


    @RequestMapping(value = "synchron/repeat/data")
    public @ResponseBody
    Response<String> synchronRepeat(@RequestBody SelectResult selectResult){
        Response<String> response = new Response<>();
        Response<List<Sheet>> bizresponse = null;
        // 查找当前数据是否存在缓存中
        String uuid = selectResult.UUID;
        Jedis jedis = RedisUtils.getRedisClient(jedisConnectionFactory);
        if (jedis == null || !jedis.ping().equals("PONG")){
            response.ReType = 0;
            response.Msg = "Redis服务器连接不上!";
            return response;
        }

        String key = "synchron:synservice:uuid:" + uuid;
        if (!jedis.exists(key)){
            response.ReType = 0;
            response.Msg = "同步数据缓存过期或人为删除!";
            return response;
        }

        String value = jedis.get(key);
        if (StringUtils.isEmpty(value)){
            response.ReType = 0;
            response.Msg = "同步缓存数据为Null!";
            return response;
        }

        // 将缓存数据转成对象
        InArgument inArgument = null;
        inArgument = JSON.parseObject(value,InArgument.class);
        if (inArgument == null){
            response.ReType = 0;
            response.Msg = "同步缓存数据为Null!";
            return response;
        }

        // 修改状态和数据
        inArgument.isUserConfirmOper = true;
        inArgument.selectSheets = selectResult.sheets;
        bizresponse = service.synchron(inArgument);

        response.ReType = bizresponse.ReType;
        response.Msg = bizresponse.Msg;
        return response;
    }

    @RequestMapping(value = "test/login")
    public @ResponseBody
    Response<String> login(@RequestParam(value = "userid") String userid,
                           @RequestParam(value = "username") String username,
                           HttpServletRequest request){
        Response<String> response = new Response<>();

        User user = new User();
        user.id = userid;
        user.name = username;

        HttpSession session = request.getSession();
        session.setAttribute("user",user);
        System.out.print(session.getId());
        response.ReType = 1;
        response.Msg = "登录成功";

        return response;
    }

    @RequestMapping(value = "test/do")
    public @ResponseBody
    Response<String> doSomeThing(
                           HttpServletRequest request){
        Response<String> response = new Response<>();

        HttpSession session = request.getSession();
        System.out.print(session.getId());
        response.ReType = 1;
        response.Msg = "获取sessionid";
        response.Data = session.getId();

        // 生成全局唯一ID;
        UUID uuid = UUID.randomUUID();
        String s = UUID.randomUUID().toString();
        System.out.println(s);

        Jedis jedis = jedisConnectionFactory.getShardInfo().createResource();
        System.out.println(jedis.ping());
        // 写入一个对象
        jedis.set("synchron:synservice:uuid:" + s, s);
        System.out.println(jedis.get("synchron:uuid"));

        User user = new User();
        user.id = "234";
        user.name = "邢海龙";

        jedis.set("user",JSON.toJSONString(user));
        jedis.set("id1","邢海龙");
        jedis.expire("id1",10);

        return response;
    }
}
