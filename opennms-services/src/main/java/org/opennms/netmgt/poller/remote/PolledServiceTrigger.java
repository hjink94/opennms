package org.opennms.netmgt.poller.remote;

import org.springframework.scheduling.quartz.SimpleTriggerBean;

public class PolledServiceTrigger extends SimpleTriggerBean {
	
	private static final long serialVersionUID = -3224274965842979439L;

	private PolledService m_polledService;
	
	public PolledServiceTrigger(PolledService polledService) throws Exception {
		super();
		m_polledService = polledService;
		
		setName(polledService.getNodeId()+':'+polledService.getIpAddr()+':'+polledService.getSvcName());
		setRepeatInterval(m_polledService.getPollModel().getPollInterval());

		afterPropertiesSet();
	}

}
