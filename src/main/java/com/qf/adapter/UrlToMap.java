package com.qf.adapter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;


public abstract class UrlToMap {

	static SSLSocketFactory fact = null;

	static TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
		public java.security.cert.X509Certificate[] getAcceptedIssuers() {
			return null;
		}

		public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
		}

		public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
		}
	} };
	
	static {
		try {
			SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			fact = sc.getSocketFactory();
		} catch (Exception e) {
		}
	}
	
	static Logger log = Logger.getLogger("UrlToMap");
	
	public abstract Map<String,Object> urlToMap(String url);
	
	public Reader loadURL(String url) {
		try {
			URL where = new URL(url);

			HttpURLConnection yc = null;

			if ("https".equals(where.getProtocol())) {
				HttpsURLConnection ycs = (HttpsURLConnection) where.openConnection();

				ycs.setSSLSocketFactory(fact);
				yc = ycs;
			} else {
				yc = (HttpURLConnection) where.openConnection();
			}
			yc.setConnectTimeout(QueueFairConfig.readTimeoutSeconds * 1000);
			yc.setReadTimeout(QueueFairConfig.readTimeoutSeconds * 1000);
			yc.connect();

			BufferedReader in = new BufferedReader(new InputStreamReader(yc.getInputStream()));
			return in;

		} catch (Exception e) {
			QueueFairAdapter.log.log(Level.WARNING, "QF Exception reading from " + url, e);
		}
		return null;
	}

}
