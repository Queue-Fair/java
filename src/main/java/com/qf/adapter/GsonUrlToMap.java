package com.qf.adapter;

import java.io.Reader;
import java.util.Map;

import com.google.gson.Gson;


public class GsonUrlToMap extends UrlToMap {

	
	public Map<String,Object> urlToMap(String url) {
		
		Reader r = loadURL(url);
		if(r==null)
			return null;
		
		Gson gson = new Gson();
		
		return gson.fromJson(r, Map.class);
	
	}
	
}
