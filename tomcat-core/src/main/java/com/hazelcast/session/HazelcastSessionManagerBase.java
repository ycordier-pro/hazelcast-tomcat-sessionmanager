package com.hazelcast.session;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.session.ManagerBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

/**
 * The base class of all session managers that implements the Hazelcast client logic
 * 
 * @author ycordier
 *
 */
public abstract class HazelcastSessionManagerBase extends ManagerBase implements SessionManager {

	private static final int DEFAULT_MAX_CONNECT_ATTEMPTS = 60;
	
	private static final int DEFAULT_WAIT_BETWEEN_CONNECT_ATTEMPTS = 5000;
	
	private HazelcastInstance instance;
	
    private boolean clientOnly;

    private String mapName;
    
    private String hazelcastInstanceName;

    private IMap<String, HazelcastSession> sessionMap;
    
    private boolean sticky = true;

    private boolean deferredWrite = true;

    private int maxConnectAttempts = DEFAULT_MAX_CONNECT_ATTEMPTS;
	
	private int waitBetweenConnectAttempts = DEFAULT_WAIT_BETWEEN_CONNECT_ATTEMPTS;

    private final Log log = LogFactory.getLog(HazelcastSessionManagerBase.class);

	public HazelcastSessionManagerBase() {
		super();
	}

	public void startHZClient(final ClassLoader classLoader, final String mapNameToUse) throws LifecycleException {
		int attempts=0;
		boolean hzClientStarted = false;
		while (!hzClientStarted) {
	        try {
				instance = HazelcastInstanceFactory.
				        getHazelcastInstance(classLoader, isClientOnly(), getHazelcastInstanceName());
				if (instance!=null)
					hzClientStarted = true;
			} catch (LifecycleException e) {
				if (attempts > maxConnectAttempts) {
					log.fatal("All attempts failed, giving up Hazelcast client connection!");
					throw e;
				} else {
					attempts++;
					log.error("Error while trying to connect Hazelcast client, will attempt new try in "+waitBetweenConnectAttempts+"ms (try "+attempts+"/"+maxConnectAttempts+")...", e);
					try {
						Thread.sleep(waitBetweenConnectAttempts);
					} catch (InterruptedException ie) {
						log.warn("Sleep between connection attempts was interrupted!", ie);
					}
				}
			}
		}
		
	    instance.getLifecycleService().addLifecycleListener(new com.hazelcast.core.LifecycleListener() {
			@Override
			public void stateChanged(com.hazelcast.core.LifecycleEvent event) {
				//try to reconnect in case of shutdown
	            if (com.hazelcast.core.LifecycleEvent.LifecycleState.SHUTDOWN.equals(event.getState())) {
	            	try {
	            		log.info("Detected Hazelcast client shutdown, restarting client...");
	            		startHZClient(classLoader, mapNameToUse);
					} catch (LifecycleException e) {
				        log.fatal("Error while restarting Hazelcast client!", e);
					}
	            }
			}
	    });
	    log.info("Hazelcastclient connected.");
	    
        sessionMap = instance.getMap(mapNameToUse);
	    if (!isSticky()) {
	        sessionMap.addEntryListener(new LocalSessionsInvalidateListener(sessions), false);
	    }

	}

	public HazelcastInstance getHZInstance() {
		return instance;
	}
	
	public void stopHZClient() {
		instance.shutdown();
	}

	public int getMaxConnectAttempts() {
		return maxConnectAttempts;
	}

	public void setMaxConnectAttempts(int maxConnectAttempts) {
		this.maxConnectAttempts = maxConnectAttempts;
	}

	public int getWaitBetweenConnectAttempts() {
		return waitBetweenConnectAttempts;
	}

	public void setWaitBetweenConnectAttempts(int waitBetweenConnectAttempts) {
		this.waitBetweenConnectAttempts = waitBetweenConnectAttempts;
	}
	
	public boolean isClientOnly() {
        return clientOnly;
    }

    public void setClientOnly(boolean clientOnly) {
        this.clientOnly = clientOnly;
    }

    public String getHazelcastInstanceName() {
        return hazelcastInstanceName;
    }

    public void setHazelcastInstanceName(String hazelcastInstanceName) {
        this.hazelcastInstanceName = hazelcastInstanceName;
    }

    public String getMapName() {
        return mapName;
    }

    public void setMapName(String mapName) {
        this.mapName = mapName;
    }

    @Override
    public IMap<String, HazelcastSession> getDistributedMap() {
        return sessionMap;
    }

    @Override
    public boolean isDeferredEnabled() {
        return deferredWrite;
    }

    public void setDeferredWrite(boolean deferredWrite) {
        this.deferredWrite = deferredWrite;
    }

    @Override
    public boolean isSticky() {
        return sticky;
    }

    public void setSticky(boolean sticky) {
        if (!sticky && getJvmRoute() != null) {
            log.warn("setting JvmRoute with non-sticky sessions is not recommended and might cause unstable behaivour");
        }
        this.sticky = sticky;
    }

}