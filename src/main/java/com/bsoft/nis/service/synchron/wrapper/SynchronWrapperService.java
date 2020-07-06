package com.bsoft.nis.service.synchron.wrapper;

import com.bsoft.nis.common.service.DateTimeService;
import com.bsoft.nis.common.servicesup.support.ConfigServiceSup;
import com.bsoft.nis.core.cached.CachedDictEnum;
import com.bsoft.nis.core.cached.DictCachedHandler;
import com.bsoft.nis.core.datasource.DataSource;
import com.bsoft.nis.core.datasource.RouteDataSourceService;
import com.bsoft.nis.domain.core.SystemConfig;
import com.bsoft.nis.domain.core.synchron.record.SynchronRecord;
import com.bsoft.nis.domain.core.synchron.rules.Synchron;
import com.bsoft.nis.domain.core.synchron.rules.SynchronMission;
import com.bsoft.nis.domain.core.synchron.rules.SynchronSource;
import com.bsoft.nis.domain.synchron.InArgument;
import com.bsoft.nis.domain.synchron.OutArgument;
import com.bsoft.nis.domain.synchron.Project;
import com.bsoft.nis.domain.synchron.Sheet;
import com.bsoft.nis.service.synchron.exception.NoCustomMadeException;
import com.bsoft.nis.service.synchron.exception.SynchronWrapperException;
import com.bsoft.nis.service.synchron.exception.TooMuchSheetException;
import com.bsoft.nis.service.synchron.support.SynchronServiceSup;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.*;

/**
 * Describtion:同步组装服务
 * Created: dragon
 * Date： 2017/1/3.
 */
@Service
public class SynchronWrapperService extends RouteDataSourceService {
    private Log logger = LogFactory.getLog(SynchronWrapperService.class);
    @Autowired
    SynchronServiceSup service;
    @Autowired
    ConfigServiceSup configService;
    @Autowired
    DictCachedHandler dictCachedHandler;
    @Autowired
    DateTimeService dateTimeService;

    /**
     * 组装同步出参组，根据入参信息、病区定制的同步规则信息生成出参组
     * @param inArgument
     * @throws TooMuchSheetException
     * @throws SynchronWrapperException
     */
    public List<OutArgument> wrapperOutArguments(InArgument inArgument) throws TooMuchSheetException,SynchronWrapperException ,NoCustomMadeException{
        List<OutArgument> outArguments = new ArrayList<>();
        List<SystemConfig> configs = new ArrayList<>();
        // 1.加载目标类型配置列表
        try {
            configs = configService.getConfigsByDmlb("442");
        } catch (SQLException e) {
            throw new SynchronWrapperException("获取目标类型配置列表失败，失败原因："+e.getMessage());
        }

        // 2.加载同步规则
        List<Synchron> synchrons = getSynchronByBQDMAndBdlx(inArgument.jgid,inArgument.bqdm,inArgument.bdlx,inArgument.lybd);
        if (synchrons.size()<=0){
            throw new NoCustomMadeException("该病区未定制同步规则!") ;
        }

        // 3.同步目标表单多个：采用预定的简单选择策略决定使用哪个表单，如果决定不了，抛给调用者选择
        List<Sheet> confirmSheets = new ArrayList<>();
        List<Sheet> notConfirmSheets = new ArrayList<>();
        Boolean isUserConfirm = false;
        Boolean needUserConfirm = false;
        if (inArgument.isUserConfirmOper){
            isUserConfirm = true;
        }

        for (SystemConfig config:configs){
            List<Synchron> synchronsTemp = (List<Synchron>)CollectionUtils.select(synchrons,new missionSheetPredicate(config.DMSB));
            // 同一类型的目标表单有多个，需使用选择策略，确定使用哪个表单
            if (synchronsTemp.size() > 1){
                Boolean canConfirm = multiSheetsSelectStrategy(inArgument, synchronsTemp, confirmSheets, notConfirmSheets, isUserConfirm);
                if (!canConfirm){
                    needUserConfirm = true;
                }

            // 同一类型的目标表单只有一个，直接确定使用
            }else if (synchronsTemp.size() == 1){
                confirmSheets.add(new Sheet(String.valueOf(synchrons.get(0).MBBD), synchrons.get(0).MBBDMC));
            }
        }

        // 本次调用不是用户选择表单调用操作 且 本次调用需要用户确认的
        if (!isUserConfirm && needUserConfirm){
            throw new TooMuchSheetException("预定多目标表单同步策略失效，需调用者选择");
        }

        // 4.组装已经确定的同步目标表单的出参
        //   需同步的表单=调用者传入 + 系统可以确定同步的表单
        List<Sheet> needSynchronSheets = (List<Sheet>)CollectionUtils.union(inArgument.selectSheets,confirmSheets);
        for (Synchron synchron:synchrons){
            for (Sheet sheet:needSynchronSheets){
                if (String.valueOf(synchron.MBBD).equals(sheet.bdid)){
                    try{
                        OutArgument outArgument = wrapperOutArgument(inArgument,synchron);
                        if (outArgument.saveProjects.size() > 0 || outArgument.flag.equals("2")){
                            outArguments.add(outArgument);
                        }
                    }catch (SynchronWrapperException ex){
                        logger.error(inArgument.toString() +">>组装同步出参失败：" + ex.getMessage());
                    }

                }
            }
        }

        return outArguments;
    }

    /**
     * 组装同步单个出参，根据入参信息、同步规则生成出参对象
     * @param inArgument
     * @param synchron
     * @return
     */
    public OutArgument wrapperOutArgument(InArgument inArgument, Synchron synchron)throws SynchronWrapperException {
        OutArgument outArgument = new OutArgument();
	    outArgument.saveProjects = new ArrayList<>();
        // 静态参数
        outArgument.zyh = inArgument.zyh;
        outArgument.hsgh = inArgument.hsgh;
        outArgument.jlsj = inArgument.jlsj;
        outArgument.bdlx = String.valueOf(synchron.MBLX);
        outArgument.bdys = String.valueOf(synchron.MBBD);
        outArgument.jgid = inArgument.jgid;
        outArgument.bqdm = inArgument.bqdm;
        outArgument.flag = inArgument.flag;
        outArgument.lybdlx = inArgument.bdlx;
        outArgument.lyjlxh = inArgument.jlxh;
        outArgument.errFlag = 0;
        outArgument.errMsg = "";
        outArgument.lymxlx = StringUtils.isEmpty(inArgument.lymxlx)?"0":inArgument.lymxlx;
        outArgument.lymx = inArgument.lymx;

        // 出参：目标记录序号
        if (inArgument.flag.equals("0")){ // 新增
            outArgument.mbjlxh = "0";
        }else if (inArgument.flag.equals("1") || inArgument.flag.equals("2")){ // 修改或删除
            try {
                List<SynchronRecord> records = getSynchronRecordsByLylxAndLyjl(inArgument.bdlx,inArgument.jlxh,inArgument.lymxlx,inArgument.lymx);
                if (records.size() != 1){
                    throw new SynchronWrapperException("修改或删除状态下同步记录不唯一");
                }

                outArgument.mbjlxh = records.get(0).MBJL;
            }catch (SynchronWrapperException ex){
                throw new SynchronWrapperException(ex.getMessage());
            }
        }

        List<Project> inProjects = inArgument.projects;
        if (inProjects.size() <= 0){
            return outArgument;
        }
        List<SynchronMission> missions = synchron.missionProjects;
        String wrapperResult = "";
        switch (synchron.DZLX){
            case 1: // 分类
                for (SynchronMission mission:missions){
					wrapperResult = "";
                    Boolean needSave = false;
                    Long mbxm = mission.MBXM;
                    List<SynchronSource> sources = mission.missionSources;
                    /**
                     * 修复动态内容为空，静态内容依然显示的问题
                     */
                    Stack<String> sourceStack = new Stack<>();
                    Integer sjlx_pre = 0;
                    for (int m = 0;m <sources.size();m++){
                        SynchronSource source = sources.get(m);
	                    String xmlx = String.valueOf(source.XMLX);
                        String jtnr = source.JTNR;
                        Integer sjlx = source.SJLX;

                        // 动态项目，需从入参中查找结果
                        if (sjlx == 2){
                            String fljg = "";

                            // 对照项目分类
                            for (Project project:inProjects){
                                String key = project.key;
                                String value = project.value;
                                List<Project> ininProjects = project.saveProjects;

	                            if (key.equals(xmlx)){
		                            // 动态内容为空时，清除前一个静态内容
		                            if (ininProjects.size() <= 0) {
		                                if (!sourceStack.empty()){
                                            sourceStack.pop();
			                            }
			                            continue;
		                            }

                                    for (int i = 0 ; i < ininProjects.size();i++){
                                        Project project1 = ininProjects.get(i);
                                        if (i == (ininProjects.size()-1)){
                                            fljg = fljg + project1.value;
                                        }else{
                                            //fljg = fljg + project1.value + ",";
                                            fljg = fljg + project1.value + (jtnr == null || "".equals(jtnr) ? "," : jtnr);
                                        }
                                    }

                                    // 护理计划、护理焦点的护理问题不用加标点,医嘱执行不加标点
                                    if ((inArgument.bdlx.equals("4") || inArgument.bdlx.equals("8") && xmlx.equals("1")) || inArgument.bdlx.equals("9")){
                                        fljg = fljg;
                                    }else{
                                        fljg = fljg + "。";
                                    }

                                    if (fljg.equals("。")) fljg = "";

                                }
                            }
                            if (!StringUtils.isEmpty(fljg)){
                                sjlx_pre = sjlx;
                                sourceStack.push(fljg);
                            }else{
                                if (sjlx_pre == 1){
                                    if (!sourceStack.empty()){
                                        sourceStack.pop();
                                    }
                                }
                            };
                        }else if (sjlx == 1){ // 静态项目
                            sjlx_pre = sjlx;
                            sourceStack.push(jtnr);
                        }else if (sjlx == 3){ // 特殊项目
                            sjlx_pre = sjlx;
                            sourceStack.push(getSpecialProjectValue(jtnr,inArgument));
                        }
                    }

                    if (!sourceStack.empty()){
                        Collections.reverse(sourceStack);
                        while (!sourceStack.empty()){
                            wrapperResult += sourceStack.pop();
                        }

                        if (!StringUtils.isEmpty(wrapperResult)){
                        outArgument.saveProjects.add(new Project(String.valueOf(mbxm),wrapperResult));
                        }
                    }
                }
                break;
            case 2: // 表单
                // 表单类型（风险评估-不匹配项目，直接组装到目标项目）
                List<Project> inProject3 = inArgument.projects.get(0).saveProjects;
	            switch (inArgument.bdlx) {
	            case "2":
		            for (Project project : inProject3) {
			            String key = project.key;
			            String value = project.value;

			            for (SynchronMission mission : missions) {
				            String mbxm = String.valueOf(mission.MBXM);

				            for (SynchronSource source : mission.missionSources) {
					            String jtnr = source.JTNR;
					            String sjlx = String.valueOf(source.SJLX);
					            String lyxm = String.valueOf(source.LYXM);
					            String xmlx = String.valueOf(source.XMLX);

					            switch (sjlx) {
					            case "2":
						            wrapperResult = wrapperResult + value;
						            break;
					            case "1":
						            wrapperResult = wrapperResult + jtnr;
						            break;
					            case "3":
						            wrapperResult = wrapperResult + getSpecialProjectValue(
								            jtnr, inArgument);
						            break;
					            }
				            }
				            outArgument.saveProjects
						            .add(new Project(String.valueOf(mbxm), wrapperResult));
			            }
		            }
		            break;
	            case "1":
		            for (SynchronMission mission : missions) {
			            String mbxm = String.valueOf(mission.MBXM);

			            for (SynchronSource source : mission.missionSources) {
				            String jtnr = source.JTNR;
				            String sjlx = String.valueOf(source.SJLX);
				            String lyxm = String.valueOf(source.LYXM);
				            String xmlx = String.valueOf(source.XMLX);

				            switch (sjlx) {
				            case "2":
					            break;
				            case "1":
					            wrapperResult = wrapperResult + jtnr;
					            break;
				            case "3":
					            wrapperResult = wrapperResult + getSpecialProjectValue(jtnr,
							            inArgument);
					            break;
				            }
			            }
			            outArgument.saveProjects
					            .add(new Project(String.valueOf(mbxm), wrapperResult));
		            }
		            break;
	            default:
		            for (Project project : inProject3) {
			            String key = project.key;
			            String value = project.value;

			            for (SynchronMission mission : missions) {
				            String mbxm = String.valueOf(mission.MBXM);

				            for (SynchronSource source : mission.missionSources) {
					            String lyxm = String.valueOf(source.LYXM);
					            if (lyxm.equals(key)) {
						            outArgument.saveProjects
								            .add(new Project(String.valueOf(mbxm),
										            wrapperResult));
					            }
				            }

			            }
		            }
		            break;
	            }
                break;
            case 3: // 项目
	            for (Project inProject2 : inArgument.projects) {
		            for (Project project : inProject2.saveProjects) {
			            String key = project.key;
			            String value = project.value;

			            for (SynchronMission mission : missions) {
				            String mbxm = String.valueOf(mission.MBXM);

				            for (SynchronSource source : mission.missionSources) {
					            String lyxm = String.valueOf(source.LYXM);
					            if (lyxm.equals(key)) {
						            outArgument.saveProjects
								            .add(new Project(String.valueOf(mbxm), value));
					            }
				            }
			            }
		            }
	            }
                break;
            default:
                break;
        }

        return outArgument;
    }
    /**
     * 获取同步规则
     * @param jgid
     * @param bqdm
     * @param bdlx
     * @param lybd
     * @return
     */
    private List<Synchron> getSynchronByBQDMAndBdlx(String jgid,String bqdm,String bdlx,String lybd){
        List<Synchron> synchrons = new ArrayList<>();
        keepOrRoutingDateSource(DataSource.MOB);
        try {
            synchrons = service.getSynchronByBQDMAndBdlx(jgid,bqdm,bdlx,lybd);
        } catch (SQLException | DataAccessException e) {
            logger.error("获取同步规则失败!"+e.getMessage(),e);
        } catch (Exception e){
            logger.error("获取同步规则失败!"+e.getMessage(),e);
        }
        return synchrons;
    }

    /**
     * 获取同步记录
     * @param lylx
     * @param lyjl
     * @param lymxlx
     * @param lymx
     * @return
     * @throws SynchronWrapperException
     */
    private List<SynchronRecord> getSynchronRecordsByLylxAndLyjl(String lylx,String lyjl,String lymxlx,String lymx)throws SynchronWrapperException {
        keepOrRoutingDateSource(DataSource.MOB);
        List<SynchronRecord> list;
        try{
            list = service.getSynchronRecordsByLylxAndLyjl(lylx, lyjl, lymxlx, lymx);
        }catch (SQLException ex){
            throw new SynchronWrapperException("获取同步记录失败!");
        }catch (DataAccessException ex){
            throw new SynchronWrapperException("获取同步记录失败!");
        }

        return list;
    }

    /**
     * 获取特殊项目值
     * @param projectTag
     * @return
     */
    private String getSpecialProjectValue(String projectTag,InArgument inArgument){
        String ret = "";
	    switch (projectTag) {
	    case "[病人姓名]":
		    List<Map> patients = service.getPatientNameByZyh(inArgument.jgid, inArgument.zyh);
		    if (patients.size() > 0) {
			    ret = (String) patients.get(0).get("BRXM");
		    }
		    break;
	    case "[护士姓名]":
		    ret = dictCachedHandler
				    .getValueByKeyFromCached(CachedDictEnum.MOB_YGDM, inArgument.jgid,
						    inArgument.hsgh);
		    break;
	    case "[记录时间]":
		    ret = dateTimeService.now(DataSource.PORTAL);
		    break;
	    case "[病区名称]":
		    ret = dictCachedHandler
				    .getValueByKeyFromCached(CachedDictEnum.MOB_KSDM, inArgument.jgid,
						    inArgument.bqdm);
		    break;
	    }
        return ret ;
    }
    /**
     * 多目标表单选择策略,不确定的目标表单保存在inArgument中
     * @param inArgument
     * @return
     */
    private Boolean multiSheetsSelectStrategy(InArgument inArgument,List<Synchron> synchrons,List<Sheet> confirmSheets,List<Sheet> notConfirmSheets,Boolean isUserConfirm){
        // 用户选择的情况下：不确定同步表单不保存到inArgument中,确定的保存到confirmSheets
        //               非：不确定同步表单保存到inArgument中 ,方便返回到前端 供用户选择,确定的保存到confirmSheets
        // 不同目标类型的规则，采用不同的选择策略
        Boolean isCanConfirm = false;
        for (Synchron synchron:synchrons){
            Integer mblx = synchron.MBLX;
            Integer mbbd = synchron.MBBD;
            String mbbdmc = synchron.MBBDMC;

            // 目前只有目前记录采用策略，其他类型表单，默认全部同步
            if (mblx == 6){
                /**
                 * 北京中西医结合医院：只要有一个配置了手动同步，就确定使用手工同步
                 */
                if (synchron.TBGZ.equals(1)){
                    isCanConfirm = false;
                    break;
                }

                isCanConfirm = nurseRecordSheetStrategy(inArgument.zyh,inArgument.jgid, String.valueOf(mbbd));
            }else{
                isCanConfirm = true;
            }

            // 只要有一个可以确定，就表示该类型单，其他表单忽略选择策略成功
            if (isCanConfirm){
                confirmSheets.add(new Sheet(String.valueOf(mbbd),mbbdmc));
                break;
            }
        }

        // 最终都不可以确定的，表示选择策略失效，需用户选择
        if (!isCanConfirm && !isUserConfirm){
            for (Synchron synchron:synchrons){
                inArgument.selectSheets.add(new Sheet(String.valueOf(synchron.MBBD),synchron.MBBDMC));
            }
        }

        return isCanConfirm;
    }

    /**
     * 护理记录表单选择策略
     * 根据当前病人再护理记录表中的最后一条记录的结构编号和当前规则的目标表单比较，如果相同，就采用当前的规则，如果一条没有写，
     * @return
     */
    private Boolean nurseRecordSheetStrategy(String zyh,String jgid,String mbbd){
        Boolean isConfirm = false;
        keepOrRoutingDateSource(DataSource.ENR);
        try {
            List<Map> list = service.getLastNurseRecordOfPatient(jgid,zyh,mbbd);
            if (list.size() <=0){
                isConfirm = false;
            }else{
                String jgbh = String.valueOf(list.get(0).get("JGBH"));
                if (jgbh.equals(mbbd)){
                    isConfirm = true;
                }
            }
        }catch (SQLException e){
            logger.error("获取病区最后一条护理记录失败!" + e.getMessage(),e);
        }catch (DataAccessException e){
            logger.error("获取病区最后一条护理记录失败!" + e.getMessage(),e);
        }
        return isConfirm;
    }

	/**
	 * 组装删除操作的出参
	 * @param inArgument
	 * @return
	 */
	public List<OutArgument> wrapperOutArgumentsForDelete(InArgument inArgument)
			throws SynchronWrapperException {
		List<OutArgument> outArguments = new ArrayList<>();
		keepOrRoutingDateSource(DataSource.MOB);
		try {
			List<SynchronRecord> recordList = service
					.getSynchronRecordsByLylxAndLyjl(inArgument.bdlx, inArgument.jlxh,
							inArgument.lymxlx, inArgument.lymx);
			if (recordList == null || recordList.isEmpty()) {
				throw new SynchronWrapperException("无同步记录");
			}
			for (SynchronRecord record : recordList) {
				OutArgument outArgument = wrapperOutArgumentForDelete(inArgument, record);
				outArguments.add(outArgument);
			}
		} catch (SQLException | DataAccessException ex) {
			throw new SynchronWrapperException("获取同步记录失败!");
		}

		return outArguments;
	}

	private OutArgument wrapperOutArgumentForDelete(InArgument inArgument,
			SynchronRecord record) {
		OutArgument outArgument = new OutArgument();
		outArgument.flag = inArgument.flag;
		outArgument.lybdlx = inArgument.bdlx;
		outArgument.lyjlxh = inArgument.jlxh;
		outArgument.mbjlxh = record.MBJL;
		outArgument.bdlx = record.MBLX;
		outArgument.lymx = inArgument.lymx;
		outArgument.lymxlx = inArgument.lymxlx;
		return outArgument;
	}

	/**
     * 目标表单过滤器
     */
    class missionSheetPredicate implements Predicate {
        String bdlx = null;
        public missionSheetPredicate(String bdlx){
            this.bdlx = bdlx;
        }

        @Override
        public boolean evaluate(Object o) {
            Synchron synchron = (Synchron)o;
	        return String.valueOf(synchron.MBLX).equals(bdlx);
        }
    }

}
