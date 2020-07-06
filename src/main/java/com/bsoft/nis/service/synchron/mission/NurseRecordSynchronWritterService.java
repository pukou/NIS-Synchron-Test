package com.bsoft.nis.service.synchron.mission;

import com.bsoft.nis.core.datasource.RouteDataSourceService;
import com.bsoft.nis.domain.synchron.OutArgument;
import com.bsoft.nis.domain.synchron.SyncResult;
import com.bsoft.nis.pojo.exchange.Response;
import com.bsoft.nis.service.synchron.exception.SynchronException;
import ctd.net.rpc.Client;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Service;

/**
 * Describtion:同步到护理记录模块 服务
 * Created: dragon
 * Date： 2017/1/3.
 */
@Service
public class NurseRecordSynchronWritterService extends RouteDataSourceService implements ISynchronService {

    private Log logger = LogFactory.getLog(NurseRecordSynchronWritterService.class);

    /**
     * 护理记录同步
     * @param outArgument
     * @throws SynchronException
     */
    @Override
    public Response<SyncResult> synchron2MissionBusiness(OutArgument outArgument) throws SynchronException {
        Response<SyncResult> response = new Response<>();
        try {
	        if (outArgument.flag.equals("2")) { // 删除
		        response = Client.rpcInvoke("nis-core.nurseRecordRpcServerProvider",
				        "synchron2MissionBusinessDel", outArgument);
	        } else {
		        response = Client.rpcInvoke("nis-core.nurseRecordRpcServerProvider",
				        "synchron2MissionBusiness", outArgument);
	        }
        } catch (Throwable throwable) {
            response.ReType = 0;
            response.Msg = "同步目标失败";
            logger.error(throwable.getMessage(),throwable);
        }
        return response;
    }
}
