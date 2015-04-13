/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.skinny.model;

import com.liferay.portal.kernel.json.JSON;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author James Falkner
 */
public class SkinnyBaseModel {

	public void setUuid(String uuid) {
		_uuid = uuid;
	}
	public String getUuid() {
		return _uuid;
	}

	public void addField(Map<String, Object> field) {
		_fields.add(field);
	}

	@JSON
	public List<Map<String, Object>> getFields() {
		return _fields;
	}

	private List<Map<String, Object>> _fields =
		new ArrayList<Map<String, Object>>();

	private String _uuid = null;


}