package com.tihonchik.lenonhonor360.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import android.util.Log;

import com.tihonchik.lenonhonor360.AppDefines;
import com.tihonchik.lenonhonor360.models.BlogEntry;
import com.tihonchik.lenonhonor360.util.BlogEntryUtils;

public class HtmlParser implements HtmlPatterns {

	public static String parseBlog() throws MalformedURLException, IOException {
		String originalHtml = getHtml(AppDefines.LENONHONOR_APP_PHP_WEBSITE_URI);
		String noWiteSpaceHtml = originalHtml.replaceAll("[\\n\\t]", "")
				.replaceAll(" +", " ").replaceAll(" ", "_");
		Matcher m = hrefPattern.matcher(noWiteSpaceHtml);
		List<String> hrefList = new ArrayList<String>();
		List<String> titleList = new ArrayList<String>();
		while (m.find()) {
			hrefList.add(m.group(0));
			titleList.add(m.group(2).replaceAll("_", " ").trim());
		}

		int lastWebBlogId = -1;
		if (hrefList.size() > 0) {
			m = idPattern.matcher(hrefList.get(0));
			if (m.find()) {
				lastWebBlogId = Integer.valueOf(m.group(1));
			}
		}

		String responseValue = null;
		List<BlogEntry> entries = new ArrayList<BlogEntry>();
		int lastDbBlogId = BlogEntryUtils.getNewestBlogId();
		Log.d("LH360", "START: BLOG:" + lastDbBlogId + " WEB:" + lastWebBlogId);
		if (lastDbBlogId == -1 && lastWebBlogId == -1) {
			responseValue = AppDefines.BLOG_UNAVAILABLE;
		} else if (lastDbBlogId != -1 && lastWebBlogId == -1) {
			// Something went wrong with parsing, load all DB entries
			// Do nothing, as if there are no updates
		} else if (lastDbBlogId == -1 && lastWebBlogId != -1) {
			Log.d("LH360", "EmptyDB");
			// insert all entries, DB has nothing in it
			entries = parseAllEntries(hrefList, titleList, noWiteSpaceHtml,
					lastDbBlogId);
			Log.d("LH360", "INSERT FULL:" + entries.size());
			BlogEntryUtils.insertBlogEntries(entries);
			responseValue = AppDefines.ISSUE_NOTIFICAION;
		} else {
			if (lastWebBlogId > lastDbBlogId) {
				Log.d("LH360", "WEB:" + lastWebBlogId + " DB:" + lastDbBlogId
						+ " DIFF:" + (lastWebBlogId - lastDbBlogId));
				// insert new web entries into DB
				entries = parseAllEntries(hrefList, titleList, noWiteSpaceHtml,
						lastDbBlogId);
				Log.d("LH360", "INSERT PARTIAL:" + entries.size());
				BlogEntryUtils.insertBlogEntries(entries);
				responseValue = AppDefines.ISSUE_NOTIFICAION;
			} else if (lastWebBlogId < lastDbBlogId) {
				Log.d("LH360", "DELETE????");
				// entry got deleted from the blog, remove from DB
				while (lastDbBlogId > lastWebBlogId) {
					BlogEntryUtils.deleteBlogEntry(lastDbBlogId);
					lastDbBlogId--;
				}
			}
			// figure out if any of entries in middle were deleted
			List<Integer> ids = BlogEntryUtils.getAllBlogIds();
			Log.d("LH360",
					"WEB SIZE:" + hrefList.size() + " DB SIZE:" + ids.size());
			if (ids.size() > hrefList.size()) {
				removeDeletedBlogs(hrefList, ids);
			} else if (ids.size() < hrefList.size()) {
				entries = parseMissingEntries(hrefList, titleList,
						noWiteSpaceHtml);
				addMissingBlogs(entries, ids);
			}
		}
		return responseValue;
	}

	private static void removeDeletedBlogs(List<String> links,
			List<Integer> dbIds) {
		Matcher m = null;
		List<Integer> blogIds = new ArrayList<Integer>();
		for (int i = 0; i < links.size(); i++) {
			m = idPattern.matcher(links.get(i));
			if (m.find()) {
				Log.d("LH360", "BLOG ID:" + m.group(1));
				blogIds.add(Integer.valueOf(m.group(1)));
			}
		}
		for (Integer dbId : dbIds) {
			Log.d("LH360", "DB ID:" + dbId);
			if (!blogIds.contains(dbId)) {
				BlogEntryUtils.deleteBlogEntry(dbId);
			}
		}
	}

	private static void addMissingBlogs(List<BlogEntry> entries,
			List<Integer> dbIds) {
		for (int i = 0; i < entries.size(); i++) {
			if (!dbIds.contains(entries.get(i).getId())) {
				Log.d("LH360", "INSERT BLOG ID:" + entries.get(i).getId());
				BlogEntryUtils.insertBlogEntry(entries.get(i));
			}
		}
	}

	private static String parseEntryWithId(int id) throws IOException {
		String blogText = "";
		Matcher m = null;
		String entryHtml = getHtml(AppDefines.LENONHONOR_APP_PHP_ENTRY_URI + id)
				.replaceAll("[\\n\\t]", "").replaceAll(" +", " ")
				.replaceAll(" ", "_");
		if (entryHtml.contains("<img_src")) {
			m = textWithImagePattern.matcher(entryHtml);
			if (m.find()) {
				String paragraphedText = m.group(2);
				Matcher mP = paragraphPattern.matcher(paragraphedText);
				int paragraphsFound = 0;
				while (mP.find()) {
					blogText += mP.group(1).replaceAll("<p>", "")
							.replaceAll("</p>", "").replaceAll("_", " ").trim();
					paragraphsFound++;
				}
				if (paragraphsFound == 0) {
					blogText = m.group(4).replaceAll("<div>", "")
							.replaceAll("</div>", "").replaceAll("_", " ")
							.trim();
				}
			}
			m = imageSourcePattern.matcher(entryHtml);
			while (m.find()) {
				BlogEntryUtils.insertImage(id, m.group(2).trim());
			}
		} else {
			m = textWithNoImagePattern.matcher(entryHtml);
			if (m.find()) {
				blogText = m.group(2).replaceAll("_", " ").trim();
			}
		}
		return blogText;
	}

	private static List<BlogEntry> parseAllEntries(List<String> links,
			List<String> titles, String html, int lastDbBlogId)
			throws IOException {
		List<BlogEntry> entries = new ArrayList<BlogEntry>();
		List<String> dates = new ArrayList<String>();
		Matcher m = datePattern.matcher(html);
		while (m.find()) {
			dates.add(m.group(2).replaceAll("_", " ").trim());
		}
		for (int i = 0; i < titles.size(); i++) {
			m = idPattern.matcher(links.get(i));
			if (m.find()) {
				int id = Integer.valueOf(m.group(1));
				if (id > lastDbBlogId) {
					BlogEntry entry = new BlogEntry(id);
					entry.setBlogDate(dates.get(i));
					entry.setTitle(titles.get(i));
					entry.setBlog(parseEntryWithId(id));
					entries.add(entry);
				} else if (id == lastDbBlogId) {
					break;
				}
			}
		}
		return entries;
	}

	private static List<BlogEntry> parseMissingEntries(List<String> links,
			List<String> titles, String html) throws IOException {
		List<BlogEntry> entries = new ArrayList<BlogEntry>();
		List<String> dates = new ArrayList<String>();
		Matcher m = datePattern.matcher(html);
		while (m.find()) {
			dates.add(m.group(2).replaceAll("_", " ").trim());
		}
		for (int i = 0; i < titles.size(); i++) {
			m = idPattern.matcher(links.get(i));
			if (m.find()) {
				int id = Integer.valueOf(m.group(1));
				BlogEntry entry = new BlogEntry(id);
				entry.setBlogDate(dates.get(i));
				entry.setTitle(titles.get(i));
				entry.setBlog(parseEntryWithId(id));
				entries.add(entry);
			}
		}
		return entries;
	}

	private static String getHtml(String uri) throws MalformedURLException,
			IOException {
		URL url = new URL(uri);
		URLConnection conn = url.openConnection();
		InputStreamReader streamReader = new InputStreamReader(
				conn.getInputStream());
		BufferedReader br = new BufferedReader(streamReader);
		StringBuilder sb = new StringBuilder();
		String line = null;
		while ((line = br.readLine()) != null) {
			sb.append(line);
			sb.append("\n");
		}
		return sb.toString();
	}
}
