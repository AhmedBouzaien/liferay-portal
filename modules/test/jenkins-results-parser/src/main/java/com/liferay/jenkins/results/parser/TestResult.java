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

package com.liferay.jenkins.results.parser;

import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import org.dom4j.Element;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * @author Leslie Wong
 * @author Yi-Chen Tsai
 */
public class TestResult {

	public static List<TestResult> getTestResults(
		Build build, JSONArray suitesJSONArray, String testStatus) {

		List<TestResult> testResults = new ArrayList<>();

		for (int i = 0; i < suitesJSONArray.length(); i++) {
			JSONObject suiteJSONObject = suitesJSONArray.getJSONObject(i);

			JSONArray casesJSONArray = suiteJSONObject.getJSONArray("cases");

			for (int j = 0; j < casesJSONArray.length(); j++) {
				TestResult testResult = new TestResult(
					build, casesJSONArray.getJSONObject(j));

				if ((testStatus == null) ||
					testStatus.equals(testResult.getStatus())) {

					testResults.add(testResult);
				}
			}
		}

		return testResults;
	}

	public TestResult(Build build, JSONObject caseJSONObject) {
		if (build == null) {
			throw new IllegalArgumentException("Build may not be null");
		}

		this.build = build;

		className = caseJSONObject.getString("className");

		duration = (long)(caseJSONObject.getDouble("duration") * 1000D);

		int x = className.lastIndexOf(".");

		try {
			simpleClassName = className.substring(x + 1);

			packageName = className.substring(0, x);
		}
		catch (StringIndexOutOfBoundsException sioobe) {
			packageName = className;
			simpleClassName = className;

			System.out.println(
				"Invalid test class name \"" + className + "\" in build " +
					build.getBuildURL());
		}

		testName = caseJSONObject.getString("name");

		status = caseJSONObject.getString("status");

		if (status.equals("FAILED") && caseJSONObject.has("errorDetails") &&
			caseJSONObject.has("errorStackTrace")) {

			errorDetails = caseJSONObject.optString("errorDetails");
			errorStackTrace = caseJSONObject.optString("errorStackTrace");
		}
	}

	public Build getBuild() {
		return build;
	}

	public String getClassName() {
		return className;
	}

	public String getConsoleOutputURL(String testrayLogsURL) {
		StringBuilder sb = new StringBuilder();

		sb.append(testrayLogsURL);
		sb.append("/jenkins-console.txt.gz");

		return sb.toString();
	}

	public String getDisplayName() {
		if (testName.startsWith("test[")) {
			return testName.substring(5, testName.length() - 1);
		}

		return simpleClassName + "." + testName;
	}

	public long getDuration() {
		return duration;
	}

	public Element getGitHubElement() {
		return getGitHubElement(null);
	}

	public Element getGitHubElement(String testrayLogsURL) {
		String testReportURL = getTestReportURL();

		Element downstreamBuildListItemElement = Dom4JUtil.getNewElement(
			"div", null);

		downstreamBuildListItemElement.add(
			Dom4JUtil.getNewAnchorElement(testReportURL, getDisplayName()));

		if ((testrayLogsURL != null) &&
			testReportURL.contains("com.liferay.poshi.runner/PoshiRunner")) {

			Dom4JUtil.addToElement(
				downstreamBuildListItemElement, " - ",
				Dom4JUtil.getNewAnchorElement(
					getPoshiReportURL(testrayLogsURL), "Poshi Report"),
				" - ",
				Dom4JUtil.getNewAnchorElement(
					getPoshiSummaryURL(testrayLogsURL), "Poshi Summary"),
				" - ",
				Dom4JUtil.getNewAnchorElement(
					getConsoleOutputURL(testrayLogsURL), "Console Output"));

			if (errorDetails != null) {
				Dom4JUtil.addToElement(
					Dom4JUtil.toCodeSnippetElement(errorDetails));
			}

			if (hasLiferayLog(testrayLogsURL)) {
				Dom4JUtil.addToElement(
					downstreamBuildListItemElement, " - ",
					Dom4JUtil.getNewAnchorElement(
						getLiferayLogURL(testrayLogsURL), "Liferay Log"));
			}
		}
		else if (errorStackTrace != null) {
			String trimmedStackTrace = StringUtils.abbreviate(
				errorStackTrace, _MAX_ERROR_STACK_DISPLAY_LENGTH);

			downstreamBuildListItemElement.add(
				Dom4JUtil.toCodeSnippetElement(trimmedStackTrace));
		}

		return downstreamBuildListItemElement;
	}

	public String getLiferayLogURL(String testrayLogsURL) {
		StringBuilder sb = new StringBuilder();

		String name = getDisplayName();

		sb.append(testrayLogsURL);
		sb.append("/");
		sb.append(name.replace("#", "_"));
		sb.append("/liferay-log.txt.gz");

		return sb.toString();
	}

	public String getPackageName() {
		return packageName;
	}

	public String getPoshiReportURL(String testrayLogsURL) {
		StringBuilder sb = new StringBuilder();

		String name = getDisplayName();

		sb.append(testrayLogsURL);
		sb.append("/");
		sb.append(name.replace("#", "_"));
		sb.append("/index.html.gz");

		return sb.toString();
	}

	public String getPoshiSummaryURL(String testrayLogsURL) {
		StringBuilder sb = new StringBuilder();

		String name = getDisplayName();

		sb.append(testrayLogsURL);
		sb.append("/");
		sb.append(name.replace("#", "_"));
		sb.append("/summary.html.gz");

		return sb.toString();
	}

	public String getStatus() {
		return status;
	}

	public String getTestName() {
		return testName;
	}

	public String getTestReportURL() {
		StringBuilder sb = new StringBuilder();

		sb.append(build.getBuildURL());
		sb.append("/testReport/");
		sb.append(packageName);
		sb.append("/");
		sb.append(simpleClassName);
		sb.append("/");

		String encodedTestName = testName;

		encodedTestName = encodedTestName.replace("[", "_");
		encodedTestName = encodedTestName.replace("]", "_");
		encodedTestName = encodedTestName.replace("#", "_");

		if (packageName.equals("junit.framework")) {
			encodedTestName = encodedTestName.replace(".", "_");
		}

		sb.append(encodedTestName);

		return sb.toString();
	}

	public boolean hasLiferayLog(String testrayLogsURL) {
		String liferayLog = null;

		try {
			liferayLog = JenkinsResultsParserUtil.toString(
				getLiferayLogURL(testrayLogsURL), false, 0, 0, 0);
		}
		catch (IOException ioe) {
			return false;
		}

		return !liferayLog.isEmpty();
	}

	protected Build build;
	protected String className;
	protected long duration;
	protected String errorDetails;
	protected String errorStackTrace;
	protected String packageName;
	protected String simpleClassName;
	protected String status;
	protected String testName;

	private static final int _MAX_ERROR_STACK_DISPLAY_LENGTH = 1500;

}