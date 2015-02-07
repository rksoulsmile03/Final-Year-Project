package net.floodlightcontroller.getThroughPut;


import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.topology.NodePortTuple;

import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.statistics.OFPortStatisticsReply;
import org.openflow.protocol.statistics.OFPortStatisticsRequest;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;

public class MyThread implements Runnable {
	
	NodePortTuple switchPort;
	IOFSwitch sw;
	protected List<OFPortStatisticsReply> statsReply;
	
	public MyThread(NodePortTuple switchPort,IOFSwitch sw, List<OFPortStatisticsReply> statsReply)
	{
		this.switchPort = switchPort;
		this.sw = sw;
		this.statsReply = statsReply;
		
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
    			if(flow > 1000)
    			{
    				System.out.println("There is congestion issue");
    			}
    			else
    			{
    				System.out.println("There is no congestion issue");
    			}
    			System.out.println("Testing for git");
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
