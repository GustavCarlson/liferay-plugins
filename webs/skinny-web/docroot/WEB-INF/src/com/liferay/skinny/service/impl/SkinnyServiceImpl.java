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

package com.liferay.skinny.service.impl;

import com.liferay.portal.kernel.util.*;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.kernel.xml.Document;
import com.liferay.portal.kernel.xml.Element;
import com.liferay.portal.kernel.xml.Node;
import com.liferay.portal.kernel.xml.SAXReaderUtil;
import com.liferay.portal.model.Group;
import com.liferay.portal.security.ac.AccessControlled;
import com.liferay.portal.security.permission.ActionKeys;
import com.liferay.portal.security.permission.PermissionChecker;
import com.liferay.portlet.dynamicdatalists.model.DDLRecord;
import com.liferay.portlet.dynamicdatalists.model.DDLRecordSet;
import com.liferay.portlet.dynamicdatamapping.model.DDMStructure;
import com.liferay.portlet.dynamicdatamapping.storage.Field;
import com.liferay.portlet.journal.NoSuchArticleException;
import com.liferay.portlet.journal.model.JournalArticle;
import com.liferay.skinny.model.SkinnyDDLRecord;
import com.liferay.skinny.model.SkinnyJournalArticle;
import com.liferay.skinny.service.base.SkinnyServiceBaseImpl;

import java.io.Serializable;
import java.text.Format;
import java.util.*;

/**
 * @author James Falkner
 * @author Amos Fong
 */
public class SkinnyServiceImpl extends SkinnyServiceBaseImpl {

	@AccessControlled(guestAccessEnabled = true)
	@Override
	public List<SkinnyDDLRecord> getSkinnyDDLRecords(long ddlRecordSetId)
		throws Exception {

		List<SkinnyDDLRecord> skinnyDDLRecords =
			new ArrayList<SkinnyDDLRecord>();

		PermissionChecker permissionChecker = getPermissionChecker();

		DDLRecordSet ddlRecordSet = ddlRecordSetLocalService.getRecordSet(
			ddlRecordSetId);

		if (permissionChecker.hasPermission(
				ddlRecordSet.getGroupId(), DDLRecordSet.class.getName(),
				ddlRecordSet.getRecordSetId(), ActionKeys.VIEW)) {

			for (DDLRecord ddlRecord : ddlRecordSet.getRecords()) {
				SkinnyDDLRecord skinnyDDLRecord = getSkinnyDDLRecord(ddlRecord, ddlRecordSet.getDDMStructure());

				skinnyDDLRecords.add(skinnyDDLRecord);
			}
		}

		return skinnyDDLRecords;
	}

	@Override
	public SkinnyJournalArticle getSkinnyJournalArticle(
		long groupId, String articleId, int status, String locale)
		throws Exception {

		return getSkinnyJournalArticle(
			journalArticleService.getLatestArticle(groupId, articleId, status), locale);
	}


	@AccessControlled(guestAccessEnabled = true)
	@Override
	public SkinnyJournalArticle getSkinnyJournalArticle(
		long groupId, String articleId, String locale)
		throws Exception {

		JournalArticle journalArticle = journalArticleLocalService.
			getLatestArticle(groupId, articleId, WorkflowConstants.STATUS_APPROVED);

		PermissionChecker permissionChecker = getPermissionChecker();

		if (!permissionChecker.hasPermission(
			groupId, JournalArticle.class.getName(),
			journalArticle.getResourcePrimKey(), ActionKeys.VIEW)) {

			String msg = String.format("No JournalArticle exists with the key " +
					"{groupId=%d, articleId=%s, status=%d}",
				groupId, articleId, WorkflowConstants.STATUS_APPROVED);
			throw new NoSuchArticleException(msg);
		}

		return getSkinnyJournalArticle(journalArticle, locale);
	}

	@AccessControlled(guestAccessEnabled = true)
	@Override
	public List<SkinnyJournalArticle> getSkinnyJournalArticles(
			long companyId, String groupName, long ddmStructureId,
			String locale)
		throws Exception {

		List<SkinnyJournalArticle> skinnyJournalArticles =
			new ArrayList<SkinnyJournalArticle>();

		Group group = groupLocalService.getGroup(companyId, groupName);

		DDMStructure ddmStructure = ddmStructureLocalService.getDDMStructure(
			ddmStructureId);

		Set<String> journalArticleIds = new HashSet<String>();

		List<JournalArticle> journalArticles =
			journalArticleLocalService.getStructureArticles(
				group.getGroupId(), ddmStructure.getStructureKey());

		for (JournalArticle journalArticle : journalArticles) {
			if (journalArticleIds.contains(journalArticle.getArticleId())) {
				continue;
			}

			journalArticleIds.add(journalArticle.getArticleId());

			try {
				PermissionChecker permissionChecker = getPermissionChecker();

				if (permissionChecker.hasPermission(
						group.getGroupId(), JournalArticle.class.getName(),
						journalArticle.getResourcePrimKey(), ActionKeys.VIEW)) {

					JournalArticle latestJournalArticle =
						journalArticleLocalService.getLatestArticle(
							group.getGroupId(), journalArticle.getArticleId(),
							WorkflowConstants.STATUS_APPROVED);

					SkinnyJournalArticle skinnyJournalArticle =
						getSkinnyJournalArticle(latestJournalArticle, locale);

					skinnyJournalArticles.add(skinnyJournalArticle);
				}
			}
			catch (NoSuchArticleException nsae) {
			}
		}

		return skinnyJournalArticles;
	}

	protected SkinnyDDLRecord getSkinnyDDLRecord(DDLRecord ddlRecord, DDMStructure ddmStructure)
			throws Exception {

		SkinnyDDLRecord skinnyDDLRecord = new SkinnyDDLRecord();
		skinnyDDLRecord.setUuid(ddlRecord.getUuid());

		for (String fieldName : ddmStructure.getFieldNames()) {
			if (ddmStructure.isFieldPrivate(fieldName)) {
				continue;
			}

			if (Validator.isNotNull(ddmStructure.getFieldProperty(fieldName, "_parentName_"))) {
				continue;
			}

			Field field = ddlRecord.getField(fieldName);
			skinnyDDLRecord.addField(getFieldObject(fieldName, field, ddlRecord, ddmStructure));
		}

		return skinnyDDLRecord;
	}

	protected Map<String, Object> getFieldObject(String fieldName, Field field, DDLRecord ddlRecord, DDMStructure ddmStructure) throws Exception {

		Map<String, Object> fieldObj = new HashMap<String, Object>();

		fieldObj.put("name", fieldName);
		fieldObj.put("value", getFieldValue(field));
		fieldObj.put("children", getFieldChildren(fieldName, field, ddlRecord, ddmStructure));
		return fieldObj;
	}

	protected List<Object> getFieldChildren(String fieldName, Field field, DDLRecord ddlRecord, DDMStructure ddmStructure) throws Exception {
		List<Object> children = new ArrayList<Object>();

		for (String childFieldName: ddmStructure.getChildrenFieldNames(fieldName)) {
			Field childField = ddlRecord.getField(childFieldName);
			children.add(getFieldObject(childFieldName, childField, ddlRecord, ddmStructure));
		}

		return children;
	}

	protected Object getFieldValue(Field field) throws Exception {

		String fieldDataType = GetterUtil.getString(field.getDataType());

		if (!field.isRepeatable()) {
			return getStringValue(fieldDataType, field.getValue());
		}

		List<Object> vals = new ArrayList<Object>();
		for (Serializable value : field.getValues(Locale.getDefault())) {
			vals.add(value);
		}

		return vals;
	}

	protected String getStringValue(String fieldDataType, Serializable fieldValue) {

		String fieldValueString = StringPool.BLANK;

		if (fieldDataType.equals("boolean")) {
			boolean booleanValue = GetterUtil.getBoolean(fieldValue);

			fieldValueString = String.valueOf(booleanValue);
		}
		else if (fieldDataType.equals("date")) {
			fieldValueString = _format.format(fieldValue);
		}
		else if (fieldDataType.equals("double")) {
			double doubleValue = GetterUtil.getDouble(fieldValue);

			fieldValueString = String.valueOf(doubleValue);
		}
		else if (fieldDataType.equals("integer") ||
				fieldDataType.equals("number")) {

			int intValue = GetterUtil.getInteger(fieldValue);

			fieldValueString = String.valueOf(intValue);
		}
		else {
			fieldValueString = GetterUtil.getString(fieldValue);
		}
		return fieldValueString;
	}


	protected SkinnyJournalArticle getSkinnyJournalArticle(
			JournalArticle journalArticle, String locale)
		throws Exception {

		SkinnyJournalArticle skinnyJournalArticle = new SkinnyJournalArticle();

		skinnyJournalArticle.setUuid(journalArticle.getUuid());
		skinnyJournalArticle.setTitle(journalArticle.getTitle(locale));

		String content = null;

		if (ArrayUtil.contains(journalArticle.getAvailableLocales(), locale)) {
			content = journalArticle.getContentByLocale(locale);
		}
		else {
			content = journalArticle.getContent();
		}

		Document document = SAXReaderUtil.read(content);

		Element rootElement = document.getRootElement();

		populateSkinnyJournalArticle(skinnyJournalArticle, rootElement);

		return skinnyJournalArticle;
	}

	protected void populateSkinnyJournalArticle(
		SkinnyJournalArticle skinnyJournalArticle, Element parentElement) {

		for (Map<String, Object> field : getNodeObjects(parentElement)) {
			skinnyJournalArticle.addField(field);
		}
	}


	protected List<Map<String, Object>> getNodeObjects(Element parent)  {

		List<Map<String, Object>> children = new ArrayList<Map<String, Object>>();
		for (Node child : parent.selectNodes("dynamic-element")) {
			children.add(getNodeObject((Element)child));
		}
		return children;



	}
	protected Map<String, Object> getNodeObject(Element element)  {

		Map<String, Object> fieldObj = new HashMap<String, Object>();

		fieldObj.put("name", element.attributeValue("name"));
		fieldObj.put("value", element.element("dynamic-content").getTextTrim());

		List<Map<String, Object>> children = new ArrayList<Map<String, Object>>();

		for (Node child: element.selectNodes("dynamic-element")) {
			children.add(getNodeObject((Element)child));
		}

		fieldObj.put("children", children);
		return fieldObj;
	}


	private Format _format = FastDateFormatFactoryUtil.getSimpleDateFormat(
		"yyyy-MM-dd");

}