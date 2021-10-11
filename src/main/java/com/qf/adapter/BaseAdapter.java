package com.qf.adapter;

import java.util.logging.Logger;

public class BaseAdapter {
	
	static UrlToMap urlToMap = new GsonUrlToMap();
	
	static Logger log = Logger.getLogger("BaseAdapter");
	
	static boolean sendIPAddressToAdapter = true;
	
	static boolean usesSecrets = true;
	
	public String getSettingsURL() {
        return QueueFairConfig.protocol + "://" + QueueFairConfig.filesServer + "/"
                + QueueFairConfig.account + "/" + QueueFairConfig.accountSecret + "/queue-fair-settings.json";

    }
}