﻿package com.meiah.webCrawlers;

import java.sql.SQLException;
import java.util.Date;

import org.apache.log4j.Logger;

import com.meiah.dao.CenterBaseDao;
import com.meiah.dao.RunningTaskDao;
import com.meiah.dao.TaskDao;
import com.meiah.po.RunningTask;
import com.meiah.po.Task;
import com.meiah.util.Config;
import com.meiah.util.Converter;
import com.meiah.util.SysObject;

public class TaskMonitor extends Thread {
	private RunningTask rtask;
	private Task task;
	private static final long MAX_RUMTIME = 2 * 3600 * 1000;// 任务的最长运行时间两小时
	public static int hangOnTime = 1000;// 任务的在队列没有链接的最长等待时间

	// public int getHangOnTime() {
	// return hangOnTime;
	// }
	//
	// public void setHangOnTime(int hangOnTime) {
	// this.hangOnTime = hangOnTime;
	// }

	private RunningTaskDao dao = RunningTaskDao.getInstance();

	private Date startTime = null;

	private long sleeptime = 5000;// 5秒检查一次

	private Logger logger = Logger.getLogger(TaskMonitor.class);

	public TaskMonitor(Task task) {
		this.task = task;
		this.rtask = (RunningTask) task;
		startTime = new Date();

	}

	@Override
	public void run() {
		logger.info("运行任务守护线程开始启动");
		mainPro();
	}

	/**
	 * 启动时，插入运行任务，不断检查运行任务状态，做出相应动作，暂停或停止。运行中不断更新任务数据。
	 */
	private void mainPro() {
		// 启动，插入任务
		int state;

		// 运行中，检查状态。
		while (true) {
			this.rtask = (RunningTask) SiteCrawler.task;

			// 1 检查状态
			state = dao.getNowState(rtask);
			// 只有在运行和暂停状态才需要接收由外部来的状态改变
			if (rtask.getRunstate() == Task.RUNNING || rtask.getRunstate() == Task.PAUSE)
				rtask.setRunstate(state);

			if (System.currentTimeMillis() - startTime.getTime() > MAX_RUMTIME) {
				logger.info("运行时间超过2小时,停止下载，准备停止任务");
				rtask.setRunstate(Task.STOP);
			}
			// 若没有下载网页的情况超过了100秒
			int timeout = 1000;

			if (rtask.getNondtimes() >= hangOnTime && SysObject.getQueueSize() == 0 && SysObject.crawlThreads.get() == 0) {
				rtask.setRunstate(Task.STOP);
				logger.info("任务：" + SiteCrawler.task.getUrl() + " 共下载了 " + rtask.getDownloadPages() + " 个页面 ");
				logger.info("10秒内没有下载到任何网页,当前没有下载中的网页，待下载的网页为0,停止任务" + this.rtask.getTaskid());
			}
			if (rtask.getNondtimes() > timeout) {
				rtask.setRunstate(Task.STOP);
				logger.info("任务：" + SiteCrawler.task.getUrl() + " 共下载了 " + rtask.getDownloadPages() + " 个页面 ");
				logger.info("过了" + timeout + "秒，仍然没有下载到任何网页，待下载url数量：" + SysObject.getQueueSize() + ",停止任务" + this.rtask.getTaskid());
			}
			int maxPage = SiteCrawler.task.getPages();
			if (rtask.getDownloadPages() > maxPage) {
				rtask.setRunstate(Task.STOP);
				logger.info("下载页数超过任务最大页数" + maxPage + "页，停止任务！");
			}
			// 2 更新数据
			rtask.countSpeed();
			// dao.updateRunningTask(rtask);
			if (rtask.getRunstate() == Task.STOP) {
				logger.info("当前任务：" + this.rtask.getTaskid() + " 停止，删除当前任务");
				logger.info("任务共下载了 " + rtask.getDownloadPages() + " 个页面,其中 " + rtask.getContentPages() + " 个新闻页面");
				if (PageCrawler.pageCount.get() != 0)
					logger.info("平均每个网页(" + PageCrawler.pageCount + ")下载时间：" + (PageCrawler.totalTime.get() / PageCrawler.pageCount.get()) + " ms");
				break;
			}

			try {
				Thread.sleep(sleeptime);
			} catch (InterruptedException e) {
			}
		}
		if (SysObject.getPageSize() > 0) {
			if (task.pluginMode == true) {
				PageResolver crawler;
				try {
					crawler = PluginFactory.getInstance().getPageResolver(task);
					crawler.start();
				} catch (Exception e) {
					logger.error("", e);
				}

			} else {
				new PageResolver(task).start();// 任务结束时有可能还有缓存的页面未解析，此次保存
			}

		}
		while (SysObject.resloveThreads.get() > 0) {
			// 等待所有的线程结束
			logger.info("结束任务！当前解析线程数：" + SysObject.resloveThreads.get());
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}
			if (System.currentTimeMillis() - startTime.getTime() > MAX_RUMTIME) {
				logger.info("运行时间超过2小时,停止下载，准备停止任务");
				break;
			}
		}
		TaskDao.getInstance().updateNextStarttime(SiteCrawler.task);// 更新下次开始时间
		saveTaskLog(rtask, "");

		RunningTaskDao.getInstance().deleteRunningTask(SiteCrawler.task);// 删除当前运行任务
		// TaskStartAuto.nowProc.decrementAndGet();
		// ListUrlFilter.readerToClose = true;
		// try {
		// Thread.sleep(500);
		// } catch (InterruptedException e) {
		// }
		// ListUrlFilter.endSocket();
		ClientCenter.endSocket();
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
		}

		clearTask();
		logger.info("当前抓取线程数：" + SysObject.crawlThreads.get() + ",当前解析线程数："
				+ SysObject.resloveThreads.get() + ",任务："
				+ SiteCrawler.task.getUrl() + " 完成 ");
		System.exit(0);// 退出JVM
	}

	private void clearTask() {
		((RunningTask) SiteCrawler.task).resetCounts();
		SysObject.cleanTask();// 清除URL排重保存信息
	}

	/**
	 * 更新任务日志
	 * 
	 * @param rtask
	 */
	private synchronized void saveTaskLog(RunningTask rtask, String comment) {
		try {
			String runip = Config.getLocalIp();
			// InetAddress.getLocalHost().getHostAddress().toString();
			String sqlStr = "insert into n_task_log (taskid,starttime,endtime,downpages,savepages,maxspeed,avgspeed,minspeed,runip,comment,threadnums,listpages,contentpages,unknownpages) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
			Object[] parasValue = new Object[] { rtask.getTaskid(),
					Converter.getSqlDateFromUtil(startTime),
					Converter.getSqlDateFromUtil(new Date()),
					rtask.getDownloadPages(), rtask.getContentPages(),
					rtask.getMaxspeed(), rtask.getAvgspeed(),
					rtask.getMinspeed(), runip, comment, rtask.getMaxThreads(),
					rtask.getListPages(), rtask.getContentPages(),
					rtask.getUnknownPages() };
			new CenterBaseDao().save(sqlStr, parasValue);
			logger.info("更新任务: " + rtask.getTaskid() + " 日志成功");

		} catch (SQLException e) {
			logger.error("更新任务日志异常", e);
		}

		// catch (UnknownHostException e) {
		// logger.error("更新任务日志异常", e);
		//
		// }

	}
}
