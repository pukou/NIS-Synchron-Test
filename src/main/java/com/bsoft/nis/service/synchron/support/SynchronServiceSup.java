package com.bsoft.nis.service.synchron.support;

import com.bsoft.nis.common.service.DateTimeService;
import com.bsoft.nis.common.service.IdentityService;
import com.bsoft.nis.core.datasource.DataSource;
import com.bsoft.nis.core.datasource.RouteDataSourceService;
import com.bsoft.nis.domain.core.synchron.record.SynchronRecord;
import com.bsoft.nis.domain.core.synchron.rules.Synchron;
import com.bsoft.nis.domain.synchron.SyncResult;
import com.bsoft.nis.mapper.synchron.SynchronServiceMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Describtion:同步子服务
 * Created: dragon
 * Date： 2017/1/4.
 */
@Service
public class SynchronServiceSup extends RouteDataSourceService {

    @Autowired
    IdentityService identityService;
    @Autowired
    SynchronServiceMapper mapper;
    @Autowired
    DateTimeService dateTimeService;

    public List<Synchron> getSynchronByBQDMAndBdlx(String jgid, String bqdm, String bdlx, String lybd) throws SQLException,DataAccessException{
        return mapper.getSynchronByBQDMAndBdlx(jgid,bqdm,bdlx,lybd);
    }

    public List<Map> getLastNurseRecordOfPatient(String jgid, String zyh, String mbbd) throws SQLException,DataAccessException{
        return mapper.getLastNurseRecordOfPatient(jgid, zyh, mbbd);
    }

    public List<SynchronRecord> getSynchronRecordsByLylxAndLyjl(String lylx, String lyjl, String lymxlx, String lymx) throws SQLException,DataAccessException{
        return mapper.getSynchronRecordsByLylxAndLyjl(lylx, lyjl, lymxlx, lymx);
    }

    public List<Map> getPatientNameByZyh(String jgid, String zyh) {
        keepOrRoutingDateSource(DataSource.HRP);
        return mapper.getPatientNameByZyh(jgid, zyh);
    }

    public Integer insertSynchronRecord(SyncResult result) throws SQLException,DataAccessException{
        SynchronRecord record = new SynchronRecord();
        record.JLXH = String.valueOf(identityService.getIdentityMax("IENR_TBJL"));
        record.JGID = result.JGID;
        record.LYLX = result.LYLX;
        record.LYJL = result.LYJL;
        record.LYMXLX = result.LYMXLX;
        record.LYMX = result.LYMX;
        record.MBLX = result.MBLX;
        record.MBJL = result.MBJL;
        record.JLSJ = dateTimeService.now(DataSource.PORTAL);
	    keepOrRoutingDateSource(DataSource.MOB);
	    record.dbtype = getCurrentDataSourceDBtype();
        return mapper.insertSynchronRecord(record);
    }

	public String getJlbhFromJlmx(String bdlx, String jlxh, String lymx, String lymxlx) throws SQLException,DataAccessException {
		if ("0".equals(lymxlx)) {
			return mapper.getJlbhFromJlmx(bdlx, jlxh);
		} else {
			return mapper.getJlbhFromJlmxByLymxlx(bdlx, jlxh, lymx, lymxlx);
		}
	}
}
