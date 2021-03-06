package com.sail.simonli.server.controller;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import com.sail.simonli.server.gary.Utils;
import com.sail.simonli.server.model.ErrorLog;
import com.sail.simonli.server.model.UserInfo;
import com.sail.simonli.server.service.ErrorLogService;
import com.sail.simonli.server.service.LoginsService;
import com.sail.simonli.server.service.UserService;
import com.sail.simonli.server.util.CommUtil;
import com.sail.simonli.server.util.Result;
import com.sail.simonli.server.util.SmsUtil;
import com.sail.simonli.server.util.StringUtil;
import com.sail.simonli.server.weixinapi.entity.Response;

@RestController
@RequestMapping("/user")
@EnableAsync  
public class UserController {
	
	private Logger logger=Logger.getLogger(UserController.class);
	
	@Autowired
	private UserService service;	
	
	@Autowired
	private LoginsService loginService;
	
	@Autowired
	private ErrorLogService errorLogService;
	
	
    @RequestMapping(value = "/register", method = RequestMethod.POST)
    public Result register(HttpServletRequest req, HttpServletResponse resp) {
    	
    	Result result = new Result();
    	
    	result.setStatus("1");
    	
    	try {
    		
    		HttpSession session = req.getSession();
    		
    		Response response = (Response) session.getAttribute("response");
    		
    		String openid = null;
    		
    		String nickname = null;
    		
    		String headimgurl = null;
    		
    		if(response!=null) {
    			
    			openid = response.getOpenid();
        		
        		nickname = response.getNickname();
        		
        		headimgurl = response.getHeadimgurl();
    		}
    		
    		
    		
    		String mobile = req.getParameter("mobile")==null?"":req.getParameter("mobile").toString();
    		
    		String smsCode = req.getParameter("smsCode")==null?"":req.getParameter("smsCode").toString();
    		
    		if (StringUtil.isEmpty(mobile)) {
				
    			result.setStatus("0");
    			
    			result.setMessage("手机号不能为空，请重新输入");
    			
    			return result;
				 
			}
    		
    		if (StringUtil.isEmpty(smsCode)) {
    				
    			result.setStatus("0");
    			
    			result.setMessage("验证码不能为空，请重新输入");
    			
    			return result;
				 
			}
    		
    		//String code="1234";
			  String code=session.getAttribute("smscode"+mobile)==null?"":session.getAttribute("smscode"+mobile).toString();
			  session.removeAttribute("smscode"+mobile);
			
			if(!smsCode.equals(code)){
				
				result.setStatus("0");
    			
    			result.setMessage("验证码错误，请重新输入");
    			
    			return result;
				 
			}
    		
    		UserInfo user = service.loadUserByMobileFromDB(mobile);//从本地DB中加数据    		
    		
    		if(user==null) {
    			
    			UserInfo userInfo = new UserInfo();
    			
    			userInfo.setMobile(mobile);
    			
    			userInfo.setOpenid(openid);
    			
    			userInfo.setNickname(nickname);
    			
    			userInfo.setHeadimgurl(headimgurl);
    			
    			Utils.query(userInfo);
    			
    			loginService.register(userInfo, resp);
    			
    			
    			service.saveSf(req, userInfo); //如果己有，就更新OPENID,如果没有，就更新手机号和OPENID
    			
    			session.setAttribute("user", userInfo);
    			
    			result.setData(userInfo);
    			
    		}else {
    			
    			result.setStatus("0");
    			
    			result.setMessage("手机号已注册，请重新输入手机号");
    			
    		}
    		
			
		} catch (Exception e) {
			
			logger.error("UserController-register:"+e.getMessage());
			
			e.printStackTrace();
			
			result.setStatus("0");
			
			result.setMessage(e.getMessage());
			
		}
    	
    	return result;
    }
    
    
    @GetMapping(value = "/sendSms/{mobile}")
    public Result sendSms(@PathVariable("mobile") String mobile,HttpServletRequest req) {
    	
    	Result result = new Result();
    	
    	result.setStatus("1");
    	
        String msgcontent="验证码：{rad}。请勿泄露该验证码。【西蒙李家居】";
        
		String rad=CommUtil.getRandNum(6);
		
		msgcontent=msgcontent.replace("{rad}", rad);//处理短信消息内容
		
    	try {
    		
			String smsResult = SmsUtil.sendSms(mobile, msgcontent);
			
			if(smsResult.indexOf("<State>0</State>") < 0){
				 ErrorLog record = new ErrorLog();
	    		 record.setApiName("短信接口");
	    		 record.setErrorMsg(smsResult);
	    		 record.setSolution("接口通，发送的内容被拦，请联系短信服务商");
	    		 errorLogService.insertErrorLog(record );
			}
			
			req.getSession().setAttribute("smscode"+mobile,rad);
			
		} catch (Exception e) {
			
			 ErrorLog record = new ErrorLog();
    		 record.setApiName("短信接口");
    		 record.setErrorMsg(e.getMessage());
    		 record.setSolution("接口调用不通，联系短信服务商");
    		 errorLogService.insertErrorLog(record );
    		
			e.printStackTrace();			
			result.setStatus("0");			
			result.setMessage(e.getMessage());
		}
    	
    	return result;
    	
    }
    
    @RequestMapping(value = "/findById", method = RequestMethod.POST)
    public Result findById(HttpServletRequest req, HttpServletResponse resp) {
    	
    	Result result = new Result();
    	
    	result.setStatus("1");
    	
    	HttpSession session = req.getSession();
    	
    	UserInfo user = (UserInfo)session.getAttribute("user");
    	
    	user = service.loadUserByMobile(user.getMobile());
    	
    	
    	if(user != null && user.getId() != null && !"".equals(user.getId())){
    	    service.save(req, user);// 从SF中获取最新的用户信息，并更新到－－本地DB中。
    	}
    	result.setData(user);
    	
    	return result;
    	
    }
    
    @RequestMapping(value = "/save", method = RequestMethod.POST)
    public Result save(UserInfo user,HttpServletRequest req, HttpServletResponse resp) {
    	
    	Result result = new Result();
    	
    	result.setStatus("1");
    	try {
    		
    		 if(user.getName()!=null&&user.getCity()!=null&&user.getCountry()!=null&&user.getBirthday()!=null&&user.getSex()!=null) {
    			user.setFinish("1");
    		 }else {
    			user.setFinish("0");
    		 }
    		
    		 UserInfo userDB = service.selectByPrimaryKey(user.getId());
    		 
    		 user.setMobile(userDB.getMobile());
    		 user.setOpenid(userDB.getOpenid());
    		 
    		 service.saveSf(req, user);
    		
    		 Thread.sleep(500);
    		 service.save(req,user);
    		
    		
    	}catch(Exception e) {
    		
    		result.setStatus("0");
    		
    		result.setMessage(e.getMessage());
    		
    		e.printStackTrace();
    		
    		logger.error("save:"+e.getMessage());
    		
    	}
    	
    	return result;
    	
    }
    

    @InitBinder
	public void initBinder(WebDataBinder binder, WebRequest request) {
		
		//转换日期
		DateFormat dateFormat=new SimpleDateFormat("yyyy-MM-dd");
		binder.registerCustomEditor(Date.class, new CustomDateEditor(dateFormat, true));// CustomDateEditor为自定义日期编辑器
	}
    
}
