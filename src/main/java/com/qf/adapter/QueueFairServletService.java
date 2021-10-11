package com.qf.adapter;

import java.util.HashMap;
import java.util.logging.Logger;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class QueueFairServletService implements QueueFairService {

	private HttpServletRequest request;
	private HttpServletResponse response;
	
	private boolean isSecure = false;
	
	private HashMap<String,String> cookies=null;
	
	public QueueFairServletService(HttpServletRequest request, HttpServletResponse response) {
		this.request=request;
		this.response=response;
		if(request.getScheme().startsWith("https")) {
			isSecure=true;
		}
	}
	
	@Override
	public void setCookie(String name, String value, int lifetimeSeconds, String domain) {
		Cookie c=new Cookie(name, value);
		if(isSecure) {
			c.setSecure(true);
		}
		c.setMaxAge(lifetimeSeconds);
		c.setPath("/");
		
		if(domain!=null && !"".equals(domain)) {
			c.setDomain(domain);
		}

		response.addCookie(c);
	}

	@Override
	public void redirect(String location) {
        try {
        	response.sendRedirect(location);
        } catch (Exception e) {
        	//ignored.
        }
	}

	@Override
	public String getCookie(String name) {
		if(cookies==null) {
			cookies = new HashMap<String,String>();
			Cookie[] cArr = request.getCookies();
			if(cArr==null || cArr.length ==0) {
				return "";
			}
			
			for(Cookie c : cArr) {
				cookies.put(c.getName(), c.getValue());
			}
		}
		
		String ret=cookies.get(name);
		if(ret==null) {
			return "";
		}
		return ret;
	}
	
	public void addHeader(String name, String value) {
		response.addHeader(name, value);
	}
}
