package com.bsoft.nis.service.synchron.exception;

/**
 * Describtion:多目标表单，且选择策略失效异常
 * Created: dragon
 * Date： 2017/1/3.
 */
public class TooMuchSheetException extends Exception {
    public TooMuchSheetException(String msg){
        super(msg);
    }
}
