package com.bsoft.nis.mapper.synchron;

import com.bsoft.nis.domain.core.synchron.record.SynchronRecord;
import com.bsoft.nis.domain.core.synchron.rules.Synchron;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * Describtion:
 * Created: dragon
 * Date： 2017/1/4.
 */
public interface SynchronServiceMapper {
    List<Synchron> getSynchronByBQDMAndBdlx(@Param(value = "JGID") String jgid, @Param(value = "BQDM") String bqdm, @Param(value = "BDLX") String bdlx, @Param(value = "LYBD") String lybd);

    List<Map> getLastNurseRecordOfPatient(@Param(value = "JGID") String jgid, @Param(value = "ZYH") String zyh, @Param(value = "MBBD") String mbbd);

    List<SynchronRecord> getSynchronRecordsByLylxAndLyjl(@Param(value = "LYLX") String lylx, @Param(value = "LYJL") String lyjl, @Param(value = "LYMXLX") String lymxlx, @Param(value = "LYMX") String lymx);

    List<Map> getPatientNameByZyh(@Param(value = "JGID") String jgid, @Param("ZYH") String zyh);

    Integer insertSynchronRecord(SynchronRecord record);

	// 从护理记录明细获取JLBH
	String getJlbhFromJlmx(@Param(value = "BDLX") String bdlx, @Param(value = "JLXH") String jlxh);

	// 从护理记录明细获取JLBH(护理计划和护理焦点)
	String getJlbhFromJlmxByLymxlx(@Param(value = "BDLX") String bdlx, @Param(value = "JLXH") String jlxh, @Param(value = "LYMX") String lymx, @Param(value = "LYMXLX") String lymxlx);
}
