package limax.xmlconfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import limax.net.Engine;
import limax.util.ElementHelper;
import limax.util.Limit;
import limax.util.MBeans;
import limax.util.Resource;
import limax.util.Trace;
import limax.util.XMLUtils;

public class Service {
	private Service() {
	}

	public interface StopperMXBean {
		void setStopTime(long delayseconds);

		long getStopTime();
	}

	public static final class Stopper implements StopperMXBean {
		static final ReentrantLock shutdownAlarmLock = new ReentrantLock();
		static final Condition shutdownAlarm = shutdownAlarmLock.newCondition();
		static long stopTime = -1L;

		public Stopper() {
			JMXRegister(this, "limax.xmlconfig:type=Service,name=Stopper");
		}

		public void doWait() {
			while (true) {
				try {
					shutdownAlarmLock.lockInterruptibly();
				} catch (final InterruptedException ex) {
					break;
				}
				try {
					if (stopTime < 0)
						shutdownAlarm.await();
					else {
						final long now = System.currentTimeMillis();
						if (now >= stopTime)
							break;
						else
							shutdownAlarm.awaitUntil(new Date(stopTime));
					}
				} catch (final InterruptedException ex) {
					break;
				} finally {
					shutdownAlarmLock.unlock();
				}
			}
		}

		@Override
		public long getStopTime() {
			final long time;
			shutdownAlarmLock.lock();
			try {
				time = stopTime;
			} finally {
				shutdownAlarmLock.unlock();
			}
			return time <= 0 ? time : time - System.currentTimeMillis();
		}

		@Override
		public void setStopTime(long milliseconds) {
			if (milliseconds < 0)
				return;
			shutdownAlarmLock.lock();
			try {
				stopTime = System.currentTimeMillis() + milliseconds;
				shutdownAlarm.signalAll();
			} finally {
				shutdownAlarmLock.unlock();
			}
		}
	}

	private static class Loader {
		private static class TaskList {
			private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<Runnable>();

			public void add(Runnable task) {
				tasks.offer(task);
			}

			public void run() {
				for (Runnable r; (r = tasks.poll()) != null; r.run())
					;
			}
		}

		private final ConfigParserCreator propertiesParserCreator;
		private final Map<String, ConfigParserCreator> priorcreatormap = new HashMap<String, ConfigParserCreator>();
		private final Map<String, ConfigParserCreator> normalcreatormap = new HashMap<String, ConfigParserCreator>();
		private final TaskList taskbeforeenginestart = new TaskList();
		private final TaskList taskafterenginestart = new TaskList();
		private final TaskList taskbeforeenginestop = new TaskList();
		private final TaskList taskafterenginestop = new TaskList();
		private final XmlConfigs.ThreadPoolSizeData threadpoolsize = new XmlConfigs.ThreadPoolSizeData();

		public Loader() {

			propertiesParserCreator = self0 -> self -> {
				String filename = new ElementHelper(self).getString("file");
				Properties properties = new Properties();
				properties.load(new InputStreamReader(new FileInputStream(filename), StandardCharsets.UTF_8));
				ElementHelper.setProperties(properties);
			};

			priorcreatormap.put("Properties", _self -> self -> {
			});

			priorcreatormap.put("Trace", self0 -> self -> {
				Trace.Config config = new Trace.Config();
				ElementHelper eh = new ElementHelper(self);
				config.setOutDir(eh.getString("outDir", "./trace"));
				config.setConsole(eh.getBoolean("console", true));
				config.setRotateHourOfDay(eh.getInt("rotateHourOfDay", 6));
				config.setRotateMinute(eh.getInt("rotateMinute", 0));
				config.setRotatePeriod(eh.getLong("rotatePeriod", 86400000l));
				config.setLevel(eh.getString("level", "warn").toUpperCase());
				Trace.openNew(config);
				JMXRegister(config, "limax.xmlconfig:type=Service,name=Trace");
			});

			priorcreatormap.put("Limit", self0 -> self -> {
				final ElementHelper eh = new ElementHelper(self);
				Limit.put(eh.getString("name"), eh.getLong("maxSize", Long.MAX_VALUE));
			});

			normalcreatormap.put("Manager", new XmlConfigs.ManagerConfigParserCreator());

			normalcreatormap.put("GlobalId", new XmlConfigs.GlobalIdConfigParserCreator());

			normalcreatormap.put("ThreadPoolSize", self -> threadpoolsize);

			normalcreatormap.put("JmxServer", self -> new XmlConfigs.JMXServer());

			normalcreatormap.put("NodeService", self -> new XmlConfigs.NodeService());

			normalcreatormap.put("Provider", new limax.provider.XmlConfig.ProviderDataCreator());

			normalcreatormap.put("Zdb", self -> new limax.provider.XmlConfig.StartZdb());

			normalcreatormap.put("Switcher", self -> new limax.switcher.Config.SwitcherConfig());
		}

		private void loadPropertiesElements(Element self) throws Exception {
			NodeList childnodes = self.getElementsByTagName("Properties");
			for (int i = 0; i < childnodes.getLength(); ++i) {
				Node node = childnodes.item(i);
				if (Node.ELEMENT_NODE != node.getNodeType())
					continue;
				Element e = (Element) node;
				propertiesParserCreator.createConfigParse(e).parse(e);
			}
		}

		private ConfigParser getConfigParserInstance(Element e) throws Exception {
			ElementHelper eh = new ElementHelper(e);
			String creatorclass = eh.getString("parserCreatorClass");
			if (null != creatorclass && creatorclass.length() > 0) {
				ConfigParserCreator creator = (ConfigParserCreator) Class.forName(creatorclass).getDeclaredConstructor()
						.newInstance();
				return creator.createConfigParse(e);
			}
			String parserclass = eh.getString("parserClass");
			if (null != parserclass && parserclass.length() > 0)
				return (ConfigParser) Class.forName(parserclass).getDeclaredConstructor().newInstance();

			String n = e.getNodeName();
			ConfigParserCreator creator = normalcreatormap.get(n);
			if (null == creator)
				throw new RuntimeException("unknown element name = " + n + "!");
			return creator.createConfigParse(e);
		}

		private void priorLoadElements(Element self) throws Exception {
			XMLUtils.getChildElements(self).forEach(e -> {
				final ConfigParserCreator creator = priorcreatormap.get(e.getNodeName());
				if (null != creator) {
					try {
						creator.createConfigParse(e).parse(e);
					} catch (Exception ex) {
						throw new RuntimeException(ex);
					}
				}
			});
		}

		private void normalLoadElements(Element self) throws Exception {
			XMLUtils.getChildElements(self).forEach(e -> {
				if (!priorcreatormap.containsKey(e.getNodeName()))
					try {
						getConfigParserInstance(e).parse(e);
					} catch (Exception ex) {
						throw new RuntimeException(e.getNodeName(), ex);
					}
			});
		}

		private void loadByElement(Element self) throws Exception {
			if (false == self.getNodeName().equals("ServiceConf"))
				throw new IllegalArgumentException(self.getNodeName() + " is not a ServiceConf.");

			loadPropertiesElements(self);
			priorLoadElements(self);
			normalLoadElements(self);
		}

		public void load(String filename) throws Exception {
			loadByElement(XMLUtils.getRootElement(filename));
			if (Trace.isInfoEnabled())
				Trace.info("ServiceConf load " + filename);
		}

		public void load(InputStream is) throws Exception {
			loadByElement(XMLUtils.getRootElement(is));
			if (Trace.isInfoEnabled())
				Trace.info("ServiceConf load " + is);
		}

		public void load(Element root) throws Exception {
			loadByElement(root);
			if (Trace.isInfoEnabled())
				Trace.info("ServiceConf load by Element");
		}

		public void addRunBeforeEngineStartTask(Runnable task) {
			taskbeforeenginestart.add(task);
		}

		public void runTaskBeforeEngineStart() {
			if (Trace.isInfoEnabled())
				Trace.info("ServiceConf runTaskBeforeEngineStart");
			taskbeforeenginestart.run();
		}

		public void addRunAfterEngineStartTask(Runnable task) {
			taskafterenginestart.add(task);
		}

		public void runTaskAfterEngineStart() {
			if (Trace.isInfoEnabled())
				Trace.info("ServiceConf runTaskAfterEngineStart");
			taskafterenginestart.run();
		}

		public void addRunBeforeEngineStopTask(Runnable task) {
			taskbeforeenginestop.add(task);
		}

		public void runTaskBeforeEngineStop() {
			if (Trace.isInfoEnabled())
				Trace.info("ServiceConf runTaskBeforeEngineStop");
			taskbeforeenginestop.run();
		}

		public void addRunAfterEngineStopTask(Runnable task) {
			taskafterenginestop.add(task);
		}

		public void runTaskAfterEngineStop() {
			if (Trace.isInfoEnabled())
				Trace.info("ServiceConf runTaskAfterEngineStop");
			taskafterenginestop.run();
		}

		private void startNetEngineByThreadPoolSizeInfo() throws Exception {
			if (Trace.isInfoEnabled())
				Trace.info("ServiceConf startNetEngine");
			Engine.open(threadpoolsize.getNioCpus(), threadpoolsize.getNetProcessors(),
					threadpoolsize.getProtocolSchedulers(), threadpoolsize.getApplicationExecutors());
		}

		public void startNetEngine() throws Exception {
			runTaskBeforeEngineStart();
			startNetEngineByThreadPoolSizeInfo();
			runTaskAfterEngineStart();
		}

		public void stopNetEngine() {
			runTaskBeforeEngineStop();
			if (Trace.isInfoEnabled())
				Trace.info("ServiceConf stopNetEngine");
			Engine.close();
			runTaskAfterEngineStop();
		}
	}

	static Resource mbeans = Resource.create(MBeans.root(), () -> {
	});

	static public void JMXRegister(Object object, String name) {
		MBeans.register(mbeans, object, name);
	}

	static private final Loader instance = new Loader();

	public static void load(String filename) throws Exception {
		instance.load(filename);
	}

	public static void load(InputStream is) throws Exception {
		instance.load(is);
	}

	public static void load(Element root) throws Exception {
		instance.load(root);
	}

	static public void addRunBeforeEngineStartTask(Runnable task) {
		instance.addRunBeforeEngineStartTask(task);
	}

	static public void addRunAfterEngineStartTask(Runnable task) {
		instance.addRunAfterEngineStartTask(task);
	}

	static public void addRunBeforeEngineStopTask(Runnable task) {
		instance.addRunBeforeEngineStopTask(task);
	}

	static public void addRunAfterEngineStopTask(Runnable task) {
		instance.addRunAfterEngineStopTask(task);
	}

	public static void startNetEngine() throws Exception {
		instance.startNetEngine();
	}

	public static void stopNetEngine() {
		instance.stopNetEngine();
	}

	private static Stopper stopper;

	private static volatile File parentFile;

	public static File getConfigParentFile() {
		return parentFile;
	}

	public static void run(String servicexmlfilename) throws Exception {
		try {
			stopper = new Stopper();
			parentFile = new File(servicexmlfilename).getParentFile();
			if (parentFile == null)
				parentFile = new File(".");
			load(servicexmlfilename);
			startNetEngine();
			stopper.doWait();
			stopNetEngine();
		} catch (Throwable e) {
			e.printStackTrace();
			System.exit(-1);
		} finally {
			mbeans.close();
		}
	}

	public static void stop(long milliseconds) {
		stopper.setStopTime(milliseconds);
	}

}
