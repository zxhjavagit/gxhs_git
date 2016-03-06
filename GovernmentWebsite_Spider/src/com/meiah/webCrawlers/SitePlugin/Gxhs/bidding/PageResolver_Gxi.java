package com.meiah.webCrawlers.SitePlugin.Gxhs.bidding;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Document;

import com.gxhs.mongodb.entry.news.NewsGeneric;
import com.meiah.dao.TaskDao;
import com.meiah.po.News;
import com.meiah.po.Task;
import com.meiah.po.TaskLink;
import com.meiah.po.WebPage;
import com.meiah.trs.NewsTrsDo;
import com.meiah.util.Config;
import com.meiah.util.ContentExtractorUtil;
import com.meiah.util.JavaUtil;
import com.meiah.util.MD5Utils;
import com.meiah.util.SysConstants;
import com.meiah.util.SysObject;
import com.meiah.webCrawlers.SiteCrawler;
import com.meiah.webCrawlers.SitePlugin.PageResolver_General;

public class PageResolver_Gxi extends PageResolver_General {
	
	public PageResolver_Gxi(Task task) {
		super(task);
	}

	protected NewsGeneric resloveNewsPage(WebPage page) {
		News news = new News();
		String webContent = page.getWebContent();
		TaskLink link = page.getLink();
		Document doc = null;
		try {
			doc = JavaUtil.getDocument(webContent);
		} catch (Exception e) {
			logger.error("解析新闻页面出现异常！" + link.getUrl());
			return null;
		}
		webContent = clearHtml(webContent);
		NewsGeneric ne = new NewsGeneric();
		ne.setTask_id(task.getTaskid());
		ne.setPage_url(link.getUrl());
		ne.set_id(MD5Utils.getMD5(ne.getPage_url().getBytes()));
		String fileName = "";
		if (Config.getIsSaveSnapShot() == 1) {
			fileName = savePageSnapShot(page);
		}
		if(StringUtils.isNotBlank(fileName))
			ne.setPage_snapshot(fileName);
		String webdomain = ne.getPage_url();
		String domain = webdomain.substring(webdomain.indexOf("://") + 3);
		domain = domain.substring(0, domain.indexOf("/"));
		ne.setWebsite_domain(SiteCrawler.topDomain);
		String prefixUrl = link.getRefererUrl().substring(0,link.getRefererUrl().indexOf("://") + 3);
		ne.setWebsite_url(prefixUrl + domain + "/");
		ne.setPage_size(String.valueOf(webContent.getBytes().length));
		Map<String, Task> ts = TaskDao.getInstance().getAllTaskMap();
		String siteName = ((Task) ts.get(ne.getTask_id())).getTname().replaceAll("\r\n", "");
		ne.setWebsite_name(siteName);
		ne.setTask_name(siteName);
		String ipUrl = ((Task) ts.get(ne.getTask_id())).getUrl();
		String ipName = "";
		String ip = JavaUtil.matchWeak(ipUrl, "http://([^/]*)")[1];
		if (NewsTrsDo.ips == null)
			NewsTrsDo.ips = new HashMap<String, String>();
		if (NewsTrsDo.ips.containsKey(ip)) {
			ip = NewsTrsDo.ips.get(ip);
		} else {
			InetAddress a = null;
			try {
				a = InetAddress.getByName(ip);
			} catch (UnknownHostException e) {
				e.printStackTrace();
			}
			ip = a.getHostAddress();
			NewsTrsDo.ips.put(ip, a.getHostAddress());
		}
	    //ip地址所在地
		if(SysObject.ipTable.containsKey(ip)){
			ipName = SysObject.ipTable.get(ip);
		}else{
			ipName = NewsTrsDo.getAddressByIP(ip);
			if(StringUtils.isNotEmpty(ipName)){
				SysObject.ipTable.put(ip, ipName);
			}
		}
		ne.setWebsite_ip(ip);
		ne.setWebsite_ip_area(ipName);
		ne.setNews_class(task.getNewsType());
		
		/** end default notChange * */
//		String newsTitle = getNewsTitle(task.getSiteConfig(), page, doc);
//		ne.setNews_title(newsTitle);
		ne.setNews_title(link.getTitle());
		if(StringUtils.isNotBlank(ne.getNews_title())) {
			ne.getNews_title().replaceAll("\r\n", "")
					.replaceAll("\n", "").replaceAll("\t", "")
					.replaceAll(">>", "").replaceAll("　　", "")
					.replaceAll(" ", "").replaceAll("■  ", "")
					.replaceAll("&gt;", "").replaceAll("&lt;", "")
					.replaceAll(" ", "").replaceAll("\t","")
					.replaceAll("&ldquo;", "").replaceAll("&rdquo;", "");
		}
		if(StringUtils.isEmpty(ne.getNews_title())) {
			try {
				news = ContentExtractorUtil.getNewsByHtml(webContent, news);
			} catch (Exception e) {
				e.printStackTrace();
			}
			ne.setNews_title(news.getTitle().replaceAll("\r\n", "").replaceAll("\n", "").replaceAll("\t", "")
					.replaceAll(" ", "").replaceAll("&gt;", "").replaceAll("&lt;", "").replaceAll("&ldquo;", "").replaceAll("&rdquo;", ""));
		}
		if(StringUtils.isEmpty(ne.getNews_title())) {
			ne.setNews_title(getNewsTitle(this.task.getBeginTitle(),
					this.task.getEndTitle(), page));
		}
		if(StringUtils.isNotBlank(ne.getNews_title())) {
			String regex = "[\\u4E00-\\u9FA5]";
			String[] chinese = JavaUtil.match(ne.getNews_title(), regex);
			if (null == chinese || chinese.length < 0) {
				ne = null;
				return ne;
			}
		}
		long tStart = System.currentTimeMillis(), t2 = 0, t3 = 0, t4 = 0;
		String content = getNewsContent(task.getSiteConfig(), page, doc);
		
		if (logger.isDebugEnabled()) {
			t3 = System.currentTimeMillis() - tStart - t2;
			logger.debug("正文提取耗费时间：" + t3 + "ms");
		}
		ne.setNews_content(content.replaceAll("　", "").replaceAll("\r\n", "").replaceAll("\n", "").trim());
		
		Date dateDublished = getNewsPublishTime(task, page, doc);
		if(null != dateDublished)
			ne.setPage_publish_time(dateDublished);
		ne.setPage_save_time(new Date());
		
		InetAddress a = null;
		try {
			ne.setServer_ip(a.getLocalHost().getHostAddress());
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		if (logger.isDebugEnabled()) {
			t4 = System.currentTimeMillis() - tStart - t2 - t3;
			logger.debug("时间提取耗费时间：" + t4 + "ms");
		}

		link.setLinkType(SysConstants.PAGETYPE_CONTENT);
		String newsSource = getNewsSource(task.getSiteConfig(), page, doc);
		newsSource = null ;
		if(StringUtils.isNotBlank(newsSource))
			ne.setPage_source(newsSource);
		String newsAuthor = getNewsAuthor(task.getSiteConfig(), page, doc);
		if(StringUtils.isNotBlank(newsAuthor))
			ne.setNews_author(StringUtils.replaceEach(newsAuthor, new String[] { "等", "撰写","作者：","作者:"," ","?" }, new String[] { "", "" ,"" , "","",""}));
		List<String> newsImage = getNewsImages(task.getSiteConfig(), page, doc,ne.getPage_url());
		if(!newsImage.isEmpty())
			ne.setPage_image_url(newsImage);
		//获取附件地址
		List<String> accessList = getNewsAccess(task.getSiteConfig(), page, doc, ne.getPage_url());
		if(!accessList.isEmpty()) 
			ne.setPage_acces(accessList);
		String columnName = getNewsColumnName(task.getSiteConfig(), page, doc);
		if(StringUtils.isNotBlank(columnName))
			ne.setNews_column(columnName);
		String documentNo = getNewsDocumentNo(task.getSiteConfig(), page, doc);
		if(StringUtils.isNotBlank(documentNo))
			ne.setNews_notice_code(documentNo);
		rtask.setContentPages(rtask.getContentPages() + 1);
		
		if(StringUtils.isEmpty(ne.getNews_title())) {
			ne = null;
			return ne;
		}
		if(StringUtils.isNotBlank(ne.getNews_title())) {
			boolean titleFilter = titleKeyFilter.isContentKeyWords(ne.getNews_title());
			if(titleFilter  || ne.getNews_title().length() < 5) {
				ne = null;
				return ne;
			}else if(ne.getNews_content().isEmpty()&&newsImage.isEmpty()&&accessList.isEmpty()){
						ne = null;
						return ne;
					}
			}
		return ne;
	}
	/**
	 * 提取新闻标题
	 * 
	 * @param beginTitle
	 * @param endTitle
	 * @param webContent
	 * @return
	 */
	protected String getNewsTitle(String beginTitle, String endTitle, WebPage page) {
		String title = "";
		String webContent = page.getWebContent();
		TaskLink link = page.getLink();
		try {
			String textBeginCode = beginTitle;
			String textEndCode = endTitle;
			if (!textBeginCode.equals("") && !textEndCode.equals("")) {
				String text = webContent.toLowerCase();
				int iPos0 = text.indexOf(textBeginCode.toLowerCase());
				if (iPos0 != -1) {
					int len0 = textBeginCode.length();
					int iPos1 = text.indexOf(textEndCode.toLowerCase(), iPos0
							+ len0);
					if (iPos1 != -1) {
						title = text.substring(iPos0 + len0, iPos1).replaceAll(
								"&nbsp;", " ").replaceAll("(?s)(?i)<.*?>", "");
					}
				}
			}
			if (title.equals("")) {
				String linkText = link.getTitle();
				if (linkText != null && linkText.trim().length() != 0) {
					title = linkText;
				} else {

					String[] temp = JavaUtil.match(webContent,
							"(?s)(?i)<title>(.*?)</title>");
					if (temp != null && temp.length > 0)
						title = temp[1];
				}
			}
		} catch (Throwable e) {
			logger.error("抓取新闻: " + link.getUrl() + " 标题出现异常", e);
		}
		if(StringUtils.isNotBlank(title))
			title = title.replaceAll("\r\n", "").replaceAll("\n", "").replaceAll("\t", "").replaceAll(" ", "").replaceAll("&gt;", "").replaceAll("&lt;", "").replaceAll("&ldquo;", "").replaceAll("&rdquo;", "");
		return title;
	}
}
