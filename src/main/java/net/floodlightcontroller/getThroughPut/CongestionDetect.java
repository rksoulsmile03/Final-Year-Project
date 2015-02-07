package net.floodlightcontroller.getThroughPut;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

public class CongestionDetect implements IFloodlightModule,ITopologyListener,ICongestionDetectService{
	
	//These two are used once in a life time
	private IThreadPoolService threadPool;
	private IFloodlightProviderService floodlightProvider;
	
	//These two are renew every time an update in topology occur
	private ITopologyService topo; 
	private Map<NodePortTuple, Set<Link>> switchPortLinks;//This can be used to return the link from the port that is detected congested.
	private Set<NodePortTuple> portSet;
	
	//This should be accessible by both individual threads and this class 
	protected Map<NodePortTuple,IsCongested> portCondition;
	
	
	
	public class IsCongested{
		
		boolean isCongested;
		
		public IsCongested(boolean isCongested)
		{
			this.isCongested = isCongested;
		}
	}
	

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
		portCondition = new ConcurrentHashMap<NodePortTuple,IsCongested>();

	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		//Since there may not be any switch when the controller is started up, the congestion detection mechanism should be
		//activated whenever there is a topology changed. 
		System.out.println("Hi, I am " + this.getClass().getName() + ", and I am going to run");
		
	}

	protected class MyThread implements Runnable {
		
		private NodePortTuple switchPort;
		private IOFSwitch sw;
		int threshold;
		List<OFPortStatisticsReply> statsReply;

		//Since every thread is responsible to the detection of congestion of every switch port, so it is the field of this class.
		public MyThread(NodePortTuple switchPort,IOFSwitch sw)
		{
			this.switchPort = switchPort;
			this.sw = sw;
			threshold = 1000;//The threshold for congestion
			statsReply = new ArrayList<OFPortStatisticsReply>();//This is created once in a life time of the thread
		}
		
		@Override
		public void run() {
	    	long prebyte,nowbyte,flow;
	    	List<OFPortStatisticsReply> l = getPortInfo(switchPort.getNodeId() ,switchPort.getPortId());
	    	//Every second, a new list is initiated for the execution of this task for detecting the flow of certain port.
	    	
	    	if(!l.isEmpty())
	    	{
	    		if(l.size()>2)
	    		{
	    			l.remove(0);
	    		}
	    		
	    		if(l.size() == 2)
	    		{
	    			prebyte = l.get(0).getReceiveBytes();
	    			nowbyte = l.get(1).getReceiveBytes();
	    			flow = nowbyte - prebyte;
	    			System.out.println("I have " + sw.getPorts().size() + " ports." + " And the flow for "+"switch "+ switchPort.getNodeId() +": port: "+ switchPort.getPortId() + " is " + flow);
	    			//If there is congestion, modify the cost of the link, and ask the topology manager to
	    			//recompute the best route.
	    			if(flow > threshold)
	    			{
	    				System.out.println("There is congestion issue");
	    				portCondition.put(switchPort,new IsCongested(false));
	    			}
	    			else
	    			{
	    				System.out.println("There is no congestion issue");
	    				portCondition.put(switchPort,new IsCongested(false));
	    			}
	    		}
	    		
	    	}
	    }

		
		public List<OFPortStatisticsReply> getPortInfo(long switchId, short portNumber)
		{
	        List<OFStatistics> values = null;
	        Future<List<OFStatistics>> future;
	        

	        // Statistics request object for getting flows
	        OFStatisticsRequest req = new OFStatisticsRequest();//this is a request object
	        req.setStatisticType(OFStatisticsType.PORT);//this method sets the type of statistic request to FLOW
	        int requestLength = req.getLengthU();
	        OFPortStatisticsRequest specificReq = new OFPortStatisticsRequest();//this is a request FLOW object
	        specificReq.setPortNumber(portNumber);
	        req.setStatistics(Collections.singletonList((OFStatistics) specificReq));
	        requestLength += specificReq.getLength();
	        req.setLengthU(requestLength);

	        try {
	            // System.out.println(sw.getStatistics(req));
	            future = sw.queryStatistics(req);
	            values = future.get(10, TimeUnit.SECONDS);
	            if (values != null) {
	                for (OFStatistics stat : values) {
	                    statsReply.add((OFPortStatisticsReply) stat);
	                }
	            }
	        } catch (Exception e) {
	            System.out.println("Exception occurs");
	        }
	        
	        return statsReply;
		}
		

	}
	@Override
	public void topologyChanged() {
		
		ScheduledExecutorService ses = threadPool.getScheduledExecutor();
		switchPortLinks = topo.getSwitchPortLinks();
		portSet = switchPortLinks.keySet();
		System.out.println("The number of ports under consideration is: " + portSet.size());
		Iterator<NodePortTuple> it = portSet.iterator();
		while(it.hasNext())
		{
			System.out.println("I have entered the loop, which means the switchPort is not empty");
			NodePortTuple switchPort = it.next();
			portCondition.put(switchPort,new IsCongested(true));
			IOFSwitch sw = floodlightProvider.getSwitch(switchPort.getNodeId());
			System.out.println("Starting many threads to monitor ports");
				//Here, multiple threads should be initiated to monitor all ports in all links.
				ses.scheduleAtFixedRate(new MyThread(switchPort,sw),
			    0,
			    1,
			    TimeUnit.SECONDS);
		}
		System.out.println("The port_IsCongested map has " + portCondition.size() + " Number of entry");

		
	}

	public void topologyChanged(List<LDUpdate> linkUpdates){};

	

	
}
