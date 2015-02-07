package net.floodlightcontroller.getThroughPut;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.statistics.OFPortStatisticsReply;
import org.openflow.protocol.statistics.OFPortStatisticsRequest;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.LDUpdate;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.threadpool.IThreadPoolService; //import this to use its thread pool
import net.floodlightcontroller.topology.ITopologyListener;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.topology.NodePortTuple;
import net.floodlightcontroller.topology.TopologyInstance;

public class CongestionDetect implements IFloodlightModule,ITopologyListener,ICongestionDetectService{
	
	protected IThreadPoolService threadPool;
	protected IFloodlightProviderService floodlightProvider;
	protected ITopologyService topo;
	

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
	    l.add(ICongestionDetectService.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
	    m.put(ICongestionDetectService.class, this);
	    return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IThreadPoolService.class);
		l.add(ITopologyService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		threadPool = context.getServiceImpl(IThreadPoolService.class);
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		topo = context.getServiceImpl(ITopologyService.class);

	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		//Since there may not be any switch when the controller is started up, the congestion detection mechanism should be
		//activated whenever there is a topology changed. 
		System.out.println("Hi, I am " + this.getClass().getName() + ", and I am going to run");
		
	}

	@Override
	public void topologyChanged() {
		
		ScheduledExecutorService ses = threadPool.getScheduledExecutor();
		Map<NodePortTuple, Set<Link>> switchPortLinks = topo.getSwitchPortLinks();
		Set<NodePortTuple> portSet = switchPortLinks.keySet();
		System.out.println("The number of ports under consideration is: " + portSet.size());
		Iterator<NodePortTuple> it = portSet.iterator();
		while(it.hasNext())
		{
			System.out.println("I have already run to here, so it means I have entered the loop");
			NodePortTuple switchPort = it.next();
			IOFSwitch sw = floodlightProvider.getSwitch(switchPort.getNodeId());
			List<OFPortStatisticsReply> statsReply = new ArrayList<OFPortStatisticsReply>();
			System.out.println("Starting many threads to monitor ports");
				//Here, multiple threads should be initiated to monitor all ports in all links.
				ses.scheduleAtFixedRate(new MyThread(switchPort,sw,statsReply),
			    0,
			    1,
			    TimeUnit.SECONDS);
		}

		
	}

	public void topologyChanged(List<LDUpdate> linkUpdates){};

	

	
}
