package com.bsoft.nis.service.synchron;

import com.bsoft.nis.core.datasource.RouteDataSourceService;
import com.bsoft.nis.domain.synchron.InArgument;
import com.bsoft.nis.domain.synchron.OutArgument;
import com.bsoft.nis.domain.synchron.Sheet;
import com.bsoft.nis.domain.synchron.SyncResult;
import com.bsoft.nis.pojo.exchange.BizResponse;
import com.bsoft.nis.pojo.exchange.Response;
import com.bsoft.nis.service.synchron.exception.*;
import com.bsoft.nis.service.synchron.mission.NurseRecordSynchronWritterService;
import com.bsoft.nis.service.synchron.support.SynchronServiceSup;
import com.bsoft.nis.service.synchron.wrapper.SynchronWrapperService;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Describtion:同步服务
 * Created: dragon
 * Date： 2017/1/3.
 */
@Service
public class SynchronService extends RouteDataSourceService {
    private Log logger = LogFactory.getLog(SynchronService.class);
    @Autowired
    SynchronServiceSup recordService; // 同步服务

    @Autowired
    SynchronWrapperService wrapperService;  // 同步组装服务

    @Autowired
    NurseRecordSynchronWritterService nurseRecordSynchronService; // 同步到护理记录的服务

    /**
     * 同步服务
     * @param inArgument
     * @return
     */
    public Response<List<Sheet>> synchron(InArgument inArgument) {
        Response<List<Sheet>> response = new Response<>();
        Response<SyncResult> otherBussServiceResponse = new Response<>();
        List<OutArgument> outArguments = new ArrayList<>();
        try{
            validInArgument(inArgument);
            outArguments = wrapperService.wrapperOutArguments(inArgument);

            // 根据表单类型确定调用哪个服务
            for (OutArgument outArgument:outArguments){
                try {
                    switch (outArgument.bdlx){
                        case "6":
                            otherBussServiceResponse = nurseRecordSynchronService.synchron2MissionBusiness(outArgument);
                            break;
                        default:
                            break;
                    }

                    // 新增的业务，需写入同步记录表  // TODO 同步成功，但同步记录失败，补救策略
                    if (otherBussServiceResponse.ReType == 1){
                        SyncResult result = otherBussServiceResponse.Data;
                        if (result != null && result.IsInsert){
                            // 插入同步记录操作
                            recordService.insertSynchronRecord(result);
                        }
                    }
                }catch (SynchronException ex){
                    logger.error(inArgument.toString() +">>同步失败：目标业务同步写入失败，" + ex.getMessage(),ex);
                }
            }

            response.ReType = 1 ;
            response.Msg = "同步成功!";
        }catch (InArgumentException ex){
            response.ReType = 0;
            response.Msg = "入参信息不完整";
            logger.error(inArgument.toString() +">>同步失败：入参信息不完整，" + ex.getMessage(),ex);
        }catch (TooMuchSheetException ex){
            response.ReType = 2;
            response.Msg = "请用户选择表单";
            response.Data = inArgument.selectSheets;
        }catch (NoCustomMadeException ex){
            response.ReType = 1;
            response.Msg = ex.getMessage();
            logger.error(inArgument.toString()+">>同步失败："+ex.getMessage(),ex);
        }catch (SynchronWrapperException ex){
            response.ReType = 0;
            response.Msg = "同步组装失败!";
            logger.error(inArgument.toString() +">>同步失败：同步出参组装失败，" + ex.getMessage(),ex);
        }catch (SQLException | DataAccessException ex){
            response.ReType = 0;
            response.Msg = "同步记录写入失败!";
            logger.error(inArgument.toString() +">>同步失败：同步出参组装失败，" + ex.getMessage(),ex);
        }
        return response;
    }


    /**
     * 校验入参
     */
    public void validInArgument(InArgument inArgument) throws InArgumentException {
        if (inArgument == null) throw new InArgumentException("同步入参为Null");
        if (StringUtils.isEmpty(inArgument.zyh)) throw new InArgumentException("同步入参：住院号不可为空");
        if (StringUtils.isEmpty(inArgument.bqdm)) throw new InArgumentException("同步入参：病区代码不可为空");
        if (StringUtils.isEmpty(inArgument.jlsj)) throw new InArgumentException("同步入参：记录时间不可为空");
        if (StringUtils.isEmpty(inArgument.bdlx)) throw new InArgumentException("同步入参：表单类型不可为空");
    }

    /**
     * 写入同步记录
     * @param inArgument
     * @param outArgument
     * @return
     */
    public BizResponse<String> synchronRecordWrite(InArgument inArgument,OutArgument outArgument){
        BizResponse<String> bizResponse = new BizResponse<>();
        return bizResponse;
    }

	/**
	 * 删除的同步服务
	 * @param inArgument
	 * @return
	 */
	public Response<String> DeleSyncData(InArgument inArgument) {
		Response<String> response = new Response<>();
		Response<SyncResult> resultResponse;
		try {
			// 校验入参
			validInArgument2(inArgument);
			// 组装出参
			List<OutArgument> outArguments = wrapperService.wrapperOutArgumentsForDelete(inArgument);
			// 根据表单类型确定调用哪个服务
			for (OutArgument outArgument : outArguments) {
				try {
					switch (outArgument.bdlx) {
					case "6":
						resultResponse = nurseRecordSynchronService.synchron2MissionBusiness(outArgument);
						break;
					default:
						break;
					}
				} catch (SynchronException ex) {
					logger.error(inArgument.toString() +">>同步失败：目标业务同步删除失败，" + ex.getMessage(), ex);
				}
			}

			response.ReType = 1 ;
			response.Msg = "同步成功!";
		} catch (InArgumentException ex) {
			response.ReType = 0;
			response.Msg = "入参信息不完整";
			logger.error(inArgument.toString() +">>同步失败：入参信息不完整，" + ex.getMessage(),ex);
		} catch (SynchronWrapperException ex) {
			response.ReType = 0;
			response.Msg = "组装出参数据失败";
			logger.error(inArgument.toString() +">>同步失败：组装出参数据失败，" + ex.getMessage(),ex);
		}
		return response;
	}

	/**
	 * 校验删除操作的入参
	 * @param inArgument
	 * @throws InArgumentException
	 */
	public void validInArgument2(InArgument inArgument) throws InArgumentException {
		if (inArgument == null) throw new InArgumentException("同步入参为Null");
		if (StringUtils.isEmpty(inArgument.bdlx)) throw new InArgumentException("同步入参：表单类型不可为空");
		if (StringUtils.isEmpty(inArgument.jlxh)) throw new InArgumentException("同步入参：记录序号不可为空");
		if (StringUtils.isEmpty(inArgument.lymxlx)) throw new InArgumentException("同步入参：来源明细类型不可为空");
		if (!inArgument.lymxlx.equals("0"))	if (StringUtils.isEmpty(inArgument.bdlx)) throw new InArgumentException("同步入参：表单类型不可为空");
	}
}
