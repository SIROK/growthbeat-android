package com.growthpush.model;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import com.growthbeat.model.Model;
import com.growthbeat.utils.JSONObjectUtils;
import com.growthpush.GrowthPush;

public class Event extends Model {

	private int goalId;

	private String clientId;

	private long timestamp;

	private String value;

	public Event() {
		super();
	}

	public Event(JSONObject jsonObject) {
		this();
		setJsonObject(jsonObject);
	}

	public static Event create(String clientId, String applicationId, String credentialId, EventType type, String name, String value) {

		Map<String, Object> params = new HashMap<String, Object>();
		if (clientId != null)
			params.put("clientId", clientId);
		if (applicationId != null)
			params.put("applicationId", applicationId);
		if (credentialId != null)
			params.put("credentialId", credentialId);
		if (type != null)
			params.put("type", type.toString());
		if (name != null)
			params.put("name", name);
		if (value != null)
			params.put("value", value);

		JSONObject jsonObject = GrowthPush.getInstance().getHttpClient().post("4/events", params);
		if (jsonObject == null)
			return null;

		return new Event(jsonObject);

	}

	public int getGoalId() {
		return goalId;
	}

	public void setGoalId(int goalId) {
		this.goalId = goalId;
	}

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	@Override
	public JSONObject getJsonObject() {
		JSONObject jsonObject = new JSONObject();

		try {
			jsonObject.put("goalId", getGoalId());
			jsonObject.put("clientId", getClientId());
			jsonObject.put("timestamp", getTimestamp());
			if (value != null)
				jsonObject.put("value", getValue());
		} catch (JSONException e) {
			return null;
		}

		return jsonObject;
	}

	@Override
	public void setJsonObject(JSONObject jsonObject) {

		if (jsonObject == null)
			return;

		try {
			if (JSONObjectUtils.hasAndIsNotNull(jsonObject, "goalId"))
				setGoalId(jsonObject.getInt("goalId"));
			if (JSONObjectUtils.hasAndIsNotNull(jsonObject, "clientId"))
				setClientId(jsonObject.getString("clientId"));
			if (JSONObjectUtils.hasAndIsNotNull(jsonObject, "timestamp"))
				setTimestamp(jsonObject.getLong("timestamp"));
			if (JSONObjectUtils.hasAndIsNotNull(jsonObject, "value"))
				setValue(jsonObject.getString("value"));
		} catch (JSONException e) {
			throw new IllegalArgumentException("Failed to parse JSON.");
		}
	}

	public enum EventType {
		custom,
		message
	}

}
