package com.bsoft.nis.service.synchron;

import com.alibaba.fastjson.JSON;
import com.bsoft.nis.domain.synchron.InArgument;
import com.bsoft.nis.domain.synchron.SelectResult;
import com.bsoft.nis.domain.synchron.Sheet;
import com.bsoft.nis.pojo.exchange.Response;
import com.bsoft.nis.rpc.synchron.SynchronServerApi;
import com.bsoft.nis.util.redis.RedisUtils;
import ctd.util.annotation.RpcService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import redis.clients.jedis.Jedis;

import java.util.List;
import java.util.UUID;

/**
 * Describtion:同步服务提供者（RPC方式）
 * Created: dragon
 * Date： 2017/1/11.
 */
public class SynchronRpcServerProvider implements SynchronServerApi{

    @Autowired
    SynchronService service;
    @Autowired
    JedisConnectionFactory jedisConnectionFactory;

    public final Integer EXPERT_TIME_SECONDS = 180;   // 缓存过期时间

    /**
     * 用户第一次调用同步的
     * @param inArgument
     * @return
     */
    @RpcService
    @Override
    public Response<SelectResult> synchron(InArgument inArgument) {
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
            response.Data = selectResult;
            // 缓存数据
            String key = "synchron:synservice:uuid:" + selectResult.UUID;
            Jedis jedis = RedisUtils.getRedisClient(jedisConnectionFactory);
            if (jedis == null || !jedis.ping().equals("PONG")){
                response.ReType = 0;
                response.Msg = "Redis服务器连接不上!";
                return response;
            }
            String value = JSON.toJSONString(inArgument);
            jedis.set(key,value);
            jedis.expire(key,EXPERT_TIME_SECONDS);
        }
        return response;
    }

    /**
     * 用户选择表单后，调用该方法
     * @param selectResult
     * @return
     */
    @RpcService
    @Override
    public Response<String> synchronRepeat(SelectResult selectResult) {
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

	/**
	 * 删除操作的同步
	 * @param inArgument
	 * @return
	 */
	@RpcService
    @Override
	public Response<String> DeleSyncData(InArgument inArgument) {
	    return service.DeleSyncData(inArgument);
    }
}
