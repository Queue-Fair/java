package com.qf.adapter;

import java.util.List;
import java.util.Map;

public class QueueFairSettings {

	public class Queue {

		public String name = null;
		public String displayName = null;
		public String adapterMode = null;
		public String adapterServer = null;
		public String queueServer = null;
		public String cookieDomain = null;
		public String dynamicTarget = null;
		public String secret = null;
		public int passedLifetimeMinutes = 60;
		public Rule[] rules = new Rule[0];
		public Variant[] variantRules = new Variant[0];

		public Queue(Map<String, Object> json) {

			name = QueueFairAdapter.getStr(json, "name");
			displayName = QueueFairAdapter.getStr(json, "displayName");
			adapterMode = QueueFairAdapter.getStr(json, "adapterMode");
			adapterServer = QueueFairAdapter.getStr(json, "adapterServer");
			queueServer = QueueFairAdapter.getStr(json, "queueServer");
			cookieDomain = QueueFairAdapter.getStr(json, "cookieDomain");
			dynamicTarget = QueueFairAdapter.getStr(json, "dynamicTarget");
			passedLifetimeMinutes = QueueFairAdapter.getInt(json, "passedLifetimeMinutes");
			secret = QueueFairAdapter.getStr(json, "secret");

			if (json.get("activation") == null) {
				return;
			}

			Object obj = json.get("activation");
			if (obj == null) {
				return;
			}

			if (!(obj instanceof Map)) {
				return;
			}

			List<Map> rulesList = (List<Map>) ((Map) obj).get("rules");
			if (rulesList != null) {
				rules = new Rule[rulesList.size()];
				for (int i = 0; i < rulesList.size(); i++) {
					rules[i] = new Rule(rulesList.get(i));
				}
			}

			List<Map<String, Object>> variantRulesList = (List<Map<String, Object>>) ((Map<String, Object>) obj)
					.get("variantRules");

			if (variantRulesList != null) {
				variantRules = new Variant[variantRulesList.size()];
				for (int i = 0; i < variantRulesList.size(); i++) {
					variantRules[i] = new Variant(variantRulesList.get(i));
				}
			}
		}
	}

	public class Variant {
		public String variant = null;
		public Rule[] rules = new Rule[0];

		public Variant(Map<String, Object> json) {
			variant = QueueFairAdapter.getStr(json, "variant");
			List<Map<String, Object>> rulesList = (List<Map<String, Object>>) json.get("rules");
			if (rulesList != null) {
				rules = new Rule[rulesList.size()];
				for (int i = 0; i < rulesList.size(); i++) {
					rules[i] = new Rule(rulesList.get(i));
				}
			}
		}
	}

	public class Rule {
		public String operator = null;
		public String component = null;
		public String name = null;
		public String value = null;
		public String match = null;
		public boolean caseSensitive = false;
		public boolean negate = false;

		public Rule(Map<String, Object> json) {
			operator = QueueFairAdapter.getStr(json, "operator");
			component = QueueFairAdapter.getStr(json, "component");
			name = QueueFairAdapter.getStr(json, "name");
			value = QueueFairAdapter.getStr(json, "value");
			caseSensitive = QueueFairAdapter.getBool(json, "caseSensitive");
			negate = QueueFairAdapter.getBool(json, "negate");
			match = QueueFairAdapter.getStr(json, "match");
		}
	}

	public Queue[] queues = new Queue[0];

	public QueueFairSettings(Map<String, Object> json) {
		List<Map<String, Object>> queuesList = (List<Map<String, Object>>) json.get("queues");
		if (queuesList == null)
			return;
		queues = new Queue[queuesList.size()];

		for (int i = 0; i < queuesList.size(); i++) {
			queues[i] = new Queue(queuesList.get(i));
		}
	}

}
