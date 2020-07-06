package com.bsoft.nis.service.synchron.mission;

import com.bsoft.nis.domain.synchron.OutArgument;
import com.bsoft.nis.domain.synchron.SyncResult;
import com.bsoft.nis.pojo.exchange.Response;
import com.bsoft.nis.service.synchron.exception.SynchronException;

/**
 * Describtion:
 * Created: dragon
 * Date： 2017/1/3.
 */
public interface ISynchronService {
    Response<SyncResult> synchron2MissionBusiness(OutArgument outArgument) throws SynchronException;
}
