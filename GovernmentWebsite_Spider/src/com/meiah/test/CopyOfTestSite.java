package com.meiah.test;

import java.io.InputStream;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import com.meiah.dao.RunningTaskDao;
import com.meiah.po.RunningTask;
import com.meiah.webCrawlers.SiteCrawler;

public class CopyOfTestSite {
	private static Logger logger = Logger.getLogger(CopyOfTestSite.class);

	/**
	 * @param
	 */
	public static void main(String[] args) {
		Properties props = new Properties();
		try {

			InputStream istream = SiteCrawler.class.getResourceAsStream("/log4j.properties");
			props.load(istream);
			istream.close();

			props.setProperty("log4j.rootLogger", "info,NEWS,logfile,logfile1");

			// 重新配置后，日志会打到新的文件去。
			PropertyConfigurator.configure(props);// 装入log4j配置信息

		} catch (Exception e) {
			logger.error("装入属性文件异常 Exception ", e);
		}
		String taskid = "1100064";
		try {	
			SiteCrawler c = new SiteCrawler(taskid);
			RunningTaskDao.getInstance().deleteRunningTask((RunningTask) SiteCrawler.task);
			RunningTaskDao.getInstance().addRunningTask((RunningTask) SiteCrawler.task);
			c.start();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}
}
